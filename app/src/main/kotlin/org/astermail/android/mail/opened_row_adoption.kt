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

fun inbox_row_needs_repair(item: InboxItem): Boolean =
    item.is_undecryptable ||
        item.is_decrypt_pending ||
        item.is_envelope_missing ||
        item.is_envelope_settling ||
        inbox_row_is_blank(item)

fun inbox_row_is_settled(item: InboxItem): Boolean =
    !inbox_row_needs_repair(item) && !item.is_pgp_placeholder

fun adopt_opened_row(items: List<InboxItem>, opened: InboxItem): List<InboxItem> {
    if (!inbox_row_is_settled(opened)) return items
    if (items.none { it.id == opened.id && inbox_row_needs_repair(it) }) return items
    return items.map { row ->
        if (row.id != opened.id || !inbox_row_needs_repair(row)) {
            row
        } else {
            opened.copy(
                thread_token = row.thread_token ?: opened.thread_token,
                thread_message_count = maxOf(row.thread_message_count, opened.thread_message_count),
                is_read = row.is_read,
                is_starred = row.is_starred,
                is_trashed = row.is_trashed,
                is_archived = row.is_archived,
                is_spam = row.is_spam,
                labels = row.labels.ifEmpty { opened.labels },
                tag_tokens = row.tag_tokens.ifEmpty { opened.tag_tokens },
                received_on = row.received_on ?: opened.received_on,
                routing_token = row.routing_token ?: opened.routing_token,
                has_unseen_reaction = row.has_unseen_reaction,
            )
        }
    }
}
