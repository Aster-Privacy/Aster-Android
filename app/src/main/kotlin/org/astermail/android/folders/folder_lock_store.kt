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

package org.astermail.android.folders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.astermail.android.api.folder_unlock_request
import org.astermail.android.api.labels.LabelItem

private const val folder_unlock_timeout_ms = 30 * 60 * 1000L
private const val folder_route_index_capacity = 4000

const val folder_lock_mode_session = "session"
const val folder_lock_mode_on_leave = "on_leave"

data class folder_unlock_session(
    val unlock_token: String?,
    val unlocked_at_ms: Long,
    val last_used_ms: Long,
    val expires_at_ms: Long?,
    val encrypted_folder_key: String?,
    val folder_key_nonce: String?,
)

private class bounded_route_index<K, V>(private val capacity: Int) :
    LinkedHashMap<K, V>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
}

object folder_lock_store {
    private val unlocked = mutableMapOf<String, folder_unlock_session>()
    private val folder_token_by_id = mutableMapOf<String, String>()
    private val folder_id_by_token = mutableMapOf<String, String>()
    private val protected_folder_ids = mutableSetOf<String>()
    private val seeded_protected_tokens = mutableSetOf<String>()
    private var folders_known = false
    private val item_folder_index = bounded_route_index<String, Set<String>>(folder_route_index_capacity)
    private val thread_folder_index = bounded_route_index<String, Set<String>>(folder_route_index_capacity)

    private var active_folder_token: String = ""
    private var lock_mode: String = folder_lock_mode_session

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var remote_revoker: ((String, String?) -> Unit)? = null

    @Volatile
    private var purge_hook: ((Set<String>) -> Unit)? = null

    fun register_remote_revoker(block: (String, String?) -> Unit) {
        remote_revoker = block
    }

    fun register_purge_hook(block: (Set<String>) -> Unit) {
        purge_hook = block
    }

    @Synchronized
    fun set_lock_mode(mode: String) {
        val normalized = if (mode == folder_lock_mode_on_leave) folder_lock_mode_on_leave else folder_lock_mode_session
        lock_mode = normalized
    }

    @Synchronized
    fun current_lock_mode(): String = lock_mode

    @Synchronized
    private fun session_for(folder_id: String): folder_unlock_session? {
        val session = unlocked[folder_id] ?: return null
        val now = System.currentTimeMillis()
        val idle_expired = now - session.last_used_ms > folder_unlock_timeout_ms
        val absolute_expired = session.expires_at_ms != null && now >= session.expires_at_ms
        if (idle_expired || absolute_expired) {
            unlocked.remove(folder_id)
            _revision.value = _revision.value + 1
            return null
        }
        return session
    }

    @Synchronized
    fun is_unlocked(folder_id: String): Boolean {
        val session = session_for(folder_id) ?: return false
        unlocked[folder_id] = session.copy(last_used_ms = System.currentTimeMillis())
        return true
    }

    @Synchronized
    fun mark_unlocked(folder_id: String) {
        mark_unlocked(folder_id, null, null, null, null)
    }

    @Synchronized
    fun mark_unlocked(
        folder_id: String,
        unlock_token: String?,
        unlock_expires_at: String?,
        encrypted_folder_key: String? = null,
        folder_key_nonce: String? = null,
    ) {
        val now = System.currentTimeMillis()
        unlocked[folder_id] = folder_unlock_session(
            unlock_token = unlock_token,
            unlocked_at_ms = now,
            last_used_ms = now,
            expires_at_ms = parse_expiry_ms(unlock_expires_at),
            encrypted_folder_key = encrypted_folder_key,
            folder_key_nonce = folder_key_nonce,
        )
        _revision.value = _revision.value + 1
    }

    @Synchronized
    fun unlock_token_for_id(folder_id: String): String? = session_for(folder_id)?.unlock_token

    @Synchronized
    fun folder_key_material(folder_id: String): Pair<String, String>? {
        val session = session_for(folder_id) ?: return null
        val key = session.encrypted_folder_key ?: return null
        val nonce = session.folder_key_nonce ?: return null
        return key to nonce
    }

    @Synchronized
    fun set_folders(labels: List<LabelItem>) {
        val newly_protected = mutableSetOf<String>()
        for (label in labels) {
            folder_token_by_id[label.id] = label.label_token
            folder_id_by_token[label.label_token] = label.id
            val protected = label.is_password_protected && label.password_set
            if (protected) {
                if (protected_folder_ids.add(label.id)) newly_protected.add(label.label_token)
            } else {
                protected_folder_ids.remove(label.id)
            }
        }
        folders_known = true
        seeded_protected_tokens.clear()
        if (newly_protected.isNotEmpty()) {
            purge_hook?.let { hook -> runCatching { hook(newly_protected) } }
        }
    }

    @Synchronized
    fun seed_protected_tokens(tokens: Collection<String>) {
        if (folders_known) return
        val cleaned = tokens.filter { it.isNotBlank() }.toSet()
        if (cleaned == seeded_protected_tokens) return
        seeded_protected_tokens.clear()
        seeded_protected_tokens.addAll(cleaned)
        _revision.value = _revision.value + 1
    }

    @Synchronized
    fun locked_folder_tokens(): Set<String> {
        val result = mutableSetOf<String>()
        if (!folders_known) result.addAll(seeded_protected_tokens)
        for (folder_id in protected_folder_ids) {
            if (session_for(folder_id) != null) continue
            folder_token_by_id[folder_id]?.let { result.add(it) }
        }
        return result
    }

    @Synchronized
    fun protected_tokens(): Set<String> =
        protected_folder_ids.mapNotNull { folder_token_by_id[it] }.toSet()

    @Synchronized
    fun set_active_folder_token(token: String) {
        if (active_folder_token == token) return
        val previous = active_folder_token
        active_folder_token = token
        if (lock_mode == folder_lock_mode_on_leave && previous.isNotBlank() && previous != token) {
            val folder_id = folder_id_by_token[previous]
            if (folder_id != null && folder_id in protected_folder_ids) {
                lock_internal(folder_id)
            }
        }
    }

    @Synchronized
    fun note_item_folders(item_id: String?, thread_token: String?, folder_tokens: Collection<String>) {
        val tokens = folder_tokens.filter { it.isNotBlank() }.toSet()
        if (tokens.isEmpty()) return
        if (!item_id.isNullOrBlank()) item_folder_index[item_id] = tokens
        if (!thread_token.isNullOrBlank()) {
            val merged = (thread_folder_index[thread_token] ?: emptySet()) + tokens
            thread_folder_index[thread_token] = merged
        }
    }

    @Synchronized
    private fun lock_internal(folder_id: String) {
        val removed = unlocked.remove(folder_id)
        if (removed != null) {
            _revision.value = _revision.value + 1
        }
        remote_revoker?.let { revoke -> runCatching { revoke(folder_id, removed?.unlock_token) } }
    }

    @Synchronized
    fun lock(folder_id: String) {
        lock_internal(folder_id)
    }

    @Synchronized
    fun lock_all() {
        val entries = unlocked.toMap()
        unlocked.clear()
        folders_known = false
        seeded_protected_tokens.clear()
        item_folder_index.clear()
        thread_folder_index.clear()
        if (entries.isNotEmpty()) _revision.value = _revision.value + 1
        val revoke = remote_revoker
        if (revoke != null) {
            for ((folder_id, session) in entries) {
                runCatching { revoke(folder_id, session.unlock_token) }
            }
        }
    }

    @Synchronized
    fun reset() {
        unlocked.clear()
        item_folder_index.clear()
        thread_folder_index.clear()
        seeded_protected_tokens.clear()
        folders_known = false
        active_folder_token = ""
        _revision.value = _revision.value + 1
    }

    @Synchronized
    fun resolve_unlock_header(request: folder_unlock_request): String? {
        if (unlocked.isEmpty()) return null
        label_id_from_path(request.path)?.let { label_id ->
            return unlock_token_for_id(label_id)
        }
        for (key in folder_scoped_parameters) {
            val value = request.parameters[key]?.firstOrNull()
            if (!value.isNullOrBlank()) {
                return token_for_label_token(value)
            }
        }
        val thread_token = thread_token_from_path(request.path)
        if (thread_token != null) {
            thread_folder_index[thread_token]?.let { tokens ->
                token_for_any(tokens)?.let { return it }
            }
            return fallback_unlock_token()
        }
        val item_id = item_id_from_path(request.path)
        if (item_id != null) {
            item_folder_index[item_id]?.let { tokens ->
                token_for_any(tokens)?.let { return it }
            }
            return fallback_unlock_token()
        }
        if (is_attachment_path(request.path)) return fallback_unlock_token()
        return null
    }

    @Synchronized
    private fun fallback_unlock_token(): String? {
        token_for_label_token(active_folder_token)?.let { return it }
        return unlocked.keys.toList().firstNotNullOfOrNull { session_for(it)?.unlock_token }
    }

    private fun is_attachment_path(path: String): Boolean =
        path_segments(path).contains("attachments")

    @Synchronized
    private fun token_for_label_token(label_token: String): String? {
        val folder_id = folder_id_by_token[label_token] ?: return null
        return unlock_token_for_id(folder_id)
    }

    @Synchronized
    private fun token_for_any(label_tokens: Set<String>): String? {
        for (label_token in label_tokens) {
            token_for_label_token(label_token)?.let { return it }
        }
        return null
    }

    private val folder_scoped_parameters = listOf("label_token", "folder_token", "label", "folder")

    private fun path_segments(path: String): List<String> =
        path.split('/').filter { it.isNotBlank() }

    private fun label_id_from_path(path: String): String? {
        val segments = path_segments(path)
        val index = segments.indexOf("labels")
        if (index < 0 || index + 1 >= segments.size) return null
        val candidate = segments[index + 1]
        if (candidate == "bulk") return null
        return candidate
    }

    private fun thread_token_from_path(path: String): String? {
        val segments = path_segments(path)
        val index = segments.indexOf("threads")
        if (index < 0 || index + 1 >= segments.size) return null
        return java.net.URLDecoder.decode(segments[index + 1], "UTF-8")
    }

    private fun item_id_from_path(path: String): String? {
        val segments = path_segments(path)
        val messages_index = segments.indexOf("messages")
        if (messages_index >= 0 && messages_index + 1 < segments.size) {
            val candidate = segments[messages_index + 1]
            if (candidate !in reserved_message_segments) return candidate
        }
        val by_mail_index = segments.indexOf("by-mail")
        if (by_mail_index >= 0 && by_mail_index + 1 < segments.size) {
            return segments[by_mail_index + 1]
        }
        return null
    }

    private val reserved_message_segments = setOf(
        "stats",
        "sync",
        "threads",
        "bulk",
        "search",
        "counts",
    )

    private fun parse_expiry_ms(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }
}

fun is_folder_protected(label: LabelItem): Boolean =
    label.is_password_protected && label.password_set

fun requires_unlock(label: LabelItem): Boolean =
    is_folder_protected(label) && !folder_lock_store.is_unlocked(label.id)

fun protected_folder_tokens(labels: List<LabelItem>): Set<String> =
    labels.filter { requires_unlock(it) }.map { it.label_token }.toSet()

fun locked_active_folder(labels: List<LabelItem>, active_token: String): LabelItem? {
    if (active_token.isBlank()) return null
    return labels.firstOrNull { it.label_token == active_token }?.takeIf { requires_unlock(it) }
}
