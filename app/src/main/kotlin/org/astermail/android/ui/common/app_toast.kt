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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
        val repeats = previous != null &&
            is_plain(previous) &&
            is_plain(toast) &&
            previous.message == toast.message
        val next = if (previous != null && (accumulates || repeats)) toast.copy(key = previous.key) else toast
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

    private fun is_plain(toast: TopToastState): Boolean =
        toast.undo_label == null &&
            toast.on_undo == null &&
            toast.secondary_label == null &&
            toast.on_secondary == null &&
            toast.on_tap == null &&
            !toast.show_close &&
            toast.on_close == null &&
            toast.on_timeout == null

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

internal const val toast_window_swap_ms = 120L
private val toast_window_pull_room = 32.dp

private var popup_layer_generation by mutableIntStateOf(0)

@Composable
private fun register_popup_layer() {
    DisposableEffect(Unit) {
        popup_layer_generation += 1
        onDispose { }
    }
}

private fun find_activity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private class toast_window_manager(
    private val activity: Activity,
    private val parent: CompositionContext,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var attached_generation = 0
    private val retiring = mutableListOf<ComposeView>()

    var failed by mutableStateOf(false)
    var density by mutableStateOf<Density?>(null)
    var status_top_px by mutableIntStateOf(0)

    fun sync(wants_window: Boolean, generation: Int) {
        if (failed) return
        when (toast_window_action(view != null, attached_generation, generation, wants_window)) {
            ToastWindowAction.none -> Unit
            ToastWindowAction.attach -> {
                view = add_window(animate_entry = true)
                attached_generation = generation
            }
            ToastWindowAction.raise -> {
                attached_generation = generation
                val previous = view ?: return
                val next = add_window(animate_entry = false) ?: return
                view = next
                retiring.add(previous)
                handler.postDelayed({
                    if (retiring.remove(previous)) remove_window(previous)
                }, toast_window_swap_ms)
            }
            ToastWindowAction.detach -> {
                view?.let { remove_window(it) }
                view = null
            }
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        retiring.forEach { remove_window(it) }
        retiring.clear()
        view?.let { remove_window(it) }
        view = null
    }

    private fun add_window(animate_entry: Boolean): ComposeView? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val host_window = activity.window ?: return null
        val decor = host_window.decorView
        if (!decor.isAttachedToWindow) return null
        val lifecycle_owner = decor.findViewTreeLifecycleOwner() ?: activity as? LifecycleOwner
        val saved_state_owner = decor.findViewTreeSavedStateRegistryOwner() ?: activity as? SavedStateRegistryOwner
        val view_model_owner = decor.findViewTreeViewModelStoreOwner() ?: activity as? ViewModelStoreOwner
        if (lifecycle_owner == null || saved_state_owner == null) {
            failed = true
            return null
        }
        val compose_view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(lifecycle_owner)
            setViewTreeSavedStateRegistryOwner(saved_state_owner)
            if (view_model_owner != null) setViewTreeViewModelStoreOwner(view_model_owner)
            setParentCompositionContext(this@toast_window_manager.parent)
            setContent { toast_window_content(this@toast_window_manager, decor, animate_entry) }
        }
        val secure = host_window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                secure,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return try {
            activity.windowManager.addView(compose_view, params)
            compose_view
        } catch (error: WindowManager.BadTokenException) {
            failed = true
            null
        } catch (error: IllegalStateException) {
            failed = true
            null
        }
    }

    private fun remove_window(target: ComposeView) {
        try {
            activity.windowManager.removeViewImmediate(target)
        } catch (error: IllegalArgumentException) {
        }
        target.disposeComposition()
    }
}

@Composable
private fun toast_window_content(manager: toast_window_manager, host: View, animate_entry: Boolean) {
    val latest by app_toast.state.collectAsState()
    var ready by remember { mutableStateOf(!animate_entry) }
    LaunchedEffect(Unit) { ready = true }
    val current = if (ready) latest else null
    val window_view = LocalView.current
    val density = manager.density ?: LocalDensity.current
    var offset_px by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalDensity provides density) {
        val top_padding = with(density) { (manager.status_top_px - offset_px).coerceAtLeast(0).toDp() }
        Box(
            modifier = Modifier
                .onGloballyPositioned {
                    val own = IntArray(2)
                    val base = IntArray(2)
                    window_view.getLocationOnScreen(own)
                    host.getLocationOnScreen(base)
                    val next = own[1] - base[1]
                    if (next != offset_px) offset_px = next
                }
                .padding(bottom = toast_window_pull_room),
        ) {
            top_toast_overlay(
                state = current,
                on_dismiss = { app_toast.dismiss(it.key) },
                on_hold = { toast, held -> app_toast.hold(toast.key, held) },
                fill_width = false,
                top_padding = top_padding,
            )
        }
    }
}

@Composable
fun app_toast_host(nested: Boolean = false) {
    if (nested) {
        register_popup_layer()
        return
    }
    val context = LocalContext.current
    val activity = remember(context) { find_activity(context) }
    val parent = rememberCompositionContext()
    val density = LocalDensity.current
    val status_top = WindowInsets.statusBars.getTop(density)
    val manager = remember(activity, parent) { activity?.let { toast_window_manager(it, parent) } }
    val current by app_toast.state.collectAsState()
    var lingering by remember { mutableStateOf(current != null) }
    LaunchedEffect(current) {
        if (current != null) {
            lingering = true
        } else {
            delay(toast_settle_ms)
            lingering = false
        }
    }
    DisposableEffect(manager) {
        onDispose { manager?.release() }
    }
    if (manager == null || manager.failed) {
        top_toast_overlay(
            state = current,
            on_dismiss = { app_toast.dismiss(it.key) },
            on_hold = { toast, held -> app_toast.hold(toast.key, held) },
        )
        return
    }
    val generation = popup_layer_generation
    val wants_window = current != null || lingering
    SideEffect {
        manager.density = density
        manager.status_top_px = status_top
        manager.sync(wants_window, generation)
    }
}

@Composable
fun sheet_toast_host() {
    register_popup_layer()
}
