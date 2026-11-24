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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val toast_edge_state = MutableStateFlow(toast_edge_for(null))

val toast_edge: StateFlow<ToastEdge> = toast_edge_state.asStateFlow()

fun set_toast_position(value: String?) {
    toast_edge_state.value = toast_edge_for(value)
}

private val toast_window_pull_room = 32.dp
private val toast_window_card_chrome = 60.dp
private val toast_window_control_floor = 32.dp
private val toast_window_text_floor = 36.sp
private val toast_window_bottom_clearance = 72.dp

private var popup_layer_generation by mutableIntStateOf(0)
private var popup_layers_open = 0

@Composable
private fun register_popup_layer() {
    DisposableEffect(Unit) {
        popup_layer_generation += 1
        popup_layers_open += 1
        onDispose { popup_layers_open = (popup_layers_open - 1).coerceAtLeast(0) }
    }
}

private class toast_window_view(
    context: Context,
    private val host: View,
) : AbstractComposeView(context) {
    private val content = mutableStateOf<(@Composable () -> Unit)?>(null)
    private val cards = LinkedHashMap<Long, toast_card_box>()
    private val own_location = IntArray(2)
    private val host_location = IntArray(2)
    private var route = ToastTouchRoute.toast

    val report_bounds: (Long, toast_card_box?) -> Unit = { key, box ->
        if (box == null) cards.remove(key) else cards[key] = box
    }

    override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
        private set

    fun set_content(next: @Composable () -> Unit) {
        shouldCreateCompositionOnAttachedToWindow = true
        content.value = next
        if (isAttachedToWindow) createComposition()
    }

    @Composable
    override fun Content() {
        content.value?.invoke()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            route = toast_touch_route(cards.values, event.x, event.y, popup_layers_open)
        }
        return when (route) {
            ToastTouchRoute.toast -> super.dispatchTouchEvent(event)
            ToastTouchRoute.drop -> true
            ToastTouchRoute.host -> {
                forward_to_host(event)
                true
            }
        }
    }

    private fun forward_to_host(event: MotionEvent) {
        if (!host.isAttachedToWindow) return
        getLocationOnScreen(own_location)
        host.getLocationOnScreen(host_location)
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation(
            (own_location[0] - host_location[0]).toFloat(),
            (own_location[1] - host_location[1]).toFloat(),
        )
        try {
            host.dispatchTouchEvent(copy)
        } finally {
            copy.recycle()
        }
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
    private var view: toast_window_view? = null
    private var attached_generation = 0
    private var touchable = false
    private var secure = false

    var failed by mutableStateOf(false)
    var density by mutableStateOf<Density?>(null)
    var applied_spec by mutableStateOf<toast_window_spec?>(null)
    var card_present by mutableStateOf(false)

    fun sync(wants_window: Boolean, generation: Int, spec: toast_window_spec, present: Boolean) {
        if (failed) return
        val idle = !present
        val applied = applied_spec
        val spec_matches = !wants_window || applied == null || applied.copy(secure = spec.secure) == spec
        when (toast_window_action(view != null, attached_generation, generation, wants_window, idle, spec_matches)) {
            ToastWindowAction.none -> Unit
            ToastWindowAction.attach -> {
                applied_spec = spec
                attached_generation = generation
                view = add_window(spec)
            }
            ToastWindowAction.raise -> {
                attached_generation = generation
                val previous = view
                view = null
                previous?.let { remove_window(it) }
                applied_spec = spec
                view = add_window(spec)
            }
            ToastWindowAction.relayout -> {
                applied_spec = spec
                view?.let { update_layout(it, spec) }
            }
        }
        update_flags(touchable = present, secure = spec.secure)
    }

    private fun update_flags(touchable: Boolean, secure: Boolean) {
        val target = view ?: return
        if (this.touchable == touchable && this.secure == secure) return
        val params = target.layoutParams as? WindowManager.LayoutParams ?: return
        val secured = if (secure) {
            params.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        params.flags = toast_window_flags(secured, touchable)
        try {
            activity.windowManager.updateViewLayout(target, params)
            this.touchable = touchable
            this.secure = secure
        } catch (error: IllegalArgumentException) {
        }
    }

    private fun update_layout(target: toast_window_view, spec: toast_window_spec) {
        val params = target.layoutParams as? WindowManager.LayoutParams ?: return
        params.height = spec.height_px
        params.gravity = toast_window_gravity(spec.edge)
        params.x = 0
        params.y = 0
        try {
            activity.windowManager.updateViewLayout(target, params)
        } catch (error: IllegalArgumentException) {
        }
    }

    fun release() {
        view?.let { remove_window(it) }
        view = null
        applied_spec = null
        card_present = false
    }

    private fun add_window(spec: toast_window_spec): toast_window_view? {
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
        val compose_view = toast_window_view(activity, decor).apply {
            setViewTreeLifecycleOwner(lifecycle_owner)
            setViewTreeSavedStateRegistryOwner(saved_state_owner)
            if (view_model_owner != null) setViewTreeViewModelStoreOwner(view_model_owner)
            setParentCompositionContext(this@toast_window_manager.parent)
        }
        compose_view.set_content {
            CompositionLocalProvider(local_toast_card_bounds provides compose_view.report_bounds) {
                toast_window_content(this@toast_window_manager, decor)
            }
        }
        val secure_flag = if (spec.secure) WindowManager.LayoutParams.FLAG_SECURE else 0
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            spec.height_px,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            toast_window_flags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    secure_flag,
                touchable = false,
            ),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = toast_window_gravity(spec.edge)
            x = 0
            y = 0
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
            touchable = false
            secure = spec.secure
            compose_view
        } catch (error: WindowManager.BadTokenException) {
            failed = true
            null
        } catch (error: IllegalStateException) {
            failed = true
            null
        }
    }

    private fun remove_window(target: toast_window_view) {
        try {
            activity.windowManager.removeViewImmediate(target)
        } catch (error: IllegalArgumentException) {
        }
        target.disposeComposition()
    }
}

@Composable
private fun toast_window_content(manager: toast_window_manager, host: View) {
    val current by app_toast.state.collectAsState()
    val spec = manager.applied_spec ?: return
    val window_view = LocalView.current
    val density = manager.density ?: LocalDensity.current
    var gap_px by remember { mutableIntStateOf(0) }
    val edge = spec.edge
    CompositionLocalProvider(LocalDensity provides density) {
        val inset = with(density) { (spec.offset_px - gap_px).coerceAtLeast(0).toDp() }
        val edge_padding = if (edge == ToastEdge.top) inset else inset + toast_window_bottom_clearance
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val own = IntArray(2)
                    val base = IntArray(2)
                    window_view.getLocationOnScreen(own)
                    host.getLocationOnScreen(base)
                    val next = if (edge == ToastEdge.top) {
                        own[1] - base[1]
                    } else {
                        (base[1] + host.height) - (own[1] + window_view.height)
                    }
                    if (next != gap_px) gap_px = next
                },
            contentAlignment = if (edge == ToastEdge.top) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            top_toast_overlay(
                state = current,
                on_dismiss = { app_toast.dismiss(it.key) },
                on_hold = { toast, held -> app_toast.hold(toast.key, held) },
                fill_width = true,
                top_padding = edge_padding,
                edge = edge,
                animate_first = true,
                on_presence = { present -> manager.card_present = present },
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
    val edge by toast_edge.collectAsState()
    val status_top = WindowInsets.statusBars.getTop(density)
    val bottom_inset = maxOf(
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.ime.getBottom(density),
    )
    val manager = remember(activity, parent) { activity?.let { toast_window_manager(it, parent) } }
    val current by app_toast.state.collectAsState()
    DisposableEffect(manager) {
        onDispose { manager?.release() }
    }
    if (manager == null || manager.failed) {
        top_toast_overlay(
            state = current,
            on_dismiss = { app_toast.dismiss(it.key) },
            on_hold = { toast, held -> app_toast.hold(toast.key, held) },
            edge = edge,
        )
        return
    }
    val band_px = with(density) {
        (toast_window_card_chrome + toast_window_pull_room).roundToPx() +
            maxOf(toast_window_control_floor.roundToPx(), toast_window_text_floor.roundToPx())
    }
    val spec = if (edge == ToastEdge.top) {
        toast_window_spec(edge, status_top + band_px, status_top, secure = false)
    } else {
        val clearance = with(density) { toast_window_bottom_clearance.roundToPx() }
        toast_window_spec(edge, bottom_inset + clearance + band_px, bottom_inset, secure = false)
    }
    val host_secure = ((activity?.window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
    val generation = popup_layer_generation
    val wants_window = current != null
    val present = manager.card_present
    SideEffect {
        manager.density = density
        manager.sync(wants_window, generation, spec.copy(secure = host_secure), present)
    }
}

@Composable
fun sheet_toast_host() {
    register_popup_layer()
}
