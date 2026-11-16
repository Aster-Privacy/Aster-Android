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

internal fun with_tag_token(
    items: List<InboxItem>,
    ids: Set<String>,
    tag_token: String,
    add: Boolean,
): List<InboxItem> {
    if (ids.isEmpty()) return items
    var changed = false
    val next = items.map { item ->
        if (item.id !in ids) return@map item
        val has = tag_token in item.tag_tokens
        if (has == add) return@map item
        changed = true
        val tokens = if (add) item.tag_tokens + tag_token else item.tag_tokens.filter { it != tag_token }
        item.copy(tag_tokens = tokens, raw_item = item.raw_item.copy(tag_tokens = tokens))
    }
    return if (changed) next else items
}

internal fun with_restored_tag_tokens(
    items: List<InboxItem>,
    previous: Map<String, List<String>>,
): List<InboxItem> {
    if (previous.isEmpty()) return items
    var changed = false
    val next = items.map { item ->
        val tokens = previous[item.id] ?: return@map item
        if (tokens == item.tag_tokens) return@map item
        changed = true
        item.copy(tag_tokens = tokens, raw_item = item.raw_item.copy(tag_tokens = tokens))
    }
    return if (changed) next else items
}

internal fun with_folder_labels(
    items: List<InboxItem>,
    labels_by_id: Map<String, List<String>>,
): List<InboxItem> {
    if (labels_by_id.isEmpty()) return items
    var changed = false
    val next = items.map { item ->
        val labels = labels_by_id[item.id] ?: return@map item
        if (labels == item.labels) return@map item
        changed = true
        item.copy(labels = labels)
    }
    return if (changed) next else items
}
