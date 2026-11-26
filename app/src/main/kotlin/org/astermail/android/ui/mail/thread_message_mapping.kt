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

package org.astermail.android.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.mail.ThreadMessageDecrypted

internal const val SYNC_THREAD_MAPPING_LIMIT = 3

internal object thread_body_parts_cache {
    private const val capacity = 256
    private val store = object : LinkedHashMap<String, thread_body_parts>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, thread_body_parts>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun get(id: String, identity: Long): thread_body_parts? = store[id]?.takeIf { it.identity == identity }

    @Synchronized
    fun put(id: String, parts: thread_body_parts) {
        store[id] = parts
    }
}

private fun with_thread_attachments(
    message: ThreadMessage,
    attachments: List<MessageAttachment>?,
): ThreadMessage =
    if (attachments.isNullOrEmpty()) message else thread_message_with_attachments(message, attachments)

internal fun cached_thread_messages(
    messages: List<ThreadMessageDecrypted>,
    attachments: Map<String, List<MessageAttachment>>,
): List<ThreadMessage>? {
    val parts = ArrayList<thread_body_parts>(messages.size)
    for (msg in messages) {
        parts.add(thread_body_parts_cache.get(msg.id, thread_body_identity(msg)) ?: return null)
    }
    return messages.mapIndexed { index, msg ->
        with_thread_attachments(thread_message_from_parts(msg, parts[index]), attachments[msg.id])
    }
}

internal fun map_thread_messages(
    messages: List<ThreadMessageDecrypted>,
    attachments: Map<String, List<MessageAttachment>>,
): List<ThreadMessage> = messages.map { msg ->
    val identity = thread_body_identity(msg)
    val parts = thread_body_parts_cache.get(msg.id, identity)
        ?: thread_body_parts_of(msg, identity).also { thread_body_parts_cache.put(msg.id, it) }
    with_thread_attachments(thread_message_from_parts(msg, parts), attachments[msg.id])
}

private class mapped_thread_messages(
    val source: List<ThreadMessageDecrypted>,
    val attachments: Map<String, List<MessageAttachment>>,
    val messages: List<ThreadMessage>,
)

private fun List<ThreadMessage>.shares_ids_with(source: List<ThreadMessageDecrypted>): Boolean {
    if (isEmpty() || source.isEmpty()) return false
    val ids = source.mapTo(HashSet(source.size)) { it.id }
    return any { it.id in ids }
}

@Composable
internal fun mapped_thread_messages_state(
    source: List<ThreadMessageDecrypted>,
    attachments: Map<String, List<MessageAttachment>>,
): List<ThreadMessage> {
    val mapped = remember { mutableStateOf<mapped_thread_messages?>(null) }
    val ready = remember(source, attachments) {
        val prior = Snapshot.withoutReadObservation { mapped.value }
        when {
            prior != null && prior.source === source && prior.attachments === attachments -> prior.messages
            else -> cached_thread_messages(source, attachments)
                ?: if (source.size <= SYNC_THREAD_MAPPING_LIMIT && prior?.messages?.shares_ids_with(source) != true) {
                    map_thread_messages(source, attachments)
                } else {
                    null
                }
        }
    }
    LaunchedEffect(source, attachments) {
        val result = ready ?: withContext(Dispatchers.Default) { map_thread_messages(source, attachments) }
        mapped.value = mapped_thread_messages(source, attachments, result)
    }
    if (ready != null) return ready
    val stale = mapped.value ?: return emptyList()
    if (stale.source === source && stale.attachments === attachments) return stale.messages
    return if (stale.messages.shares_ids_with(source)) stale.messages else emptyList()
}
