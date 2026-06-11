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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.api.mail.MailApi
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailDao
import org.astermail.android.storage.search.DecryptedMailEntity

@Singleton
class SearchIndexManager @Inject constructor(
    private val db: AsterDatabase,
    private val mail_api: MailApi,
    private val repository: MailRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dao: DecryptedMailDao = db.decrypted_mail_dao()

    private val _index_ready = MutableStateFlow(false)
    val index_ready: StateFlow<Boolean> = _index_ready.asStateFlow()

    @Volatile
    private var is_building = false

    fun ensure_index_built() {
        if (is_building || _index_ready.value) return
        scope.launch { build_index_background() }
    }

    fun refresh_index() {
        scope.launch { build_index_background() }
    }

    fun on_items_loaded(items: List<InboxItem>) {
        scope.launch {
            cache_items(items)
            if (!_index_ready.value) {
                _index_ready.value = dao.count() > 0
            }
        }
    }

    suspend fun get_cached_items(): List<DecryptedMailEntity> = dao.get_all()

    suspend fun update_read(id: String, is_read: Boolean) = dao.update_read(id, is_read)

    suspend fun update_starred(id: String, is_starred: Boolean) = dao.update_starred(id, is_starred)

    suspend fun mark_trashed(ids: List<String>) = dao.mark_trashed(ids)

    suspend fun mark_archived(ids: List<String>) = dao.mark_archived(ids)

    suspend fun mark_spam(ids: List<String>) = dao.mark_spam(ids)

    suspend fun remove_items(ids: List<String>) = dao.remove_items(ids)

    suspend fun clear() {
        dao.clear_all()
        _index_ready.value = false
    }

    private suspend fun build_index_background() {
        val took_lock = mutex.withLock {
            if (is_building) false
            else { is_building = true; true }
        }
        if (!took_lock) return
        try {
            val existing_ids = dao.get_all_ids().toHashSet()
            var cursor: String? = null
            val max_pages = 20
            repeat(max_pages) { page ->
                val response = mail_api.list_messages(limit = 50, cursor = cursor)
                val new_items = response.items.filter { it.id !in existing_ids }
                if (new_items.isNotEmpty()) {
                    val decrypted = repository.decrypt_items_for_cache(new_items)
                    cache_items(decrypted)
                    new_items.forEach { existing_ids.add(it.id) }
                }
                if (!response.has_more || response.next_cursor == null) return@repeat
                cursor = response.next_cursor
            }
            _index_ready.value = true
        } catch (_: Throwable) {
        } finally {
            mutex.withLock { is_building = false }
        }
    }

    private suspend fun cache_items(items: List<InboxItem>) {
        if (items.isEmpty()) return
        val entities = items.map { item ->
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
            )
        }
        dao.insert_all(entities)
    }
}
