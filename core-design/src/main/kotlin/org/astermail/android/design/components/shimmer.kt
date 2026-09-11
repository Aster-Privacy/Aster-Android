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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_reduce_motion

private const val shimmer_period_ms = 1200L
private const val shimmer_band_fraction = 0.6f

private fun mix(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)

private val shared_shimmer_phase = mutableFloatStateOf(0f)

@Composable
private fun shimmer_phase(animated: Boolean): State<Float> {
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameMillis { frame_ms ->
                val next = (frame_ms % shimmer_period_ms) / shimmer_period_ms.toFloat()
                if (shared_shimmer_phase.floatValue != next) {
                    shared_shimmer_phase.floatValue = next
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

@Composable
fun shimmer_state(animated: Boolean = true): shimmer_appearance {
    val colors = AsterMaterial.colors
    val is_animated = animated && !aster_reduce_motion()
    val phase = shimmer_phase(is_animated)
    return remember(colors, is_animated, phase) {
        val surface = colors.bg_card
        val lift = if (colors.is_dark) Color.White else Color.Black
        shimmer_appearance(
            base = mix(
                mix(surface, lift, if (colors.is_dark) 0.07f else 0.09f),
                colors.accent_blue,
                0.05f,
            ),
            highlight = mix(
                mix(surface, lift, if (colors.is_dark) 0.16f else 0.03f),
                colors.accent_blue,
                0.13f,
            ),
            phase = phase,
            animated = is_animated,
        )
    }
}

fun Modifier.shimmer(
    state: shimmer_appearance,
    shape: Shape = RectangleShape,
    phase_shift: Float = 0f,
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val band = size.width * shimmer_band_fraction
        val band_brush = if (band > 0f) {
            Brush.linearGradient(
                colors = listOf(state.base, state.highlight, state.base),
                start = Offset.Zero,
                end = Offset(band, 0f),
            )
        } else {
            null
        }
        val travel = size.width + band
        onDrawBehind {
            drawRect(state.base)
            if (band_brush != null && state.animated) {
                val local_phase = ((state.phase.value - phase_shift) % 1f + 1f) % 1f
                translate(left = local_phase * travel - band) {
                    drawRect(
                        brush = band_brush,
                        topLeft = Offset.Zero,
                        size = Size(band, size.height),
                    )
                }
            }
        }
    }

@Composable
fun Modifier.shimmer(
    shape: Shape = RectangleShape,
    animated: Boolean = true,
): Modifier = this.shimmer(shimmer_state(animated), shape)
