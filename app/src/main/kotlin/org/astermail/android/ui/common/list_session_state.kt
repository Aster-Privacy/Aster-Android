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

package org.astermail.android.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object app_session {

    val process_token: String = java.util.UUID.randomUUID().toString()

    private val foreground_epoch_state = MutableStateFlow(0)

    val foreground_epoch: StateFlow<Int> = foreground_epoch_state

    private var was_backgrounded = false

    fun mark_backgrounded() {
        was_backgrounded = true
    }

    fun mark_foregrounded() {
        if (!was_backgrounded) return
        was_backgrounded = false
        foreground_epoch_state.value += 1
    }
}

internal fun session_lazy_list_state_saver(): Saver<LazyListState, Any> = listSaver(
    save = {
        listOf(
            app_session.process_token,
            it.firstVisibleItemIndex,
            it.firstVisibleItemScrollOffset,
        )
    },
    restore = {
        val token = it.getOrNull(0) as? String
        val index = it.getOrNull(1) as? Int ?: 0
        val offset = it.getOrNull(2) as? Int ?: 0
        if (token == app_session.process_token) {
            LazyListState(index, offset)
        } else {
            LazyListState(0, 0)
        }
    },
)

@Composable
fun remember_session_lazy_list_state(): LazyListState =
    rememberSaveable(saver = session_lazy_list_state_saver()) { LazyListState(0, 0) }
