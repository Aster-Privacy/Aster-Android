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

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val ZOOM_MIN_SCALE = 1f
internal const val ZOOM_DEFAULT_MAX_SCALE = 8f
internal const val ZOOM_DOUBLE_TAP_SCALE = 2.5f
private const val ZOOM_ACTIVE_THRESHOLD = 1.01f
private const val ZOOM_DOUBLE_TAP_MS = 220
private const val ZOOM_DOUBLE_TAP_SLOP_FACTOR = 6f

internal fun zoom_pan_bounds(content: Size, container: Size, scale: Float): Offset {
    val width = if (content.width > 0f) content.width else container.width
    val height = if (content.height > 0f) content.height else container.height
    return Offset(
        ((width * scale - container.width) / 2f).coerceAtLeast(0f),
        ((height * scale - container.height) / 2f).coerceAtLeast(0f),
    )
}

internal fun zoom_fitted_size(container: Size, aspect_ratio: Float): Size {
    if (aspect_ratio <= 0f || container.width <= 0f || container.height <= 0f) return Size.Zero
    val container_ratio = container.width / container.height
    return if (aspect_ratio >= container_ratio) {
        Size(container.width, container.width / aspect_ratio)
    } else {
        Size(container.height * aspect_ratio, container.height)
    }
}

internal data class zoom_step(val scale: Float, val offset: Offset, val leftover: Offset)

internal fun zoom_transform(
    scale: Float,
    offset: Offset,
    content: Size,
    container: Size,
    max_scale: Float,
    centroid: Offset,
    pan: Offset,
    zoom: Float,
): zoom_step {
    val next = (scale * zoom).coerceIn(ZOOM_MIN_SCALE, max_scale)
    val factor = if (scale > 0f) next / scale else 1f
    val anchor = centroid - Offset(container.width / 2f, container.height / 2f)
    val wanted = (offset - anchor) * factor + anchor + pan
    val bounds = zoom_pan_bounds(content, container, next)
    val clamped = Offset(
        wanted.x.coerceIn(-bounds.x, bounds.x),
        wanted.y.coerceIn(-bounds.y, bounds.y),
    )
    return zoom_step(next, clamped, wanted - clamped)
}

@Stable
internal class zoom_state(val max_scale: Float = ZOOM_DEFAULT_MAX_SCALE) {
    var scale by mutableFloatStateOf(ZOOM_MIN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var container by mutableStateOf(Size.Zero)
        internal set
    var content_aspect_ratio by mutableFloatStateOf(0f)

    val is_zoomed: Boolean get() = scale > ZOOM_ACTIVE_THRESHOLD

    private fun content_size(): Size = zoom_fitted_size(container, content_aspect_ratio)

    fun transform(centroid: Offset, pan: Offset, zoom: Float): Offset {
        val step = zoom_transform(scale, offset, content_size(), container, max_scale, centroid, pan, zoom)
        scale = step.scale
        offset = step.offset
        return step.leftover
    }

    suspend fun animate_scale_to(target: Float, anchor: Offset) {
        val goal = target.coerceIn(ZOOM_MIN_SCALE, max_scale)
        animate(initialValue = scale, targetValue = goal, animationSpec = tween(ZOOM_DOUBLE_TAP_MS)) { value, _ ->
            if (scale > 0f) transform(anchor, Offset.Zero, value / scale)
        }
        if (goal <= ZOOM_MIN_SCALE) {
            scale = ZOOM_MIN_SCALE
            offset = Offset.Zero
        }
    }

    suspend fun toggle(anchor: Offset) {
        if (is_zoomed) {
            animate_scale_to(ZOOM_MIN_SCALE, anchor)
        } else {
            animate_scale_to(ZOOM_DOUBLE_TAP_SCALE.coerceAtMost(max_scale), anchor)
        }
    }

    suspend fun reset() {
        animate_scale_to(ZOOM_MIN_SCALE, Offset(container.width / 2f, container.height / 2f))
    }
}

@Composable
internal fun remember_zoom_state(vararg keys: Any?, max_scale: Float = ZOOM_DEFAULT_MAX_SCALE): zoom_state =
    remember(*keys) { zoom_state(max_scale) }

internal fun Modifier.zoom_layer(state: zoom_state): Modifier = graphicsLayer {
    scaleX = state.scale
    scaleY = state.scale
    translationX = state.offset.x
    translationY = state.offset.y
}

internal fun Modifier.zoomable(
    state: zoom_state,
    enabled: Boolean = true,
    on_tap: (() -> Unit)? = null,
    on_overscroll: ((Offset) -> Unit)? = null,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tap by rememberUpdatedState(on_tap)
    val overscroll by rememberUpdatedState(on_overscroll)
    this
        .onSizeChanged { state.container = it.toSize() }
        .clipToBounds()
        .pointerInput(state, enabled) {
            if (!enabled) return@pointerInput
            val decay = splineBasedDecay<Offset>(density)
            val touch_slop = viewConfiguration.touchSlop
            val double_tap_timeout = viewConfiguration.doubleTapTimeoutMillis
            val long_press_timeout = viewConfiguration.longPressTimeoutMillis
            var last_tap_time = 0L
            var last_tap_position = Offset.Zero
            var tap_job: Job? = null
            var motion_job: Job? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                motion_job?.cancel()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var travelled = Offset.Zero
                var moved = false
                var multi_touch = false
                var dragging = false
                var up_time = down.uptimeMillis
                var up_position = down.position
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) {
                        val last = event.changes.firstOrNull()
                        if (last != null) {
                            up_time = last.uptimeMillis
                            up_position = last.position
                            if (dragging || multi_touch) last.consume()
                        }
                        break
                    }
                    if (pressed.size >= 2) {
                        multi_touch = true
                        val leftover = state.transform(
                            centroid = event.calculateCentroid(useCurrent = false),
                            pan = event.calculatePan(),
                            zoom = event.calculateZoom(),
                        )
                        overscroll?.invoke(leftover)
                        event.changes.forEach { it.consume() }
                    } else {
                        val change = pressed.first()
                        val delta = change.positionChange()
                        travelled += delta
                        if (!moved && travelled.getDistance() > touch_slop) moved = true
                        if (moved && state.is_zoomed) {
                            dragging = true
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val leftover = state.transform(change.position, delta, 1f)
                            overscroll?.invoke(leftover)
                            change.consume()
                        }
                    }
                }
                if (dragging && !multi_touch && state.is_zoomed) {
                    val velocity: Velocity = tracker.calculateVelocity()
                    motion_job = scope.launch {
                        var previous = Offset.Zero
                        AnimationState(
                            typeConverter = Offset.VectorConverter,
                            initialValue = Offset.Zero,
                            initialVelocity = Offset(velocity.x, velocity.y),
                        ).animateDecay(decay) {
                            val delta = value - previous
                            previous = value
                            val leftover = state.transform(Offset.Zero, delta, 1f)
                            val sink = overscroll
                            if (sink != null) {
                                sink(leftover)
                            } else if (leftover.getDistance() >= delta.getDistance() && delta != Offset.Zero) {
                                cancelAnimation()
                            }
                        }
                    }
                }
                val is_tap = !moved && !multi_touch && up_time - down.uptimeMillis < long_press_timeout
                if (is_tap) {
                    val near_last = (up_position - last_tap_position).getDistance() < touch_slop * ZOOM_DOUBLE_TAP_SLOP_FACTOR
                    if (last_tap_time > 0L && up_time - last_tap_time < double_tap_timeout && near_last) {
                        tap_job?.cancel()
                        last_tap_time = 0L
                        motion_job = scope.launch { state.toggle(up_position) }
                    } else {
                        last_tap_time = up_time
                        last_tap_position = up_position
                        tap_job?.cancel()
                        tap_job = if (tap != null) {
                            scope.launch {
                                delay(double_tap_timeout)
                                tap?.invoke()
                            }
                        } else {
                            null
                        }
                    }
                }
            }
        }
}
