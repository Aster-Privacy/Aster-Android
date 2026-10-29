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

package org.astermail.android.ui.mail

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

internal const val DETAIL_SWIPE_COMMIT_FRACTION = 0.3f
internal const val DETAIL_SWIPE_FLING_MIN_FRACTION = 0.05f
internal const val DETAIL_SWIPE_EDGE_STRETCH_FRACTION = 0.1f
internal const val DETAIL_SWIPE_HORIZONTAL_DOMINANCE = 1.5f
private const val DETAIL_SWIPE_ENTER_MS = 260
private const val DETAIL_SWIPE_STUCK_RECOVERY_MS = 600L
private val detail_swipe_fling_velocity = 500.dp

internal enum class DetailSwipeClaim { pending, claim, release }

internal enum class DetailSwipeOutcome { next, previous, settle }

internal fun detail_swipe_claim(dx_px: Float, dy_px: Float, slop_px: Float): DetailSwipeClaim = when {
    abs(dy_px) > slop_px && abs(dy_px) >= abs(dx_px) -> DetailSwipeClaim.release
    abs(dx_px) > slop_px && abs(dx_px) > abs(dy_px) * DETAIL_SWIPE_HORIZONTAL_DOMINANCE -> DetailSwipeClaim.claim
    else -> DetailSwipeClaim.pending
}

internal fun detail_swipe_points_to_next(offset_px: Float, rtl: Boolean): Boolean =
    if (rtl) offset_px > 0f else offset_px < 0f

internal fun detail_swipe_visual_px(raw_px: Float, can_go: Boolean, width_px: Float): Float {
    if (can_go || width_px <= 0f) return raw_px
    val limit = width_px * DETAIL_SWIPE_EDGE_STRETCH_FRACTION
    return sign(raw_px) * limit * (1f - exp(-abs(raw_px) * 0.35f / limit))
}

internal fun detail_swipe_outcome(
    offset_px: Float,
    velocity_px_s: Float,
    width_px: Float,
    fling_px_s: Float,
    has_next: Boolean,
    has_previous: Boolean,
    rtl: Boolean,
): DetailSwipeOutcome {
    if (width_px <= 0f || offset_px == 0f) return DetailSwipeOutcome.settle
    val toward_next = detail_swipe_points_to_next(offset_px, rtl)
    if (toward_next && !has_next) return DetailSwipeOutcome.settle
    if (!toward_next && !has_previous) return DetailSwipeOutcome.settle
    val fraction = abs(offset_px) / width_px
    val same_way = sign(velocity_px_s) == sign(offset_px)
    val flung = abs(velocity_px_s) >= fling_px_s
    if (flung && !same_way) return DetailSwipeOutcome.settle
    val commits = fraction >= DETAIL_SWIPE_COMMIT_FRACTION ||
        (flung && fraction >= DETAIL_SWIPE_FLING_MIN_FRACTION)
    if (!commits) return DetailSwipeOutcome.settle
    return if (toward_next) DetailSwipeOutcome.next else DetailSwipeOutcome.previous
}

internal fun detail_swipe_enter_side(to_next: Boolean, rtl: Boolean): Int =
    if (to_next != rtl) 1 else -1

internal fun detail_swipe_starts_in_system_zone(x_px: Float, width_px: Float, left_px: Float, right_px: Float): Boolean =
    x_px < left_px || x_px > width_px - right_px

@Stable
internal class DetailSwipeState(initial_offset: Float) {
    var offset by mutableFloatStateOf(initial_offset)
    var committing by mutableStateOf(false)
    var entering by mutableStateOf(initial_offset != 0f)
    internal var motion: Job? = null
}

@Composable
internal fun remember_detail_swipe_state(key: String, direction: Int): DetailSwipeState {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val enter_side = if (direction == 0) 0 else detail_swipe_enter_side(direction > 0, rtl)
    var arrived by rememberSaveable(key) { mutableStateOf(enter_side == 0) }
    val state = remember(key) { DetailSwipeState(if (arrived) 0f else enter_side.toFloat()) }
    LaunchedEffect(state) {
        if (arrived) return@LaunchedEffect
        arrived = true
        try {
            animate(
                initialValue = state.offset,
                targetValue = 0f,
                animationSpec = tween(DETAIL_SWIPE_ENTER_MS, easing = LinearOutSlowInEasing),
            ) { value, _ -> state.offset = value }
        } finally {
            state.offset = 0f
            state.entering = false
        }
    }
    return state
}

internal fun Modifier.detail_swipe_offset(state: DetailSwipeState): Modifier =
    graphicsLayer { translationX = state.offset * size.width }

internal fun Modifier.detail_swipe_navigation(
    state: DetailSwipeState,
    on_next: (() -> Unit)?,
    on_previous: (() -> Unit)?,
): Modifier = composed {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val layout_direction = LocalLayoutDirection.current
    val gesture_insets = WindowInsets.systemGestures
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val live_next by rememberUpdatedState(on_next)
    val live_previous by rememberUpdatedState(on_previous)
    val live_insets by rememberUpdatedState(gesture_insets)

    fun run_motion(target: Float, velocity: Float, spec: AnimationSpec<Float>, then: (suspend () -> Unit)? = null) {
        state.motion?.cancel()
        state.motion = scope.launch {
            animate(state.offset, target, velocity, spec) { value, _ -> state.offset = value }
            then?.invoke()
        }
    }

    fun settle(velocity: Float) {
        run_motion(0f, velocity, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
    }

    pointerInput(rtl, slop) {
        val fling_px_s = detail_swipe_fling_velocity.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (state.committing || state.entering) return@awaitEachGesture
            val width = size.width.toFloat()
            if (width <= 0f) return@awaitEachGesture
            val left_zone = live_insets.getLeft(density, layout_direction).toFloat()
            val right_zone = live_insets.getRight(density, layout_direction).toFloat()
            if (detail_swipe_starts_in_system_zone(down.position.x, width, left_zone, right_zone)) {
                return@awaitEachGesture
            }
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                tracker.addPointerInputChange(change)
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                when (detail_swipe_claim(dx, dy, slop)) {
                    DetailSwipeClaim.release -> return@awaitEachGesture
                    DetailSwipeClaim.claim -> {
                        change.consume()
                        break
                    }
                    DetailSwipeClaim.pending -> Unit
                }
            }
            state.motion?.cancel()
            val base_px = state.offset * width
            var raw = 0f
            var armed = false
            fun can_go(offset_px: Float): Boolean =
                if (detail_swipe_points_to_next(offset_px, rtl)) live_next != null else live_previous != null
            fun visual(): Float {
                val total = base_px + raw
                return detail_swipe_visual_px(total, can_go(total), width)
            }
            var released = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) break
                if (!change.pressed) {
                    released = true
                    tracker.addPointerInputChange(change)
                    change.consume()
                    break
                }
                tracker.addPointerInputChange(change)
                raw += change.positionChange().x
                event.changes.forEach { it.consume() }
                val now = visual()
                state.offset = now / width
                val would_commit = can_go(now) && abs(now) / width >= DETAIL_SWIPE_COMMIT_FRACTION
                if (would_commit && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                armed = would_commit
            }
            val velocity = if (released) tracker.calculateVelocity().x else 0f
            val offset_px = state.offset * width
            val outcome = if (released) {
                detail_swipe_outcome(
                    offset_px = offset_px,
                    velocity_px_s = velocity,
                    width_px = width,
                    fling_px_s = fling_px_s,
                    has_next = live_next != null,
                    has_previous = live_previous != null,
                    rtl = rtl,
                )
            } else {
                DetailSwipeOutcome.settle
            }
            val action = when (outcome) {
                DetailSwipeOutcome.next -> live_next
                DetailSwipeOutcome.previous -> live_previous
                DetailSwipeOutcome.settle -> null
            }
            if (action == null) {
                settle(velocity / width)
                return@awaitEachGesture
            }
            state.committing = true
            run_motion(
                target = sign(offset_px),
                velocity = velocity / width,
                spec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.02f),
            ) {
                action()
                delay(DETAIL_SWIPE_STUCK_RECOVERY_MS)
                state.committing = false
                settle(0f)
            }
        }
    }
}
