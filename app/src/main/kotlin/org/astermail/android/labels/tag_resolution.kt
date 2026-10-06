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

package org.astermail.android.labels

import org.astermail.android.api.tags.TagItem
import org.astermail.android.mail.InboxItem

fun merge_tag_snapshot(previous: List<TagItem>, incoming: List<TagItem>): List<TagItem> {
    if (previous.isEmpty()) return incoming
    if (incoming.isEmpty()) return incoming
    val resolved = previous.filter { it.encrypted_name.isNotBlank() }.associateBy { it.tag_token }
    if (resolved.isEmpty()) return incoming
    return incoming.map { tag ->
        if (tag.encrypted_name.isNotBlank()) return@map tag
        val known = resolved[tag.tag_token] ?: return@map tag
        tag.copy(
            encrypted_name = known.encrypted_name,
            encrypted_color = known.encrypted_color,
            encrypted_icon = known.encrypted_icon,
        )
    }
}

fun merge_tag_tokens(server_tokens: List<String>, pending: Map<String, Boolean>): List<String> {
    if (pending.isEmpty()) return server_tokens
    val kept = server_tokens.filter { pending[it] != false }
    val added = pending.entries.filter { it.value && it.key !in kept }.map { it.key }
    return if (added.isEmpty()) kept else kept + added
}

data class ConversationMember(
    val id: String,
    val thread_token: String?,
    val tag_tokens: List<String>,
)

fun InboxItem.as_conversation_member(): ConversationMember =
    ConversationMember(id = id, thread_token = thread_token, tag_tokens = tag_tokens)

private fun conversation_siblings(own: ConversationMember, loaded: List<ConversationMember>): List<ConversationMember> {
    val token = own.thread_token?.takeIf { it.isNotBlank() } ?: return emptyList()
    return loaded.filter { it.id != own.id && it.thread_token == token }
}

fun conversation_tag_tokens(own: ConversationMember?, loaded: List<ConversationMember>): List<String> {
    if (own == null) return emptyList()
    return (own.tag_tokens + conversation_siblings(own, loaded).flatMap { it.tag_tokens }).distinct()
}

fun conversation_item_ids(
    own: ConversationMember,
    loaded: List<ConversationMember>,
    message_ids: List<String>,
): List<String> =
    (listOf(own.id) + message_ids + conversation_siblings(own, loaded).map { it.id })
        .filter { it.isNotBlank() }
        .distinct()
