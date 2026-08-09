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

import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem

fun raw_item_folder_tokens(item: MailItem): Set<String> {
    val tokens = mutableSetOf<String>()
    item.folder_token?.takeIf { it.isNotBlank() }?.let { tokens.add(it) }
    item.folders?.forEach { folder ->
        folder.folder_token?.takeIf { it.isNotBlank() }?.let { tokens.add(it) }
    }
    item.labels?.forEach { label ->
        label.folder_token?.takeIf { it.isNotBlank() }?.let { tokens.add(it) }
    }
    return tokens
}

fun inbox_item_folder_tokens(item: InboxItem): Set<String> =
    item.labels.filter { it.isNotBlank() }.toSet() + raw_item_folder_tokens(item.raw_item)

fun record_item_folders(items: List<InboxItem>) {
    for (item in items) {
        folder_lock_store.note_item_folders(
            item_id = item.id,
            thread_token = item.thread_token,
            folder_tokens = inbox_item_folder_tokens(item),
        )
    }
}

fun is_item_in_locked_folder(item: InboxItem, locked_tokens: Set<String>): Boolean {
    if (locked_tokens.isEmpty()) return false
    return inbox_item_folder_tokens(item).any { it in locked_tokens }
}

fun filter_locked_items(items: List<InboxItem>): List<InboxItem> {
    val locked = folder_lock_store.locked_folder_tokens()
    if (locked.isEmpty()) return items
    return items.filterNot { is_item_in_locked_folder(it, locked) }
}
