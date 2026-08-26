//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailDao
import org.astermail.android.storage.search.DecryptedMailEntity

data class IndexProgress(
    val indexed: Int,
    val total: Int,
    val started_at_ms: Long,
)

@Singleton
class SearchIndexManager @Inject constructor(
    private val db_provider: dagger.Lazy<AsterDatabase>,
    private val mail_api: MailApi,
    private val repository: MailRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dao: DecryptedMailDao by lazy { db_provider.get().decrypted_mail_dao() }

    private val _index_ready = MutableStateFlow(false)
    val index_ready: StateFlow<Boolean> = _index_ready.asStateFlow()

    private val pause_prefs by lazy {
        context.getSharedPreferences("search_index", android.content.Context.MODE_PRIVATE)
    }

    private val _index_paused = MutableStateFlow(false)
    val index_paused: StateFlow<Boolean> = _index_paused.asStateFlow()

    private val _index_progress = MutableStateFlow<IndexProgress?>(null)
    val index_progress: StateFlow<IndexProgress?> = _index_progress.asStateFlow()

    init {
        scope.launch {
            _index_paused.value = pause_prefs.getBoolean(KEY_INDEX_PAUSED, false)
        }
    }

    @Volatile
    private var is_building = false

    private val epoch = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var build_job: Job? = null

    fun ensure_index_built() {
        if (is_building || _index_ready.value || _index_paused.value) return
        build_job = scope.launch { build_index_background() }
    }

    fun refresh_index() {
        if (_index_paused.value) return
        build_job = scope.launch { build_index_background() }
    }

    suspend fun refresh_index_and_wait() {
        if (_index_paused.value) return
        build_index_background()
    }

    fun pause_indexing() {
        _index_paused.value = true
        pause_prefs.edit().putBoolean(KEY_INDEX_PAUSED, true).apply()
        build_job?.cancel()
    }

    fun resume_indexing() {
        _index_paused.value = false
        pause_prefs.edit().putBoolean(KEY_INDEX_PAUSED, false).apply()
        build_job = scope.launch { build_index_background() }
    }

    fun on_items_loaded(items: List<InboxItem>) {
        val my_epoch = epoch.get()
        val cacheable = items.filter {
            val t = it.raw_item.item_type
            t == null || t == "received"
        }
        scope.launch {
            cache_items(cacheable, my_epoch)
            if (epoch.get() == my_epoch && !_index_ready.value) {
                _index_ready.value = dao.count() > 0
            }
        }
    }

    suspend fun get_cached_items(): List<DecryptedMailEntity> {
        purge_bundle_poisoned()
        val protected_tokens = org.astermail.android.folders.folder_lock_store.protected_tokens()
        if (protected_tokens.isNotEmpty()) purge_folder_tokens(protected_tokens)
        val rows = dao.get_all()
        if (protected_tokens.isEmpty()) return rows
        return rows.filterNot { row ->
            row.labels.split(',').any { it.isNotBlank() && it in protected_tokens }
        }
    }

    private suspend fun purge_bundle_poisoned() {
        runCatching { dao.clear_armored_previews() }
        runCatching { dao.delete_bundle_poisoned() }
        runCatching { dao.delete_blank_rows() }
    }

    suspend fun reconcile_inbox_window(
        returned_ids: Set<String>,
        returned_thread_tokens: Set<String>,
        min_timestamp: String,
    ) {
        if (returned_ids.isEmpty() || min_timestamp.isBlank()) return
        clear_window_absences(returned_ids)
        val stale = dao.inbox_window_rows_newer_than(min_timestamp)
            .filterNot { it.id in returned_ids }
            .filterNot { it.thread_token != null && it.thread_token in returned_thread_tokens }
            .map { it.id }
        if (stale.isEmpty()) return
        val snoozed = runCatching {
            mail_api.list_messages(limit = 200, item_type = "received", is_snoozed = true)
        }.getOrNull() ?: return
        if (snoozed.has_more) return
        val snoozed_ids = snoozed.items.map { it.id }.toHashSet()
        val candidates = stale.filterNot { it in snoozed_ids }
        val removable = record_window_absences(candidates)
        if (removable.isNotEmpty()) dao.remove_items(removable)
    }

    private val window_absences = mutableMapOf<String, Int>()

    private fun record_window_absences(ids: List<String>): List<String> {
        val confirmed = mutableListOf<String>()
        for (id in ids) {
            val count = (window_absences[id] ?: 0) + 1
            if (count >= WINDOW_ABSENCES_BEFORE_REMOVAL) {
                window_absences.remove(id)
                confirmed.add(id)
            } else {
                window_absences[id] = count
            }
        }
        return confirmed
    }

    private fun clear_window_absences(ids: Set<String>) {
        for (id in ids) window_absences.remove(id)
    }

    suspend fun update_read(id: String, is_read: Boolean) = dao.update_read(id, is_read)

    suspend fun update_starred(id: String, is_starred: Boolean) = dao.update_starred(id, is_starred)

    suspend fun mark_trashed(ids: List<String>) = dao.mark_trashed(ids)

    suspend fun mark_archived(ids: List<String>) = dao.mark_archived(ids)

    suspend fun mark_unarchived(ids: List<String>) = dao.mark_unarchived(ids)

    suspend fun mark_spam(ids: List<String>) = dao.mark_spam(ids)

    suspend fun mark_unspam(ids: List<String>) = dao.mark_unspam(ids)

    suspend fun mark_restored(ids: List<String>) = dao.mark_restored(ids)

    suspend fun remove_items(ids: List<String>) = dao.remove_items(ids)

    suspend fun add_tag_token(ids: List<String>, token: String) {
        if (ids.isEmpty() || token.isBlank()) return
        dao.add_tag_token(ids, token)
    }

    suspend fun remove_tag_token(ids: List<String>, token: String) {
        if (ids.isEmpty() || token.isBlank()) return
        dao.remove_tag_token(ids, token)
    }

    suspend fun add_label_token(ids: List<String>, token: String) {
        if (ids.isEmpty() || token.isBlank()) return
        dao.add_label_token(ids, token)
    }

    suspend fun remove_label_token(ids: List<String>, token: String) {
        if (ids.isEmpty() || token.isBlank()) return
        dao.remove_label_token(ids, token)
    }

    suspend fun clear() {
        build_job?.cancel()
        mutex.withLock {
            epoch.incrementAndGet()
            dao.clear_all()
            _index_ready.value = false
            _index_progress.value = null
        }
    }

    private data class IndexScope(
        val is_trashed: Boolean?,
        val is_archived: Boolean?,
        val is_spam: Boolean?,
    )

    private val attachment_probe_batch = 50

    private val index_scopes = listOf(
        IndexScope(is_trashed = false, is_archived = false, is_spam = false),
        IndexScope(is_trashed = true, is_archived = null, is_spam = null),
        IndexScope(is_trashed = null, is_archived = true, is_spam = null),
        IndexScope(is_trashed = null, is_archived = null, is_spam = true),
    )

    private suspend fun build_index_background() {
        val took_lock = mutex.withLock {
            if (is_building) false
            else { is_building = true; true }
        }
        if (!took_lock) return
        val my_epoch = epoch.get()
        try {
            purge_bundle_poisoned()
            val existing_ids = dao.get_all_ids().toHashSet()
            val page_size = 200
            val max_pages = 500
            val build_budget_ms = 10 * 60 * 1000L
            var total_target = 0
            var processed = 0
            val started_at = System.currentTimeMillis()
            var walk_complete = true
            for (scope in index_scopes) {
                var cursor: String? = null
                var page = 0
                var scope_complete = false
                while (page < max_pages) {
                    if (System.currentTimeMillis() - started_at > build_budget_ms) break
                    val response = mail_api.list_messages(
                        limit = page_size,
                        cursor = cursor,
                        item_type = "received",
                        is_trashed = scope.is_trashed,
                        is_archived = scope.is_archived,
                        is_spam = scope.is_spam,
                        skip_total = if (page > 0) true else null,
                    )
                    if (page == 0 && response.total >= 0) {
                        total_target += minOf(response.total, max_pages * page_size)
                    }
                    val new_items = response.items.filter { it.id !in existing_ids }
                    val known_ids = response.items.map { it.id }.filter { it in existing_ids }
                    if (known_ids.isNotEmpty()) {
                        val known_set = known_ids.toHashSet()
                        val known_items = response.items.filter { it.id in known_set }
                        refresh_known_flags(known_items)
                        when (scope.is_trashed) {
                            true -> dao.mark_trashed(known_ids)
                            false -> dao.mark_untrashed(known_ids)
                            else -> {}
                        }
                        when (scope.is_archived) {
                            true -> dao.mark_archived(known_ids)
                            false -> dao.mark_unarchived(known_ids)
                            else -> {}
                        }
                        when (scope.is_spam) {
                            true -> dao.mark_spam(known_ids)
                            false -> dao.mark_unspam(known_ids)
                            else -> {}
                        }
                    }
                    if (new_items.isNotEmpty()) {
                        val decrypted = repository.decrypt_items_for_cache(new_items).map {
                            it.copy(
                                is_trashed = scope.is_trashed ?: it.is_trashed,
                                is_archived = scope.is_archived ?: it.is_archived,
                                is_spam = scope.is_spam ?: it.is_spam,
                            )
                        }
                        val persisted = cache_items(decrypted, my_epoch)
                        existing_ids.addAll(persisted)
                    }
                    processed += response.items.size
                    if (epoch.get() == my_epoch) {
                        _index_progress.value = IndexProgress(
                            indexed = processed,
                            total = maxOf(total_target, processed),
                            started_at_ms = started_at,
                        )
                    }
                    if (!response.has_more) {
                        scope_complete = true
                        break
                    }
                    if (response.next_cursor == null) break
                    cursor = response.next_cursor
                    page++
                }
                if (!scope_complete) walk_complete = false
            }
            enrich_attachment_flags(my_epoch)
            if (epoch.get() == my_epoch && walk_complete) _index_ready.value = true
        } catch (error: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("SearchIndexManager", "index build aborted", error)
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { is_building = false }
                if (!_index_paused.value) _index_progress.value = null
            }
        }
    }

    private companion object {
        const val KEY_INDEX_PAUSED = "index_paused"
        const val WINDOW_ABSENCES_BEFORE_REMOVAL = 2
    }

    data class AttachmentProbeResult(
        val found: Set<String>,
        val failed: List<String>,
    )

    suspend fun known_attachment_ids(): Set<String>? {
        return try {
            dao.ids_with_attachments().toSet()
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun probe_attachment_ids(ids: List<String>): AttachmentProbeResult {
        if (ids.isEmpty()) return AttachmentProbeResult(emptySet(), emptyList())
        val found = HashSet<String>()
        val failed = ArrayList<String>()
        for (batch in ids.chunked(attachment_probe_batch)) {
            repository.probe_messages_with_attachments(batch).fold(
                onSuccess = { found.addAll(it) },
                onFailure = { failed.addAll(batch) },
            )
        }
        if (found.isNotEmpty()) {
            runCatching { dao.mark_has_attachments(found.toList()) }
        }
        return AttachmentProbeResult(found, failed)
    }

    suspend fun resolve_attachment_ids(ids: List<String>): Set<String> {
        return probe_attachment_ids(ids).found
    }

    private suspend fun refresh_known_flags(items: List<MailItem>) {
        val read = items.filter { it.is_read == true }.map { it.id }
        val unread = items.filter { it.is_read == false }.map { it.id }
        val starred = items.filter { it.is_starred == true }.map { it.id }
        val unstarred = items.filter { it.is_starred == false }.map { it.id }
        val pinned = items.filter { it.is_pinned == true }.map { it.id }
        val unpinned = items.filter { it.is_pinned == false }.map { it.id }
        if (read.isNotEmpty()) dao.set_read(read, true)
        if (unread.isNotEmpty()) dao.set_read(unread, false)
        if (starred.isNotEmpty()) dao.set_starred(starred, true)
        if (unstarred.isNotEmpty()) dao.set_starred(unstarred, false)
        if (pinned.isNotEmpty()) dao.set_pinned(pinned, true)
        if (unpinned.isNotEmpty()) dao.set_pinned(unpinned, false)
    }

    private suspend fun enrich_attachment_flags(my_epoch: Int) {
        if (epoch.get() != my_epoch) return
        resolve_attachment_ids(dao.ids_without_attachments())
    }

    suspend fun purge_folder_tokens(folder_tokens: Set<String>) {
        for (token in folder_tokens) {
            if (token.isBlank()) continue
            runCatching { dao.delete_by_folder_token(token) }
        }
    }

    private suspend fun cache_items(items: List<InboxItem>, my_epoch: Int): Set<String> {
        if (items.isEmpty()) return emptySet()
        val protected_tokens = org.astermail.android.folders.folder_lock_store.protected_tokens()
        val indexable = items
            .filterNot { is_index_poisoned(it) }
            .filterNot { item ->
                protected_tokens.isNotEmpty() &&
                    org.astermail.android.folders.inbox_item_folder_tokens(item).any { it in protected_tokens }
            }
        if (indexable.isEmpty()) return emptySet()
        val entities = indexable.map { item ->
            DecryptedMailEntity(
                id = item.id,
                thread_token = item.thread_token,
                thread_message_count = item.thread_message_count,
                sender_name = item.sender_name,
                sender_email = item.sender_email,
                subject = item.subject,
                preview = item.preview,
                timestamp = item.timestamp,
                is_read = item.is_read,
                is_starred = item.is_starred,
                is_encrypted = item.is_encrypted,
                has_attachments = item.has_attachments,
                is_trashed = item.is_trashed,
                is_archived = item.is_archived,
                is_spam = item.is_spam,
                labels = item.labels.joinToString(","),
                indexed_at = System.currentTimeMillis(),
                category = item.category,
                received_on = item.received_on,
                display_sender_name = item.display_sender_name,
                display_sender_email = item.display_sender_email,
                to_addresses = item.to_addresses.joinToString(",").ifBlank { null },
                routing_token = item.routing_token,
                is_external = item.raw_item.is_external,
                has_recipient_key = item.raw_item.has_recipient_key,
                is_pinned = item.raw_item.metadata?.is_pinned ?: false,
                tag_tokens = item.tag_tokens.joinToString(",").ifBlank { null },
            )
        }
        mutex.withLock {
            if (epoch.get() != my_epoch) return emptySet()
            dao.insert_all(entities)
        }
        return indexable.map { it.id }.toSet()
    }
}

internal fun is_index_poisoned(item: InboxItem): Boolean {
    if (item.is_undecryptable) return true
    return item.sender_email.isBlank() && item.subject.isBlank() && item.preview.isBlank()
}
