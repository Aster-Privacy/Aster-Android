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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val toast_transition_delay_ms = 280L
const val toast_exit_gap_ms = 220L
const val toast_settle_ms = 450L
const val toast_plain_duration_ms = 2600L
const val toast_action_duration_ms = 4500L

class ToastController(private val scope: CoroutineScope) {
    private val visible = MutableStateFlow<TopToastState?>(null)
    private var transient: TopToastState? = null
    private var sticky: TopToastState? = null
    private var held_key: Long? = null
    private var timer: Job? = null
    private var swap: Job? = null
    private var settling: Job? = null
    private var delayed: Job? = null

    val state: StateFlow<TopToastState?> = visible.asStateFlow()

    fun show(message: String) {
        show(TopToastState(message = message))
    }

    @Synchronized
    fun show(toast: TopToastState) {
        delayed?.cancel()
        delayed = null
        val previous = transient
        val accumulates = previous != null &&
            toast.accumulation_key != null &&
            previous.accumulation_key == toast.accumulation_key
        val next = if (previous != null && accumulates) toast.copy(key = previous.key) else toast
        transient = next
        if (held_key != next.key) held_key = null
        start_timer(next)
        render()
        if (previous != null && previous.key != next.key) previous.on_timeout?.invoke()
    }

    @Synchronized
    fun show_after_transition(message: String) {
        delayed?.cancel()
        delayed = scope.launch {
            delay(toast_transition_delay_ms)
            show_delayed(message)
        }
    }

    @Synchronized
    fun show_sticky(toast: TopToastState) {
        sticky = toast
        render()
    }

    @Synchronized
    fun hide_sticky(key: Long) {
        if (sticky?.key != key) return
        sticky = null
        render()
    }

    @Synchronized
    fun dismiss(key: Long) {
        when (key) {
            transient?.key -> clear_transient()
            sticky?.key -> sticky = null
            else -> return
        }
        render()
    }

    @Synchronized
    fun dismiss() {
        delayed?.cancel()
        delayed = null
        if (transient == null) return
        clear_transient()
        render()
    }

    @Synchronized
    fun dismiss_accumulated() {
        if (transient?.accumulation_key == null) return
        clear_transient()
        render()
    }

    @Synchronized
    fun hold(key: Long, held: Boolean) {
        val current = transient
        if (held) {
            if (current?.key != key) return
            held_key = key
            timer?.cancel()
            timer = null
            return
        }
        if (held_key != key) return
        held_key = null
        if (current?.key == key) start_timer(current)
    }

    @Synchronized
    private fun show_delayed(message: String) {
        delayed = null
        show(TopToastState(message = message))
    }

    @Synchronized
    private fun timed_out(key: Long) {
        val current = transient ?: return
        if (current.key != key) return
        clear_transient()
        render()
        current.on_timeout?.invoke()
    }

    @Synchronized
    private fun gap_finished() {
        swap = null
        present(transient ?: sticky)
    }

    private fun present(target: TopToastState?) {
        visible.value = target
        settling?.cancel()
        settling = if (target == null) null else scope.launch { delay(toast_settle_ms) }
    }

    private fun clear_transient() {
        transient = null
        held_key = null
        timer?.cancel()
        timer = null
    }

    private fun start_timer(toast: TopToastState) {
        timer?.cancel()
        timer = null
        if (held_key == toast.key) return
        val duration = toast.duration_ms
            ?: if (toast.on_undo != null) toast_action_duration_ms else toast_plain_duration_ms
        val key = toast.key
        timer = scope.launch {
            delay(duration)
            timed_out(key)
        }
    }

    private fun render() {
        val target = transient ?: sticky
        val shown = visible.value
        if (shown != null) {
            if (target != null && target.key == shown.key) {
                visible.value = target
                return
            }
            if (target != null && settling?.isActive == true) {
                present(target)
                return
            }
            visible.value = null
            swap?.cancel()
            swap = scope.launch {
                delay(toast_exit_gap_ms)
                gap_finished()
            }
            return
        }
        if (swap?.isActive == true) return
        present(target)
    }
}

val app_toast = ToastController(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

@Composable
fun app_toast_host() {
    val current by app_toast.state.collectAsState()
    top_toast_overlay(
        state = current,
        on_dismiss = { app_toast.dismiss(it.key) },
        on_hold = { toast, held -> app_toast.hold(toast.key, held) },
    )
}
