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

package org.astermail.android.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

const val contrast_body_text = 4.5
const val contrast_large_text = 3.0

private fun linear_channel(value: Float): Double {
    val v = value.toDouble()
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

fun relative_luminance(color: Color): Double =
    0.2126 * linear_channel(color.red) + 0.7152 * linear_channel(color.green) + 0.0722 * linear_channel(color.blue)

fun contrast_ratio(a: Color, b: Color): Double {
    val la = relative_luminance(a)
    val lb = relative_luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

fun mix_rgb(from: Color, to: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )
}

fun ensure_contrast(foreground: Color, backgrounds: List<Color>, minimum: Double): Color {
    val solid = foreground.copy(alpha = 1f)
    fun passes(candidate: Color): Boolean = backgrounds.all { contrast_ratio(candidate, it) >= minimum }
    if (passes(solid)) return solid
    val darkest_bg = backgrounds.minOf { relative_luminance(it) }
    val target = if (darkest_bg < 0.18) Color.White else Color.Black
    if (!passes(target)) return target
    var low = 0f
    var high = 1f
    repeat(24) {
        val mid = (low + high) / 2f
        if (passes(mix_rgb(solid, target, mid))) high = mid else low = mid
    }
    return mix_rgb(solid, target, high)
}

fun readable_on(fill: Color): Color {
    val white = contrast_ratio(Color.White, fill)
    val black = contrast_ratio(Color.Black, fill)
    return if (white >= black) Color.White else Color.Black
}
