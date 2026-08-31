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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_reduce_motion

private fun mix(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)

@Composable
fun shimmer_brush(animated: Boolean = true): Brush {
    val colors = AsterMaterial.colors
    val surface = colors.bg_card
    val lift = if (colors.is_dark) Color.White else Color.Black
    val base = mix(mix(surface, lift, if (colors.is_dark) 0.07f else 0.09f), colors.accent_blue, 0.05f)
    val highlight = mix(mix(surface, lift, if (colors.is_dark) 0.16f else 0.03f), colors.accent_blue, 0.13f)

    if (!animated || aster_reduce_motion()) {
        return Brush.linearGradient(colors = listOf(base, base))
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmer_offset by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shimmer_offset, 0f),
        end = Offset(shimmer_offset + 300f, 0f),
    )
}
