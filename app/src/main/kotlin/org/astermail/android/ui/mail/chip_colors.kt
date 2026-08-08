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

private data class HslColor(val hue: Float, val saturation: Float, val lightness: Float)

private fun to_hsl(color: Color): HslColor {
    val r = color.red
    val g = color.green
    val b = color.blue
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val lightness = (high + low) / 2f
    if (high == low) return HslColor(0f, 0f, lightness)
    val delta = high - low
    val saturation = if (lightness > 0.5f) delta / (2f - high - low) else delta / (high + low)
    val hue = when (high) {
        r -> (g - b) / delta + if (g < b) 6f else 0f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } / 6f
    return HslColor(hue, saturation, lightness)
}

private fun hue_to_channel(p: Float, q: Float, offset: Float): Float {
    var t = offset
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}

private fun from_hsl(value: HslColor): Color {
    val l = value.lightness.coerceIn(0f, 1f)
    if (value.saturation <= 0f) return Color(l, l, l, 1f)
    val q = if (l < 0.5f) l * (1f + value.saturation) else l + value.saturation - l * value.saturation
    val p = 2f * l - q
    return Color(
        hue_to_channel(p, q, value.hue + 1f / 3f),
        hue_to_channel(p, q, value.hue),
        hue_to_channel(p, q, value.hue - 1f / 3f),
        1f,
    )
}

internal fun chip_background(label: Color, surface: Color, is_dark: Boolean): Color =
    mix(surface, label, if (is_dark) 0.15f else 0.12f)

internal fun chip_border(label: Color, surface: Color, is_dark: Boolean): Color =
    mix(surface, label, if (is_dark) 0.30f else 0.25f)

internal fun chip_content(label: Color, background: Color, is_dark: Boolean): Color {
    val base = to_hsl(label)
    val target = if (is_dark) max(base.lightness, 0.68f) else min(base.lightness, 0.36f)
    var candidate = from_hsl(base.copy(lightness = target))
    var step = 0
    while (contrast_ratio(candidate, background) < chip_min_contrast && step < 40) {
        step += 1
        val walked = if (is_dark) target + step * 0.02f else target - step * 0.02f
        candidate = from_hsl(base.copy(lightness = walked.coerceIn(0f, 1f)))
    }
    return candidate
}
