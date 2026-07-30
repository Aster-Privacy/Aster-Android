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

package org.astermail.android.ui.mail

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal const val chip_min_contrast = 4.5

private fun linearize(channel: Float): Double {
    val c = channel.coerceIn(0f, 1f).toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

internal fun relative_luminance(color: Color): Double =
    0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

internal fun contrast_ratio(a: Color, b: Color): Double {
    val la = relative_luminance(a)
    val lb = relative_luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private fun mix(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )
}

internal fun chip_background(label: Color, surface: Color, is_dark: Boolean): Color =
    mix(surface, label, if (is_dark) 0.26f else 0.18f)

internal fun chip_content(label: Color, background: Color, is_dark: Boolean): Color {
    val toward = if (is_dark) Color.White else Color.Black
    var candidate = mix(label, toward, if (is_dark) 0.2f else 0.1f)
    var step = 0
    while (contrast_ratio(candidate, background) < chip_min_contrast && step < 24) {
        step += 1
        candidate = mix(label, toward, (if (is_dark) 0.2f else 0.1f) + step * 0.035f)
    }
    return candidate
}
