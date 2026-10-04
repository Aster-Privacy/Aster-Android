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

private val search_fetched_item_types = setOf("sent", "draft")

fun search_scope_query(folder: String): String? = when (folder) {
    "trash" -> "in:trash"
    "archive" -> "in:archive"
    "spam" -> "in:spam"
    "starred" -> "is:starred"
    "sent" -> "in:sent"
    "drafts" -> "in:drafts"
    else -> null
}

fun search_folder_for_scope(value: String): String? = when (value) {
    "sent" -> "sent"
    "drafts", "draft" -> "drafts"
    else -> null
}

fun search_folder_item_type(folder: String): String? = when (folder) {
    "sent" -> "sent"
    "drafts" -> "draft"
    else -> null
}

fun replace_search_folder_items(
    current: List<InboxItem>,
    item_type: String,
    fetched: List<InboxItem>,
): List<InboxItem> {
    val fetched_ids = fetched.mapTo(HashSet()) { it.id }
    return current.filterNot { it.raw_item.item_type == item_type || it.id in fetched_ids } + fetched
}

fun carry_search_folder_items(
    fresh: List<InboxItem>,
    previous: List<InboxItem>,
): List<InboxItem> {
    val fresh_ids = fresh.mapTo(HashSet()) { it.id }
    val carried = previous.filter { it.raw_item.item_type in search_fetched_item_types && it.id !in fresh_ids }
    return if (carried.isEmpty()) fresh else fresh + carried
}
