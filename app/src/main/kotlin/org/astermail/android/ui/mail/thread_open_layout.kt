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

const val thread_visible_tail_count = 2
const val thread_body_wait_ms = 700L

data class ThreadOpenLayout(
    val known_ids: Set<String>,
    val hidden_ids: Set<String>,
    val expanded_ids: Set<String>,
)

fun thread_is_complete(
    loaded_count: Int,
    expected_count: Int,
    settled: Boolean,
    any_body_pending: Boolean,
): Boolean {
    if (loaded_count <= 0) return false
    if (settled) return true
    return loaded_count >= expected_count && !any_body_pending
}

fun initial_thread_layout(
    message_ids: List<String>,
    opened_id: String,
    tail_count: Int = thread_visible_tail_count,
): ThreadOpenLayout {
    val known = message_ids.toSet()
    if (message_ids.size <= 1) {
        return ThreadOpenLayout(known_ids = known, hidden_ids = emptySet(), expanded_ids = known)
    }
    val last_id = message_ids.last()
    val opened_index = message_ids.indexOf(opened_id)
    val hidden = if (message_ids.size <= tail_count + 2) {
        emptySet()
    } else {
        val range = 1 until message_ids.size - tail_count
        if (opened_index in range) emptySet() else range.map { message_ids[it] }.toSet()
    }
    val expanded = if (opened_index >= 0) setOf(last_id, opened_id) else setOf(last_id)
    return ThreadOpenLayout(known_ids = known, hidden_ids = hidden, expanded_ids = expanded)
}

fun thread_message_is_expanded(
    message_id: String,
    is_last: Boolean,
    total_messages: Int,
    expanded_ids: Set<String>,
    known_ids: Set<String>,
): Boolean = total_messages <= 1 ||
    expanded_ids.contains(message_id) ||
    (is_last && message_id !in known_ids)

fun thread_reveal_ready(
    complete: Boolean,
    expanded_ids: Set<String>,
    hidden_ids: Set<String>,
    ready_body_ids: Set<String>,
    body_wait_expired: Boolean,
): Boolean {
    if (!complete) return false
    if (body_wait_expired) return true
    return expanded_ids.none { it !in hidden_ids && it !in ready_body_ids }
}
