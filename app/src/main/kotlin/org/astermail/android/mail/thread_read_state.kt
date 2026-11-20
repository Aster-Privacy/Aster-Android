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

package org.astermail.android.mail

internal fun thread_with_read(state: ThreadUiState, item_id: String, is_read: Boolean): ThreadUiState {
    val item = state.item
    val item_changes = item != null && item.id == item_id && item.is_read != is_read
    val message_changes = state.messages.any { it.id == item_id && it.is_read != is_read }
    if (!item_changes && !message_changes) return state
    return state.copy(
        item = if (item_changes) item?.copy(is_read = is_read) else item,
        messages = if (message_changes) {
            state.messages.map { if (it.id == item_id && it.is_read != is_read) it.copy(is_read = is_read) else it }
        } else {
            state.messages
        },
    )
}
