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
import androidx.compose.ui.graphics.TileMode
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

internal const val shimmer_sweep_ms = 1350L
internal const val shimmer_rest_ms = 450L
internal const val shimmer_period_ms = shimmer_sweep_ms + shimmer_rest_ms
internal const val shimmer_band_fraction = 0.55f
internal const val shimmer_tilt = 0.364f

private const val shimmer_light_base_alpha = 0.10f
private const val shimmer_dark_base_alpha = 0.13f
private const val shimmer_light_peak_alpha = 0.62f
private const val shimmer_dark_peak_alpha = 0.2f

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
    (frame_ms % shimmer_period_ms).coerceAtMost(shimmer_sweep_ms) / shimmer_sweep_ms.toFloat()

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
    internal val phase: State<Float>,
    internal val animated: Boolean,
)

internal fun shimmer_base_color(ink: Color, is_dark: Boolean): Color =
    ink.copy(alpha = ink.alpha * if (is_dark) shimmer_dark_base_alpha else shimmer_light_base_alpha)

internal fun shimmer_highlight_color(ink: Color, surface: Color, is_dark: Boolean): Color =
    if (is_dark) {
        ink.copy(alpha = ink.alpha * shimmer_dark_peak_alpha)
    } else {
        surface.copy(alpha = shimmer_light_peak_alpha)
    }

@Composable
fun shimmer_state(
    animated: Boolean = true,
): shimmer_appearance {
    val colors = AsterMaterial.colors
    val is_animated = animated && !aster_reduce_motion()
    val phase = shimmer_phase(is_animated)
    val ink = colors.text_secondary
    val surface = colors.bg_card
    val is_dark = colors.is_dark
    return remember(ink, surface, is_dark, is_animated, phase) {
        shimmer_appearance(
            base = shimmer_base_color(ink, is_dark),
            highlight = shimmer_highlight_color(ink, surface, is_dark),
            phase = phase,
            animated = is_animated,
        )
    }
}

internal fun shimmer_sweep_span(width: Float, height: Float): Float = width + height * shimmer_tilt

internal fun shimmer_band_left(phase: Float, sweep_width: Float, origin_u: Float): Float {
    val band = sweep_width * shimmer_band_fraction
    return phase.coerceIn(0f, 1f) * (sweep_width + band) - band - origin_u
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

private fun shimmer_modifier(state: shimmer_appearance, shape: Shape): Modifier =
    Modifier
        .graphicsLayer(shape = shape, clip = true)
        .then(shimmer_element(state))

fun Modifier.shimmer(
    state: shimmer_appearance,
    shape: Shape = RectangleShape,
): Modifier = this.then(shimmer_modifier(state, shape))

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
    shimmer_modifier(state, shimmer_bar_shape(height_fraction.coerceIn(0f, 1f), corner)),
)

private data class shimmer_element(
    val state: shimmer_appearance,
) : ModifierNodeElement<shimmer_node>() {
    override fun create(): shimmer_node = shimmer_node(state)

    override fun update(node: shimmer_node) {
        node.update(state)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shimmer"
    }
}

internal fun shimmer_band_stops(highlight: Color): Array<Pair<Float, Color>> = arrayOf(
    0f to highlight.copy(alpha = 0f),
    0.2f to highlight.copy(alpha = highlight.alpha * 0.16f),
    0.36f to highlight.copy(alpha = highlight.alpha * 0.62f),
    0.5f to highlight,
    0.64f to highlight.copy(alpha = highlight.alpha * 0.62f),
    0.8f to highlight.copy(alpha = highlight.alpha * 0.16f),
    1f to highlight.copy(alpha = 0f),
)

internal fun shimmer_band_end(band: Float): Offset {
    val norm = 1f + shimmer_tilt * shimmer_tilt
    return Offset(band / norm, band * shimmer_tilt / norm)
}

private class shimmer_node(
    var state: shimmer_appearance,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private var origin_u = 0f
    private var cached_sweep = -1f
    private var band = 0f
    private var band_brush: Brush? = null

    fun update(next_state: shimmer_appearance) {
        if (next_state === state) return
        state = next_state
        cached_sweep = -1f
        invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!isAttached || !coordinates.isAttached) return
        val position = coordinates.positionInWindow()
        val next_origin = position.x + position.y * shimmer_tilt
        if (next_origin != origin_u) {
            origin_u = next_origin
            if (state.animated) invalidateDraw()
        }
    }

    private fun prepare(sweep_width: Float) {
        if (sweep_width == cached_sweep) return
        cached_sweep = sweep_width
        band = sweep_width * shimmer_band_fraction
        band_brush = if (band > 0f) {
            Brush.linearGradient(
                colorStops = *shimmer_band_stops(state.highlight),
                start = Offset.Zero,
                end = shimmer_band_end(band),
                tileMode = TileMode.Clamp,
            )
        } else {
            null
        }
    }

    override fun ContentDrawScope.draw() {
        val current = state
        drawRect(current.base)
        if (current.animated) {
            val configuration = currentValueOf(LocalConfiguration)
            val sweep_width = shimmer_sweep_span(
                configuration.screenWidthDp.dp.toPx(),
                configuration.screenHeightDp.dp.toPx(),
            )
            prepare(sweep_width)
            val brush = band_brush
            val band_left = shimmer_band_left(current.phase.value, sweep_width, origin_u)
            val local_right = shimmer_sweep_span(size.width, size.height)
            if (brush != null && shimmer_band_hits(band_left, band, 0f, local_right)) {
                translate(left = band_left) {
                    drawRect(
                        brush = brush,
                        topLeft = Offset(-band_left, 0f),
                        size = size,
                    )
                }
            }
        }
        drawContent()
    }
}
