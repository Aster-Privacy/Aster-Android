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
import kotlinx.coroutines.delay
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
import org.astermail.android.storage.search.InboxWindowRow
import org.astermail.android.storage.search.MessageBodyIndexDao

private const val armored_prefix = "-----BEGIN PGP MESSAGE"
private const val snoozed_page_size = 200
private const val max_snoozed_pages = 50
private const val index_removal_chunk = 500

fun resumed_progress(known: Int, total: Int): Int =
    if (known <= 0 || total <= 0) 0 else minOf(known, total)

fun is_expired_mail(expires_at: String?, now_ms: Long): Boolean {
    val raw = expires_at?.trim().orEmpty()
    if (raw.isEmpty()) return false
    val millis = runCatching { java.time.Instant.parse(raw).toEpochMilli() }
        .recoverCatching { java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        .recoverCatching { java.time.LocalDateTime.parse(raw).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }
        .getOrNull() ?: return false
    return millis <= now_ms
}

fun split_expired_rows(
    rows: List<DecryptedMailEntity>,
    now_ms: Long,
): Pair<List<DecryptedMailEntity>, List<String>> {
    val (expired, live) = rows.partition { is_expired_mail(it.expires_at, now_ms) }
    return live to expired.map { it.id }
}

fun index_rows_missing_from_page(
    rows: List<InboxWindowRow>,
    returned_ids: Set<String>,
    returned_thread_tokens: Set<String>,
    protected_ids: Set<String> = emptySet(),
): List<String> = rows.asSequence()
    .filterNot { it.id in returned_ids || it.id in protected_ids }
    .filterNot { it.thread_token != null && it.thread_token in returned_thread_tokens }
    .map { it.id }
    .toList()

fun index_ids_missing_from_server(
    candidate_ids: Collection<String>,
    server_ids: Set<String>,
    walked_ids: Set<String>,
): List<String> {
    if (!server_ids.containsAll(walked_ids)) return emptyList()
    return candidate_ids.filterNot { it in server_ids }
}

data class IndexProgress(
    val indexed: Int,
    val total: Int,
    val started_at_ms: Long,
)

internal fun body_index_progress_of(indexed: Int, skipped: Int, total: Int, started_at_ms: Long): IndexProgress? {
    val handled = indexed.coerceAtLeast(0) + skipped.coerceAtLeast(0)
    if (total <= 0 || handled >= total) return null
    return IndexProgress(indexed = handled, total = total, started_at_ms = started_at_ms)
}

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
    private val body_dao: MessageBodyIndexDao by lazy { db_provider.get().message_body_index_dao() }

    private val _body_index_progress = MutableStateFlow<IndexProgress?>(null)
    val body_index_progress: StateFlow<IndexProgress?> = _body_index_progress.asStateFlow()

    @Volatile
    private var body_backfill_job: Job? = null

    private val body_failed_ids = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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
        scope.launch {
            index_ready.collect { ready -> if (ready) start_body_backfill() }
        }
    }

    fun start_body_backfill() {
        if (_index_paused.value) return
        if (body_backfill_job?.isActive == true) return
        val started_epoch = epoch.get()
        body_backfill_job = scope.launch {
            try {
                run_body_backfill(started_epoch)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("SearchIndexManager", "body index backfill stopped", error)
                }
            } finally {
                withContext(NonCancellable) { _body_index_progress.value = null }
            }
        }
    }

    private fun on_unmetered_network(): Boolean {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return runCatching {
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            body_backfill_allowed(
                on_wifi = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI),
                on_ethernet = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET),
            )
        }.getOrDefault(false)
    }

    private suspend fun publish_body_progress(started_at: Long) {
        val total = body_dao.cached_mail_count()
        val indexed = body_dao.indexed_cached_count()
        _body_index_progress.value = body_index_progress_of(indexed, body_failed_ids.size, total, started_at)
    }

    private suspend fun run_body_backfill(started_epoch: Int) {
        val started_at = System.currentTimeMillis()
        while (epoch.get() == started_epoch && !_index_paused.value) {
            if (!on_unmetered_network()) {
                _body_index_progress.value = null
                delay(BODY_BACKFILL_NETWORK_RECHECK_MS)
                continue
            }
            val rows = body_dao.unindexed_rows(body_failed_ids.take(BODY_BACKFILL_SKIP_LIMIT), BODY_BACKFILL_BATCH)
            if (rows.isEmpty()) return
            publish_body_progress(started_at)
            for ((thread_token, members) in rows.groupBy { it.thread_token.orEmpty() }) {
                if (epoch.get() != started_epoch || _index_paused.value || !on_unmetered_network()) break
                val ids = members.map { it.id }
                val result = kotlinx.coroutines.withTimeoutOrNull(BODY_BACKFILL_FETCH_TIMEOUT_MS) {
                    repository.index_thread_bodies(thread_token, ids)
                }
                if (result?.isSuccess != true) body_failed_ids.addAll(ids)
                publish_body_progress(started_at)
                delay(BODY_BACKFILL_PAUSE_MS)
            }
        }
    }

    suspend fun body_hits(terms: Collection<String>): Map<String, Set<String>> = withContext(Dispatchers.IO) {
        terms.filter { it.isNotBlank() }.distinct().associateWith { term ->
            val expression = body_match_expression(term) ?: return@associateWith emptySet()
            runCatching { body_dao.match_ids(expression, body_search_match_limit).toHashSet() }
                .getOrDefault(emptySet())
        }
    }

    @Volatile
    private var is_building = false

    private val epoch = java.util.concurrent.atomic.AtomicInteger(0)

    private val read_overlays = java.util.concurrent.CopyOnWriteArraySet<(String) -> Boolean?>()

    fun add_read_overlay(overlay: (String) -> Boolean?) {
        read_overlays.add(overlay)
    }

    fun remove_read_overlay(overlay: (String) -> Boolean?) {
        read_overlays.remove(overlay)
    }

    private fun pending_read(id: String): Boolean? = read_overlays.firstNotNullOfOrNull { it(id) }

    @Volatile
    private var build_job: Job? = null

    fun ensure_index_built() {
        if (is_building || _index_ready.value || _index_paused.value) return
        if (build_job?.isActive == true) return
        build_job = scope.launch {
            if (has_fresh_full_build() && dao.count() > 0) {
                if (!_index_ready.value) _index_ready.value = true
                return@launch
            }
            delay(STARTUP_BUILD_DELAY_MS)
            build_index_background()
        }
    }

    private fun has_fresh_full_build(): Boolean {
        val built_at = runCatching { pause_prefs.getLong(KEY_LAST_FULL_BUILD, 0L) }.getOrDefault(0L)
        val age = System.currentTimeMillis() - built_at
        return built_at > 0L && age in 0 until FULL_BUILD_FRESH_MS
    }

    private fun mark_full_build() {
        runCatching {
            pause_prefs.edit()
                .putLong(KEY_LAST_FULL_BUILD, System.currentTimeMillis())
                .putBoolean(KEY_INDEX_COMPLETE, true)
                .apply()
        }
    }

    private fun has_completed_build(): Boolean =
        runCatching { pause_prefs.getBoolean(KEY_INDEX_COMPLETE, false) }.getOrDefault(false)

    private fun load_poisoned_ids(): MutableSet<String> {
        val recorded_at = runCatching { pause_prefs.getLong(KEY_POISONED_AT, 0L) }.getOrDefault(0L)
        val age = System.currentTimeMillis() - recorded_at
        if (recorded_at <= 0L || age !in 0 until POISONED_TTL_MS) return HashSet()
        return runCatching { pause_prefs.getStringSet(KEY_POISONED_IDS, null)?.toHashSet() }
            .getOrNull() ?: HashSet()
    }

    private fun save_poisoned_ids(ids: Set<String>, reset_clock: Boolean) {
        val capped = if (ids.size > MAX_POISONED_IDS) ids.take(MAX_POISONED_IDS).toHashSet() else HashSet(ids)
        runCatching {
            val editor = pause_prefs.edit().putStringSet(KEY_POISONED_IDS, capped)
            if (reset_clock) editor.putLong(KEY_POISONED_AT, System.currentTimeMillis())
            editor.apply()
        }
    }

    private fun forget_build_state() {
        runCatching {
            pause_prefs.edit()
                .remove(KEY_LAST_FULL_BUILD)
                .remove(KEY_INDEX_COMPLETE)
                .remove(KEY_POISONED_IDS)
                .remove(KEY_POISONED_AT)
                .apply()
        }
    }

    fun refresh_index() {
        if (_index_paused.value) return
        if (is_building || build_job?.isActive == true) return
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
        body_backfill_job?.cancel()
    }

    fun resume_indexing() {
        _index_paused.value = false
        pause_prefs.edit().putBoolean(KEY_INDEX_PAUSED, false).apply()
        if (_index_ready.value) start_body_backfill()
        if (is_building || build_job?.isActive == true) return
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
            index_arrivals(items, my_epoch)
        }
    }

    private val arrival_mutex = Mutex()

    private suspend fun index_arrivals(items: List<InboxItem>, my_epoch: Int) {
        if (_index_paused.value) return
        if (org.astermail.android.api.network.low_network_state.extend_timeouts()) return
        val candidates = items.asSequence()
            .filterNot { it.is_undecryptable }
            .filter { !it.thread_token.isNullOrBlank() }
            .take(BODY_ARRIVAL_WINDOW)
            .toList()
        if (candidates.isEmpty() || !arrival_mutex.tryLock()) return
        try {
            val indexed = runCatching { body_dao.indexed_among(candidates.map { it.id }).toHashSet() }
                .getOrDefault(emptySet())
            val missing = candidates.filterNot { it.id in indexed || it.id in body_failed_ids }
            for ((thread_token, members) in missing.groupBy { it.thread_token!! }.entries.take(BODY_ARRIVAL_THREADS)) {
                if (epoch.get() != my_epoch) return
                val ids = members.map { it.id }
                val result = kotlinx.coroutines.withTimeoutOrNull(BODY_BACKFILL_FETCH_TIMEOUT_MS) {
                    repository.index_thread_bodies(thread_token, ids)
                }
                if (result?.isSuccess != true) body_failed_ids.addAll(ids)
            }
        } finally {
            arrival_mutex.unlock()
        }
    }

    suspend fun get_warm_items(limit: Int): List<DecryptedMailEntity> {
        val protected_tokens = org.astermail.android.folders.folder_lock_store.protected_tokens()
        val (rows, expired_ids) = split_expired_rows(dao.get_warm_window(limit), System.currentTimeMillis())
        scope.launch {
            if (expired_ids.isNotEmpty()) runCatching { remove_items(expired_ids) }
            purge_bundle_poisoned()
            if (protected_tokens.isNotEmpty()) purge_folder_tokens(protected_tokens)
        }
        val visible = if (protected_tokens.isEmpty()) {
            rows
        } else {
            rows.filterNot { row ->
                row.labels.split(',').any { it.isNotBlank() && it in protected_tokens }
            }
        }
        return visible.map { row ->
            if (row.preview.startsWith(armored_prefix) || row.subject.startsWith(armored_prefix)) {
                row.copy(
                    preview = if (row.preview.startsWith(armored_prefix)) "" else row.preview,
                    subject = if (row.subject.startsWith(armored_prefix)) "" else row.subject,
                )
            } else {
                row
            }
        }
    }

    suspend fun get_cached_items(): List<DecryptedMailEntity> {
        purge_bundle_poisoned()
        val protected_tokens = org.astermail.android.folders.folder_lock_store.protected_tokens()
        if (protected_tokens.isNotEmpty()) purge_folder_tokens(protected_tokens)
        val (rows, expired_ids) = split_expired_rows(dao.get_all(), System.currentTimeMillis())
        if (expired_ids.isNotEmpty()) runCatching { remove_items(expired_ids) }
        if (protected_tokens.isEmpty()) return rows
        return rows.filterNot { row ->
            row.labels.split(',').any { it.isNotBlank() && it in protected_tokens }
        }
    }

    private val poison_purge_pending = java.util.concurrent.atomic.AtomicBoolean(true)

    private suspend fun purge_bundle_poisoned() {
        if (!poison_purge_pending.getAndSet(false)) return
        val purged = runCatching { dao.clear_armored_previews() }.isSuccess and
            runCatching { dao.delete_bundle_poisoned() }.isSuccess and
            runCatching { dao.delete_blank_rows() }.isSuccess
        if (!purged) poison_purge_pending.set(true)
    }

    suspend fun reconcile_inbox_window(
        returned_ids: Set<String>,
        returned_thread_tokens: Set<String>,
        min_timestamp: String,
        whole_scope: Boolean = false,
        protected_ids: Set<String> = emptySet(),
    ) {
        if (!whole_scope && (returned_ids.isEmpty() || min_timestamp.isBlank())) return
        clear_window_absences(returned_ids)
        val rows = if (whole_scope) {
            dao.unlabeled_inbox_rows()
        } else {
            dao.inbox_window_rows_newer_than(min_timestamp)
        }
        val stale = index_rows_missing_from_page(rows, returned_ids, returned_thread_tokens, protected_ids)
        if (stale.isEmpty()) return
        val snoozed_ids = runCatching { collect_snoozed_ids() }.getOrNull() ?: return
        val candidates = stale.filterNot { it in snoozed_ids }
        val removable = record_window_absences(candidates)
        remove_index_rows(removable)
    }

    private suspend fun remove_index_rows(ids: List<String>) {
        ids.chunked(index_removal_chunk).forEach { dao.remove_items(it) }
    }

    private suspend fun collect_server_ids(started_at: Long, budget_ms: Long): Set<String>? {
        val ids = HashSet<String>()
        var cursor: String? = null
        var pages = 0
        while (pages < MAX_SERVER_ID_PAGES) {
            if (System.currentTimeMillis() - started_at > budget_ms) return null
            val response = mail_api.list_messages(
                limit = SERVER_ID_PAGE_SIZE,
                cursor = cursor,
                item_type = "all",
                include_spam = true,
                include_trash = true,
                group_by_thread = false,
                skip_total = true,
                include_envelope = false,
            )
            response.items.forEach { ids.add(it.id) }
            pages++
            if (!response.has_more) return ids
            cursor = response.next_cursor ?: return null
        }
        return null
    }

    private suspend fun purge_ids_missing_from_server(
        start_ids: Set<String>,
        walked_ids: Set<String>,
        my_epoch: Int,
        started_at: Long,
        budget_ms: Long,
    ) {
        if (start_ids.isEmpty()) return
        val server_ids = collect_server_ids(started_at, budget_ms) ?: return
        if (epoch.get() != my_epoch) return
        val candidates = dao.unlabeled_ids().filter { it in start_ids }
        val missing = index_ids_missing_from_server(candidates, server_ids, walked_ids)
        if (missing.isEmpty() || epoch.get() != my_epoch) return
        remove_index_rows(missing)
    }

    private suspend fun collect_snoozed_ids(): HashSet<String> {
        val ids = HashSet<String>()
        var cursor: String? = null
        var pages = 0
        while (pages < max_snoozed_pages) {
            val response = mail_api.list_messages(
                limit = snoozed_page_size,
                cursor = cursor,
                item_type = "received",
                is_snoozed = true,
            )
            response.items.forEach { ids.add(it.id) }
            cursor = response.next_cursor
            pages++
            if (!response.has_more || cursor == null) return ids
        }
        throw IllegalStateException("snoozed listing exceeded the page budget")
    }

    private val window_absences: MutableMap<String, Int> by lazy { load_window_absences() }

    private fun load_window_absences(): MutableMap<String, Int> {
        val stored = runCatching { pause_prefs.getStringSet(KEY_WINDOW_ABSENCES, emptySet()) }
            .getOrNull()
            .orEmpty()
        val loaded = mutableMapOf<String, Int>()
        for (entry in stored) {
            val split = entry.lastIndexOf('|')
            if (split <= 0) continue
            val count = entry.substring(split + 1).toIntOrNull() ?: continue
            loaded[entry.substring(0, split)] = count
        }
        return loaded
    }

    private fun persist_window_absences() {
        val encoded = window_absences.entries
            .take(MAX_TRACKED_ABSENCES)
            .mapTo(HashSet()) { it.key + "|" + it.value }
        runCatching { pause_prefs.edit().putStringSet(KEY_WINDOW_ABSENCES, encoded).apply() }
    }

    fun last_inbox_sync_at(): Long =
        runCatching { pause_prefs.getLong(KEY_LAST_INBOX_SYNC, 0L) }.getOrDefault(0L)

    fun mark_inbox_synced() {
        runCatching { pause_prefs.edit().putLong(KEY_LAST_INBOX_SYNC, System.currentTimeMillis()).apply() }
    }

    fun expire_inbox_sync() {
        runCatching { pause_prefs.edit().remove(KEY_LAST_INBOX_SYNC).apply() }
    }

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
        if (ids.isNotEmpty()) persist_window_absences()
        return confirmed
    }

    private fun clear_window_absences(ids: Set<String>) {
        var changed = false
        for (id in ids) {
            if (window_absences.remove(id) != null) changed = true
        }
        if (changed) persist_window_absences()
    }

    suspend fun update_read(id: String, is_read: Boolean) = dao.update_read(id, is_read)

    suspend fun update_starred(id: String, is_starred: Boolean) = dao.update_starred(id, is_starred)

    suspend fun mark_trashed(ids: List<String>) = dao.mark_trashed(ids)

    suspend fun mark_archived(ids: List<String>) = dao.mark_archived(ids)

    suspend fun mark_unarchived(ids: List<String>) = dao.mark_unarchived(ids)

    suspend fun mark_spam(ids: List<String>) = dao.mark_spam(ids)

    suspend fun mark_unspam(ids: List<String>) = dao.mark_unspam(ids)

    suspend fun mark_restored(ids: List<String>) = dao.mark_restored(ids)

    suspend fun remove_items(ids: List<String>) {
        dao.remove_items(ids)
        ids.chunked(index_removal_chunk).forEach { runCatching { body_dao.delete_ids(it) } }
    }

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
        body_backfill_job?.cancel()
        mutex.withLock {
            epoch.incrementAndGet()
            dao.clear_all()
            runCatching { body_dao.clear_all() }
            body_failed_ids.clear()
            _body_index_progress.value = null
            forget_build_state()
            _index_ready.value = false
            _index_progress.value = null
        }
    }

    private data class IndexScope(
        val is_trashed: Boolean?,
        val is_archived: Boolean?,
        val is_spam: Boolean?,
        val item_type: String = "received",
        val archived_from_item: Boolean = false,
    )

    private val attachment_probe_batch = 50

    private val index_scopes = listOf(
        IndexScope(
            is_trashed = false,
            is_archived = false,
            is_spam = false,
            item_type = "all",
            archived_from_item = true,
        ),
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
            val start_ids = HashSet(existing_ids)
            val walked_ids = HashSet<String>()
            val incremental = has_fresh_full_build() && existing_ids.isNotEmpty()
            val quiet = incremental || (has_completed_build() && existing_ids.isNotEmpty())
            val poisoned_ids = load_poisoned_ids()
            val poisoned_before = poisoned_ids.size
            val poisoned_clock_reset = poisoned_ids.isEmpty()
            val added_ids = HashSet<String>()
            val page_size = 200
            val max_pages = 500
            val build_budget_ms = 10 * 60 * 1000L
            var total_target = 0
            var processed = 0
            var shown = 0
            val started_at = System.currentTimeMillis()
            var walk_complete = true
            var caught_up = true
            if (!quiet) {
                total_target = count_index_target(page_size * max_pages)
                shown = resumed_progress(start_ids.size, total_target)
                if (shown > 0 && epoch.get() == my_epoch) {
                    _index_progress.value = IndexProgress(
                        indexed = shown,
                        total = total_target,
                        started_at_ms = started_at,
                    )
                }
            }
            for (scope in index_scopes) {
                var cursor: String? = null
                var page = 0
                var scope_complete = false
                while (page < max_pages) {
                    if (System.currentTimeMillis() - started_at > build_budget_ms) break
                    val response = mail_api.list_messages(
                        limit = page_size,
                        cursor = cursor,
                        item_type = scope.item_type,
                        is_trashed = scope.is_trashed,
                        is_archived = scope.is_archived,
                        is_spam = scope.is_spam,
                        skip_total = if (page > 0) true else null,
                    )
                    val scoped_items = response.items.filter { is_indexed_item_type(it.item_type) }
                    if (quiet && page == 0 && response.total >= 0) {
                        total_target += minOf(response.total, max_pages * page_size)
                    }
                    response.items.mapTo(walked_ids) { it.id }
                    val new_items = scoped_items.filter { it.id !in existing_ids && it.id !in poisoned_ids }
                    val known_ids = scoped_items.map { it.id }.filter { it in existing_ids }
                    if (known_ids.isNotEmpty()) {
                        val known_set = known_ids.toHashSet()
                        val known_items = scoped_items.filter { it.id in known_set }
                        refresh_known_flags(known_items)
                        when (scope.is_trashed) {
                            true -> dao.mark_trashed(known_ids)
                            false -> dao.mark_untrashed(known_ids)
                            else -> {}
                        }
                        if (scope.archived_from_item) {
                            val archived = known_items.filter { it.is_archived == true }.map { it.id }
                            val unarchived = known_items.filter { it.is_archived != true }.map { it.id }
                            if (archived.isNotEmpty()) dao.mark_archived(archived)
                            if (unarchived.isNotEmpty()) dao.mark_unarchived(unarchived)
                        } else {
                            when (scope.is_archived) {
                                true -> dao.mark_archived(known_ids)
                                false -> dao.mark_unarchived(known_ids)
                                else -> {}
                            }
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
                                is_archived = if (scope.archived_from_item) it.is_archived else scope.is_archived ?: it.is_archived,
                                is_spam = scope.is_spam ?: it.is_spam,
                            )
                        }
                        val persisted = cache_items(decrypted, my_epoch)
                        existing_ids.addAll(persisted)
                        added_ids.addAll(persisted)
                        if (epoch.get() == my_epoch) {
                            decrypted.filter { is_index_poisoned(it) }.mapTo(poisoned_ids) { it.id }
                        }
                    }
                    processed += response.items.size
                    if (!quiet && epoch.get() == my_epoch) {
                        shown = maxOf(shown, processed)
                        _index_progress.value = IndexProgress(
                            indexed = shown,
                            total = maxOf(total_target, shown),
                            started_at_ms = started_at,
                        )
                    }
                    if (!response.has_more) {
                        scope_complete = true
                        break
                    }
                    if (incremental && new_items.isEmpty()) break
                    if (response.next_cursor == null) break
                    cursor = response.next_cursor
                    page++
                }
                if (!scope_complete) walk_complete = false
                if (!scope_complete && !incremental) caught_up = false
            }
            if (epoch.get() == my_epoch && poisoned_ids.size != poisoned_before) {
                save_poisoned_ids(poisoned_ids, poisoned_clock_reset)
            }
            enrich_attachment_flags(my_epoch, if (incremental) added_ids else null)
            if (epoch.get() == my_epoch && walk_complete) {
                runCatching {
                    purge_ids_missing_from_server(start_ids, walked_ids, my_epoch, started_at, build_budget_ms)
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
            if (epoch.get() == my_epoch && walk_complete) {
                _index_ready.value = true
                mark_full_build()
            } else if (epoch.get() == my_epoch && incremental && caught_up) {
                _index_ready.value = true
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("SearchIndexManager", "index build aborted", error)
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { is_building = false }
                _index_progress.value = null
            }
        }
    }

    private fun is_indexed_item_type(item_type: String?): Boolean =
        item_type == null || item_type == "received"

    private suspend fun count_index_target(cap: Int): Int {
        var total = 0
        for (scope in index_scopes) {
            val response = mail_api.list_messages(
                limit = 1,
                cursor = null,
                item_type = scope.item_type,
                is_trashed = scope.is_trashed,
                is_archived = scope.is_archived,
                is_spam = scope.is_spam,
                skip_total = null,
            )
            if (response.total > 0) total += minOf(response.total, cap)
        }
        return total
    }

    private companion object {
        const val BODY_BACKFILL_BATCH = 40
        const val BODY_ARRIVAL_WINDOW = 25
        const val BODY_ARRIVAL_THREADS = 10
        const val BODY_BACKFILL_SKIP_LIMIT = 900
        const val BODY_BACKFILL_PAUSE_MS = 400L
        const val BODY_BACKFILL_FETCH_TIMEOUT_MS = 20_000L
        const val BODY_BACKFILL_NETWORK_RECHECK_MS = 30_000L
        const val KEY_INDEX_PAUSED = "index_paused"
        const val KEY_WINDOW_ABSENCES = "window_absences"
        const val KEY_LAST_INBOX_SYNC = "last_inbox_sync"
        const val KEY_LAST_FULL_BUILD = "last_full_build"
        const val KEY_INDEX_COMPLETE = "index_complete"
        const val KEY_POISONED_IDS = "poisoned_ids"
        const val KEY_POISONED_AT = "poisoned_at"
        const val FULL_BUILD_FRESH_MS = 6 * 60 * 60 * 1000L
        const val POISONED_TTL_MS = 24 * 60 * 60 * 1000L
        const val MAX_POISONED_IDS = 2000
        const val STARTUP_BUILD_DELAY_MS = 4000L
        const val MAX_TRACKED_ABSENCES = 500
        const val WINDOW_ABSENCES_BEFORE_REMOVAL = 2
        const val SERVER_ID_PAGE_SIZE = 200
        const val MAX_SERVER_ID_PAGES = 500
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
        val read = items.filter { (pending_read(it.id) ?: it.is_read) == true }.map { it.id }
        val unread = items.filter { (pending_read(it.id) ?: it.is_read) == false }.map { it.id }
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

    private suspend fun enrich_attachment_flags(my_epoch: Int, only_ids: Set<String>?) {
        if (epoch.get() != my_epoch) return
        val candidates = dao.ids_without_attachments()
        val targets = if (only_ids == null) candidates else candidates.filter { it in only_ids }
        resolve_attachment_ids(targets)
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
        val now_ms = System.currentTimeMillis()
        val indexable = items
            .filterNot { is_index_poisoned(it) }
            .filterNot { is_expired_mail(it.raw_item.expires_at, now_ms) }
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
                is_read = pending_read(item.id) ?: item.is_read,
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
                system_origin = item.raw_item.system_origin,
                has_recipient_key = item.raw_item.has_recipient_key,
                is_pinned = item.raw_item.metadata?.is_pinned ?: false,
                tag_tokens = item.tag_tokens.joinToString(",").ifBlank { null },
                spf_result = item.raw_item.spf_result,
                dkim_result = item.raw_item.dkim_result,
                dmarc_result = item.raw_item.dmarc_result,
                sender_aligned = item.raw_item.sender_aligned,
                sender_verified = item.raw_item.sender_verified,
                sender_verified_domain = item.raw_item.sender_verified_domain,
                sender_verification = item.sender_verification,
                item_type = item.raw_item.item_type,
                expires_at = item.raw_item.expires_at,
            )
        }
        mutex.withLock {
            if (epoch.get() != my_epoch) return emptySet()
            dao.insert_all(entities)
            poison_purge_pending.set(true)
        }
        return indexable.map { it.id }.toSet()
    }
}

internal fun is_index_poisoned(item: InboxItem): Boolean {
    if (item.is_undecryptable) return true
    return item.sender_email.isBlank() && item.subject.isBlank() && item.preview.isBlank()
}
