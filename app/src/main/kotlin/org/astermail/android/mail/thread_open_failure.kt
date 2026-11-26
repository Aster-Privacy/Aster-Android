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

data class ThreadOptimisticTarget(
    val item_id: String?,
    val thread_token: String?,
)

fun thread_optimistic_target(state: ThreadUiState): ThreadOptimisticTarget {
    val item = state.item
    if (item != null) return ThreadOptimisticTarget(item.id, item.thread_token)
    return ThreadOptimisticTarget(state.requested_id, state.requested_thread_token)
}

fun thread_state_after_open_failure(
    item_id: String,
    keep: ThreadUiState,
    seed: InboxItem?,
    offline_fallback: ThreadMessageDecrypted?,
    missing_on_server: Boolean,
    transient: Boolean,
    error_text: String,
): ThreadUiState {
    val kept = keep.item?.id == item_id && keep.messages.any { !it.is_body_pending }
    val offline = transient && !missing_on_server
    return when {
        kept -> keep.copy(is_loading = false, error = null, offline = offline)
        offline && offline_fallback != null && seed != null ->
            ThreadUiState(messages = listOf(offline_fallback), item = seed, offline = true)
        else -> ThreadUiState(
            error = error_text,
            offline = offline,
            requested_id = item_id,
            requested_thread_token = seed?.takeIf { it.id == item_id }?.thread_token?.takeIf { it.isNotBlank() },
        )
    }
}
