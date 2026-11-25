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

internal const val THREAD_REVALIDATE_WAIT_MS = 20_000L

internal fun merge_revalidated_thread(
    current: List<ThreadMessageDecrypted>,
    fetched: List<ThreadMessageDecrypted>,
    opened_id: String,
    keep_unconfirmed: Boolean,
): List<ThreadMessageDecrypted>? {
    val server = fetched.filterNot { is_optimistic_send_id(it.id) }
    if (server.isEmpty()) return null
    val current_by_id = current.associateBy { it.id }
    val opened = current_by_id[opened_id]
    val base = if (opened != null) include_opened_message(server, opened) else server
    val kept = base.map { message ->
        val prior = current_by_id[message.id]
        if (prior != null && prior.has_readable_body() && !message.has_readable_body()) prior else message
    }
    val merged = if (keep_unconfirmed) {
        val server_ids = kept.map { it.id }.toSet()
        kept + current.filter { it.id !in server_ids && !is_optimistic_send_id(it.id) }
    } else {
        kept
    }
    return if (merged == current) null else merged
}
