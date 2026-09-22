// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val toast_transition_delay_ms = 280L
private const val toast_exit_gap_ms = 220L

object app_toast {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null

    val state = MutableStateFlow<TopToastState?>(null)

    fun show(message: String) {
        show(TopToastState(message = message))
    }

    fun show(toast: TopToastState) {
        pending?.cancel()
        val current = state.value
        if (current == null || current.key == toast.key) {
            state.value = toast
            return
        }
        pending = scope.launch {
            state.value = null
            delay(toast_exit_gap_ms)
            state.value = toast
        }
    }

    fun show_after_transition(message: String) {
        pending?.cancel()
        pending = scope.launch {
            delay(toast_transition_delay_ms)
            if (state.value != null) {
                state.value = null
                delay(toast_exit_gap_ms)
            }
            state.value = TopToastState(message = message)
        }
    }

    fun dismiss() {
        pending?.cancel()
        state.value = null
    }
}

@Composable
fun app_toast_host() {
    val current by app_toast.state.collectAsStateWithLifecycle()
    top_toast_overlay(
        state = current,
        on_dismiss = { app_toast.dismiss() },
        duration_ms = 2600,
    )
}
