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

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class hsv_color(val hue: Float, val saturation: Float, val value: Float)

private val short_hex_pattern = Regex("^[0-9a-fA-F]{3}$")
private val long_hex_pattern = Regex("^[0-9a-fA-F]{6}$")

fun normalize_hex_color(input: String?): String? {
    if (input == null) return null
    val cleaned = input.trim().removePrefix("#")
    return when {
        long_hex_pattern.matches(cleaned) -> "#" + cleaned.lowercase()
        short_hex_pattern.matches(cleaned) ->
            "#" + cleaned.lowercase().map { "$it$it" }.joinToString("")
        else -> null
    }
}

fun hex_to_rgb(hex: String): Int? =
    normalize_hex_color(hex)?.substring(1)?.toInt(16)

fun rgb_to_hex(rgb: Int): String =
    "#" + (rgb and 0xFFFFFF).toString(16).padStart(6, '0')

fun hsv_to_rgb(color: hsv_color): Int {
    val hue = ((color.hue % 360f) + 360f) % 360f
    val saturation = color.saturation.coerceIn(0f, 1f)
    val value = color.value.coerceIn(0f, 1f)
    val chroma = value * saturation
    val x = chroma * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - chroma
    val (r1, g1, b1) = when ((hue / 60f).toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
    return (r shl 16) or (g shl 8) or b
}

fun rgb_to_hsv(rgb: Int): hsv_color {
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val delta = high - low
    val hue = when {
        delta == 0f -> 0f
        high == r -> 60f * (((g - b) / delta) % 6f)
        high == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val normalized_hue = ((hue % 360f) + 360f) % 360f
    val saturation = if (high == 0f) 0f else delta / high
    return hsv_color(normalized_hue, saturation, high)
}

fun hsv_to_hex(color: hsv_color): String = rgb_to_hex(hsv_to_rgb(color))

fun hex_to_hsv(hex: String): hsv_color? = hex_to_rgb(hex)?.let { rgb_to_hsv(it) }

fun wheel_hue_saturation(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
    if (radius <= 0f) return 0f to 0f
    val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    val hue = ((angle % 360f) + 360f) % 360f
    val saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
    return hue to saturation
}

fun wheel_offset(hue: Float, saturation: Float, radius: Float): Pair<Float, Float> {
    val radians = Math.toRadians(hue.toDouble())
    val distance = saturation.coerceIn(0f, 1f) * radius
    return (cos(radians) * distance).toFloat() to (sin(radians) * distance).toFloat()
}

val display_color_presets: List<String> = listOf(
    "#ef4444",
    "#f97316",
    "#eab308",
    "#22c55e",
    "#14b8a6",
    "#3b82f6",
    "#a855f7",
    "#ec4899",
)

data class swatch_row_metrics(val size: Float, val gap: Float)

fun fit_swatch_row(
    available: Float,
    count: Int,
    max_size: Float,
    max_gap: Float,
    min_gap: Float,
): swatch_row_metrics {
    if (count <= 0 || available <= 0f) return swatch_row_metrics(0f, 0f)
    val gaps = (count - 1).coerceAtLeast(0)
    val gap = if (max_size * count + max_gap * gaps <= available) max_gap else min_gap
    val size = ((available - gap * gaps) / count).coerceAtMost(max_size).coerceAtLeast(0f)
    return swatch_row_metrics(size, gap)
}

fun is_custom_color(value: String?, presets: List<String>): Boolean {
    val normalized = normalize_hex_color(value) ?: return false
    return presets.none { normalize_hex_color(it) == normalized }
}

private fun linear_channel(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

fun relative_luminance(rgb: Int): Double =
    0.2126 * linear_channel((rgb shr 16) and 0xFF) +
        0.7152 * linear_channel((rgb shr 8) and 0xFF) +
        0.0722 * linear_channel(rgb and 0xFF)

fun contrast_ratio(first: Int, second: Int): Double {
    val a = relative_luminance(first)
    val b = relative_luminance(second)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

fun prefers_dark_text(rgb: Int): Boolean =
    contrast_ratio(rgb, 0x000000) >= contrast_ratio(rgb, 0xFFFFFF)
