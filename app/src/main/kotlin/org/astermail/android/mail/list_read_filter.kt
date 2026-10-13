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

enum class ListReadFilter { all, unread, read }

fun list_read_filter_query(filter: ListReadFilter): Boolean? = when (filter) {
    ListReadFilter.all -> null
    ListReadFilter.unread -> false
    ListReadFilter.read -> true
}

fun folder_supports_read_filter(folder: String): Boolean =
    folder != "drafts" && folder != "scheduled"

fun effective_read_filter(folder: String, wanted: ListReadFilter): ListReadFilter =
    if (folder_supports_read_filter(folder)) wanted else ListReadFilter.all

fun read_filter_allows_cache_write(filter: ListReadFilter): Boolean =
    filter == ListReadFilter.all

fun read_filter_offset_base(read_flags: List<Boolean>, filter: ListReadFilter): Int = when (filter) {
    ListReadFilter.all -> read_flags.size
    ListReadFilter.unread -> read_flags.count { !it }
    ListReadFilter.read -> read_flags.count { it }
}

fun mark_all_has_unread(
    filter_active: Boolean,
    loaded_has_unread: Boolean,
    server_unread_count: Int?,
): Boolean = when {
    loaded_has_unread -> true
    filter_active -> false
    else -> (server_unread_count ?: 0) > 0
}
