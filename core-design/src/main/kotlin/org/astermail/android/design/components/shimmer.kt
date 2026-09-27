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

package org.astermail.android.design.components

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_reduce_motion

private const val shimmer_period_ms = 1600L
private const val shimmer_band_fraction = 0.6f

private const val shimmer_base_alpha = 0.12f
private const val shimmer_peak_alpha = 0.17f

private val shared_shimmer_phase = mutableFloatStateOf(0f)

private val shimmer_frame_drivers = WeakHashMap<MonotonicFrameClock, Mutex>()

private val shimmer_fallback_driver = Mutex()

private fun shimmer_frame_driver(clock: MonotonicFrameClock?): Mutex =
    if (clock == null) {
        shimmer_fallback_driver
    } else {
        synchronized(shimmer_frame_drivers) { shimmer_frame_drivers.getOrPut(clock) { Mutex() } }
    }

internal fun shimmer_phase_at(frame_ms: Long): Float =
    (frame_ms % shimmer_period_ms) / shimmer_period_ms.toFloat()

@Composable
private fun shimmer_phase(animated: Boolean): State<Float> {
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        shimmer_frame_driver(coroutineContext[MonotonicFrameClock]).withLock {
            while (true) {
                withInfiniteAnimationFrameMillis { frame_ms ->
                    shared_shimmer_phase.floatValue = shimmer_phase_at(frame_ms)
                }
            }
        }
    }
    return shared_shimmer_phase
}

@Immutable
class shimmer_appearance internal constructor(
    internal val base: Color,
    internal val highlight: Color,
    internal val band_edge: Color,
    internal val phase: State<Float>,
    internal val animated: Boolean,
)

@Composable
fun shimmer_state(
    animated: Boolean = true,
): shimmer_appearance {
    val colors = AsterMaterial.colors
    val is_animated = animated && !aster_reduce_motion()
    val phase = shimmer_phase(is_animated)
    val ink = colors.text_muted
    return remember(ink, is_animated, phase) {
        val peak = ink.copy(alpha = ink.alpha * shimmer_peak_alpha)
        shimmer_appearance(
            base = ink.copy(alpha = ink.alpha * shimmer_base_alpha),
            highlight = peak,
            band_edge = peak.copy(alpha = 0f),
            phase = phase,
            animated = is_animated,
        )
    }
}

internal fun shimmer_band_left(phase: Float, phase_shift: Float, sweep_width: Float, origin_x: Float): Float {
    val band = sweep_width * shimmer_band_fraction
    val local_phase = ((phase - phase_shift) % 1f + 1f) % 1f
    return local_phase * (sweep_width + band) - band - origin_x
}

internal fun shimmer_band_hits(band_left: Float, band: Float, left: Float, right: Float): Boolean =
    band > 0f && band_left + band > left && band_left < right

private data class shimmer_bar_shape(val fraction: Float, val corner: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bar_height = size.height * fraction
        val bar_top = (size.height - bar_height) / 2f
        val radius = with(density) { corner.toPx() }.coerceAtMost(bar_height / 2f)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = bar_top,
                right = size.width,
                bottom = bar_top + bar_height,
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
}

private fun shimmer_modifier(state: shimmer_appearance, shape: Shape, phase_shift: Float): Modifier =
    Modifier
        .graphicsLayer(shape = shape, clip = true)
        .then(shimmer_element(state, phase_shift))

fun Modifier.shimmer(
    state: shimmer_appearance,
    shape: Shape = RectangleShape,
    phase_shift: Float = 0f,
): Modifier = this.then(shimmer_modifier(state, shape, phase_shift))

@Composable
fun Modifier.shimmer(
    shape: Shape = RectangleShape,
    animated: Boolean = true,
): Modifier = this.shimmer(shimmer_state(animated), shape)

fun Modifier.shimmer_line(
    state: shimmer_appearance,
    height_fraction: Float = 0.6f,
    corner: Dp = 4.dp,
): Modifier = this.then(
    shimmer_modifier(state, shimmer_bar_shape(height_fraction.coerceIn(0f, 1f), corner), 0f),
)

private data class shimmer_element(
    val state: shimmer_appearance,
    val phase_shift: Float,
) : ModifierNodeElement<shimmer_node>() {
    override fun create(): shimmer_node = shimmer_node(state, phase_shift)

    override fun update(node: shimmer_node) {
        node.update(state, phase_shift)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shimmer"
    }
}

private class shimmer_node(
    var state: shimmer_appearance,
    var phase_shift: Float,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private var origin_x = 0f
    private var cached_sweep = -1f
    private var band = 0f
    private var band_brush: Brush? = null

    fun update(next_state: shimmer_appearance, next_phase_shift: Float) {
        if (next_state === state && next_phase_shift == phase_shift) return
        state = next_state
        phase_shift = next_phase_shift
        cached_sweep = -1f
        invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        origin_x = coordinates.positionInWindow().x
    }

    private fun prepare(sweep_width: Float) {
        if (sweep_width == cached_sweep) return
        cached_sweep = sweep_width
        band = sweep_width * shimmer_band_fraction
        band_brush = if (band > 0f) {
            Brush.linearGradient(
                colors = listOf(state.band_edge, state.highlight, state.band_edge),
                start = Offset.Zero,
                end = Offset(band, 0f),
            )
        } else {
            null
        }
    }

    override fun ContentDrawScope.draw() {
        val current = state
        drawRect(current.base)
        if (current.animated) {
            val sweep_width = currentValueOf(LocalConfiguration).screenWidthDp.dp.toPx()
            prepare(sweep_width)
            val brush = band_brush
            val band_left = shimmer_band_left(current.phase.value, phase_shift, sweep_width, origin_x)
            if (brush != null && shimmer_band_hits(band_left, band, 0f, size.width)) {
                val left = maxOf(0f, band_left)
                val right = minOf(size.width, band_left + band)
                translate(left = band_left) {
                    drawRect(
                        brush = brush,
                        topLeft = Offset(left - band_left, 0f),
                        size = Size(right - left, size.height),
                    )
                }
            }
        }
        drawContent()
    }
}
