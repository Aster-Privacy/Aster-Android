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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_haptic
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.remember_haptic
import org.astermail.android.ui.theme.local_accessibility
import kotlin.math.cos
import kotlin.math.sin

val aster_pull_refresh_threshold = 56.dp

internal const val PULL_REFRESH_DRAG_RATIO = 0.6f

internal const val PULL_REFRESH_REARM_FRACTION = 0.8f

private const val PULL_REFRESH_MIN_VISIBLE_MS = 600L

private const val PULL_REFRESH_START_WAIT_MS = 800L

private val indicator_disc_size = 40.dp

private val indicator_shadow_room = 12.dp

private val indicator_ring_size = 24.dp

private val indicator_stroke = 3.dp

private val indicator_elevation = 6.dp

private val indicator_rest_drop = 6.dp

private val indicator_overpull_travel = 32.dp

private const val ARC_MIN_DEG = 18f

private const val ARC_RANGE_DEG = 252f

private const val ARC_CAP_DEG = 8f

private const val SPIN_CYCLE_MS = 1332L

private const val SPIN_TURN_MS = 1900f

private const val COLOR_BLEND_START = 0.7f

private val accent_hue_steps = floatArrayOf(0f, 42f, -36f)

private fun ease(value: Float): Float = FastOutSlowInEasing.transform(value.coerceIn(0f, 1f))

private fun hue_shift(color: Color, degrees: Float): Color {
    if (degrees == 0f) return color
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.copy(alpha = 1f).toArgb(), hsl)
    hsl[0] = ((hsl[0] + degrees) % 360f + 360f) % 360f
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = color.alpha)
}

@Stable
class aster_pull_refresh_state internal constructor(
    private val threshold_px: () -> Float,
    private val can_pull: () -> Boolean,
    private val on_refresh: () -> Unit,
    private val on_threshold: () -> Unit,
) : NestedScrollConnection {
    var distance by mutableFloatStateOf(0f)
        private set
    var holding by mutableStateOf(false)
        private set
    var threshold_crossings by mutableIntStateOf(0)
        private set
    private var threshold_armed = true

    val distance_fraction: Float
        get() = threshold_px().let { if (it > 0f) distance / it else 0f }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || distance <= 0f) return Offset.Zero
        return Offset(0f, drag(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f || holding || !can_pull()) return Offset.Zero
        return Offset(0f, drag(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        threshold_armed = true
        if (distance <= 0f) return Velocity.Zero
        val armed = distance >= threshold_px() && !holding && can_pull()
        distance = 0f
        if (armed) {
            holding = true
            on_refresh()
        }
        return if (available.y > 0f) available else Velocity.Zero
    }

    fun release() {
        holding = false
    }

    private fun drag(dy: Float): Float {
        val limit = threshold_px()
        val before = distance
        val next = (before + dy * PULL_REFRESH_DRAG_RATIO).coerceIn(0f, limit * 2f)
        distance = next
        if (limit > 0f) {
            if (threshold_armed && before < limit && next >= limit) {
                threshold_armed = false
                threshold_crossings += 1
                on_threshold()
            } else if (!threshold_armed && next < limit * PULL_REFRESH_REARM_FRACTION) {
                threshold_armed = true
            }
        }
        return (next - before) / PULL_REFRESH_DRAG_RATIO
    }
}

@Composable
fun remember_aster_pull_refresh_state(
    refreshing: Boolean,
    enabled: Boolean,
    on_refresh: () -> Unit,
    threshold: Dp = aster_pull_refresh_threshold,
): aster_pull_refresh_state {
    val threshold_px = with(LocalDensity.current) { threshold.toPx() }
    val threshold_now = rememberUpdatedState(threshold_px)
    val enabled_now = rememberUpdatedState(enabled)
    val refreshing_now = rememberUpdatedState(refreshing)
    val refresh_now = rememberUpdatedState(on_refresh)
    val haptic_now = rememberUpdatedState(local_accessibility.current.haptic_enabled)
    val tactile_now = rememberUpdatedState(remember_haptic())
    val state = remember {
        aster_pull_refresh_state(
            threshold_px = { threshold_now.value },
            can_pull = { enabled_now.value && !refreshing_now.value },
            on_refresh = { refresh_now.value() },
            on_threshold = { if (haptic_now.value) tactile_now.value(aster_haptic.gesture_threshold) },
        )
    }
    LaunchedEffect(state.holding) {
        if (!state.holding) return@LaunchedEffect
        withTimeoutOrNull(PULL_REFRESH_START_WAIT_MS) {
            snapshotFlow { refreshing_now.value }.first { it }
        }
        snapshotFlow { refreshing_now.value }.first { !it }
        state.release()
    }
    return state
}

@Composable
fun aster_pull_refresh_indicator(
    state: aster_pull_refresh_state,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val density = LocalDensity.current
    val reduce_motion = aster_reduce_motion()
    val travel_px = with(density) { (indicator_disc_size + indicator_shadow_room + indicator_rest_drop).toPx() }
    val overpull_px = with(density) { indicator_overpull_travel.toPx() }

    val raw = (refreshing || state.holding) && enabled
    var visible by remember { mutableStateOf(false) }
    val shown_at = remember { longArrayOf(0L) }
    LaunchedEffect(raw) {
        if (raw) {
            if (!visible) shown_at[0] = android.os.SystemClock.uptimeMillis()
            visible = true
        } else if (visible) {
            val shown_for = android.os.SystemClock.uptimeMillis() - shown_at[0]
            delay((PULL_REFRESH_MIN_VISIBLE_MS - shown_for).coerceAtLeast(0L))
            visible = false
        }
    }

    val travel = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    val pop = remember { Animatable(0f) }
    val arrow = remember { Animatable(1f) }
    var spin_mode by remember { mutableStateOf(false) }
    val spin_ms = remember { mutableLongStateOf(0L) }
    val handoff_deg = remember { floatArrayOf(-90f) }

    LaunchedEffect(state, reduce_motion) {
        snapshotFlow { state.distance_fraction.coerceIn(0f, 2f) to (visible || state.holding) }
            .collectLatest { (fraction, on) ->
                when {
                    fraction > 0f -> {
                        if (!on) spin_mode = false
                        exit.snapTo(1f)
                        travel.snapTo(fraction)
                    }
                    on -> {
                        spin_mode = true
                        exit.snapTo(1f)
                        if (reduce_motion) {
                            travel.snapTo(1f)
                        } else {
                            travel.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 340f))
                        }
                    }
                    travel.value > 0f && spin_mode -> {
                        if (reduce_motion) {
                            travel.snapTo(0f)
                        } else {
                            coroutineScope {
                                launch { exit.animateTo(0f, spring(dampingRatio = 1f, stiffness = 170f)) }
                                travel.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 150f))
                            }
                        }
                        spin_mode = false
                        exit.snapTo(1f)
                    }
                    travel.value > 0f -> {
                        if (reduce_motion) {
                            travel.snapTo(0f)
                        } else {
                            travel.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
                        }
                    }
                    else -> {
                        spin_mode = false
                        exit.snapTo(1f)
                    }
                }
            }
    }

    LaunchedEffect(state.threshold_crossings) {
        if (state.threshold_crossings == 0 || reduce_motion) return@LaunchedEffect
        pop.animateTo(1f, tween(durationMillis = 90, easing = FastOutSlowInEasing))
        pop.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 520f))
    }

    val spinning = spin_mode || state.holding
    LaunchedEffect(spinning, reduce_motion) {
        spin_ms.longValue = 0L
        if (!spinning) {
            arrow.snapTo(1f)
            return@LaunchedEffect
        }
        if (reduce_motion) {
            arrow.snapTo(0f)
            return@LaunchedEffect
        }
        launch { arrow.animateTo(0f, tween(durationMillis = 200, easing = FastOutSlowInEasing)) }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> spin_ms.longValue = (now - start) / 1_000_000L }
        }
    }

    val accent = colors.accent_blue.copy(alpha = 1f)
    val palette = remember(accent) { accent_hue_steps.map { hue_shift(accent, it) } }
    val disc_color = if (colors.is_dark) colors.bg_tertiary.copy(alpha = 1f) else colors.bg_card.copy(alpha = 1f)
    val spinning_now = rememberUpdatedState(spinning)

    val on_screen by remember { derivedStateOf { travel.value > 0.001f } }
    if (!on_screen && !spinning) return

    Box(
        modifier = modifier
            .size(indicator_disc_size + indicator_shadow_room * 2)
            .graphicsLayer {
                val t = travel.value
                val reveal = t.coerceIn(0f, 1f)
                val over = (t - 1f).coerceAtLeast(0f)
                val out = exit.value
                translationY = -travel_px * (1f - reveal) + overpull_px * (over / (1f + over))
                alpha = (reveal * 2.4f).coerceAtMost(1f) * ease(out * 1.2f)
                val scale = (0.72f + 0.28f * ease(reveal)) * (0.18f + 0.82f * out) + 0.14f * pop.value
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(indicator_disc_size)
                .shadow(indicator_elevation, CircleShape, clip = false)
                .background(disc_color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(indicator_ring_size)) {
                val base_stroke = indicator_stroke.toPx()
                val radius = (size.minDimension - base_stroke) / 2f
                val arc_origin = Offset(center.x - radius, center.y - radius)
                val arc_size = Size(radius * 2f, radius * 2f)
                val t = travel.value
                val progress = t.coerceIn(0f, 1f)
                val over = (t - 1f).coerceAtLeast(0f)
                val start: Float
                var sweep: Float
                val color: Color
                val arrow_scale: Float
                if (spinning_now.value) {
                    val elapsed = spin_ms.longValue
                    val phased = elapsed + SPIN_CYCLE_MS / 2
                    val cycle = (phased / SPIN_CYCLE_MS).toInt()
                    val phase = (phased % SPIN_CYCLE_MS).toFloat() / SPIN_CYCLE_MS
                    val head = ARC_RANGE_DEG * ease(phase * 2f)
                    val tail = ARC_RANGE_DEG * ease(phase * 2f - 1f)
                    start = handoff_deg[0] + elapsed * 360f / SPIN_TURN_MS + cycle * ARC_RANGE_DEG + tail
                    sweep = head - tail + ARC_MIN_DEG
                    val from = palette[cycle % palette.size]
                    val to = palette[(cycle + 1) % palette.size]
                    val blend = if (elapsed == 0L) 0f else ease((phase - COLOR_BLEND_START) / (1f - COLOR_BLEND_START))
                    color = lerp(from, to, blend)
                    arrow_scale = arrow.value
                } else {
                    start = -90f + 150f * ease(progress) + 200f * (over / (1f + over))
                    sweep = ARC_MIN_DEG + ARC_RANGE_DEG * ease(progress)
                    handoff_deg[0] = start
                    color = accent.copy(alpha = if (progress >= 1f) 1f else 0.3f + 0.55f * progress)
                    arrow_scale = progress
                }
                sweep = sweep.coerceIn(ARC_MIN_DEG, 360f - ARC_CAP_DEG * 2f)
                val stroke = base_stroke * (0.82f + 0.18f * (sweep / (ARC_MIN_DEG + ARC_RANGE_DEG)))
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arc_origin,
                    size = arc_size,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (arrow_scale > 0.01f) {
                    val end_rad = Math.toRadians((start + sweep).toDouble())
                    val radial = Offset(cos(end_rad).toFloat(), sin(end_rad).toFloat())
                    val tangent = Offset(-radial.y, radial.x)
                    val tip = center + radial * radius
                    val half_width = stroke * 1.6f * arrow_scale
                    val length = stroke * 2.1f * arrow_scale
                    val path = Path().apply {
                        moveTo(tip.x + radial.x * half_width, tip.y + radial.y * half_width)
                        lineTo(tip.x + tangent.x * length, tip.y + tangent.y * length)
                        lineTo(tip.x - radial.x * half_width, tip.y - radial.y * half_width)
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }
        }
    }
}
