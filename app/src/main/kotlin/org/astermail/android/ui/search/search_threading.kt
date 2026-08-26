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

package org.astermail.android.ui.search

import org.astermail.android.mail.InboxItem
import org.astermail.android.ui.mail.ThreadRow
import org.astermail.android.ui.mail.flat_thread_rows
import org.astermail.android.ui.mail.group_by_thread
import org.astermail.android.ui.mail.inbox_item_to_email

internal fun search_result_threads(
    items: List<InboxItem>,
    grouping_enabled: Boolean,
    context: android.content.Context? = null,
): List<ThreadRow> {
    val emails = items.map { inbox_item_to_email(it, context = context) }
    val rows = if (grouping_enabled) group_by_thread(emails) else flat_thread_rows(emails)
    return rows.sortedWith(
        compareByDescending<ThreadRow> { it.newest.received_at }.thenByDescending { it.thread_id },
    )
}

internal fun search_thread_member_ids(
    threads: List<ThreadRow>,
    corpus: List<InboxItem>,
    grouping_enabled: Boolean,
): Map<String, List<String>> {
    if (!grouping_enabled) return emptyMap()
    val by_thread = corpus.groupBy { item ->
        item.thread_token?.takeIf { it.isNotBlank() } ?: item.id
    }
    return threads.associate { row ->
        row.newest.id to (by_thread[row.thread_id]?.map { it.id } ?: listOf(row.newest.id))
    }
}

internal fun expand_thread_selection(
    ids: List<String>,
    member_ids: Map<String, List<String>>,
    grouping_enabled: Boolean,
): List<String> =
    if (!grouping_enabled) ids else ids.flatMap { member_ids[it] ?: listOf(it) }.distinct()
