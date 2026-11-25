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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

val local_high_contrast = staticCompositionLocalOf { false }

data class storage_palette(
    val slots: List<Color>,
    val track: Color,
)

const val storage_palette_size = 6
const val storage_min_segment_contrast = 3.0
const val storage_min_segment_contrast_high = 4.5
const val storage_min_normal_delta = 0.1
const val storage_min_cvd_delta = 0.045

enum class storage_cvd_kind { protan, deutan, tritan }

private data class storage_slot_spec(
    val hue_offset: Double,
    val dark_lightness: Double,
    val light_lightness: Double,
)

private val storage_slot_specs = listOf(
    storage_slot_spec(35.0, 0.84, 0.44),
    storage_slot_spec(-40.0, 0.7, 0.66),
    storage_slot_spec(180.0, 0.86, 0.62),
    storage_slot_spec(110.0, 0.72, 0.5),
    storage_slot_spec(-110.0, 0.8, 0.56),
)

private val storage_hue_steps = listOf(0.0, -10.0, 10.0, -20.0, 20.0, -30.0, 30.0)
private const val storage_lightness_step = 0.03
private const val storage_lightness_step_count = 6
private const val storage_neutral_hue = 259.0

private val storage_cvd_matrices = mapOf(
    storage_cvd_kind.protan to arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998),
    ),
    storage_cvd_kind.deutan to arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047413),
        doubleArrayOf(-0.01182, 0.04294, 0.968881),
    ),
    storage_cvd_kind.tritan to arrayOf(
        doubleArrayOf(1.255528, -0.076749, -0.178779),
        doubleArrayOf(-0.078411, 0.930809, 0.147602),
        doubleArrayOf(0.004733, 0.691367, 0.3039),
    ),
)

private val storage_track_light = Color(0x1464748B)
private val storage_track_dark = Color(0x2994A3B8)
private val storage_track_light_high_contrast = Color(0x38475569)
private val storage_track_dark_high_contrast = Color(0x38CBD5E1)

private fun clamp_unit(value: Double): Double = min(1.0, max(0.0, value))

private fun to_linear(channel: Double): Double =
    if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

private fun from_linear(channel: Double): Double =
    if (channel <= 0.0031308) 12.92 * channel else 1.055 * channel.pow(1.0 / 2.4) - 0.055

private fun parse_hex(hex: String): DoubleArray? {
    val value = hex.trim().removePrefix("#")
    if (value.length != 6 || !value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return DoubleArray(3) { index -> value.substring(index * 2, index * 2 + 2).toInt(16) / 255.0 }
}

private fun format_hex(rgb: DoubleArray): String =
    "#" + rgb.joinToString("") { channel ->
        (clamp_unit(channel) * 255.0).roundToInt().toString(16).padStart(2, '0')
    }

private fun linear_of(hex: String): DoubleArray =
    (parse_hex(hex) ?: doubleArrayOf(0.0, 0.0, 0.0)).map { to_linear(it) }.toDoubleArray()

private fun linear_to_oklab(rgb: DoubleArray): DoubleArray {
    val (r, g, b) = Triple(rgb[0], rgb[1], rgb[2])
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
    )
}

private fun oklab_to_linear(lab: DoubleArray): DoubleArray {
    val l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]).pow(3)
    val m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]).pow(3)
    val s = (lab[0] - 0.0894841775 * lab[1] - 1.291485548 * lab[2]).pow(3)
    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
    )
}

private fun linear_to_hex(rgb: DoubleArray): String =
    format_hex(rgb.map { from_linear(clamp_unit(it)) }.toDoubleArray())

fun storage_hex_to_oklch(hex: String): DoubleArray {
    val lab = linear_to_oklab(linear_of(hex))
    val hue = (Math.toDegrees(atan2(lab[2], lab[1])) + 360.0) % 360.0
    return doubleArrayOf(lab[0], hypot(lab[1], lab[2]), hue)
}

fun storage_oklch_to_hex(lightness: Double, chroma: Double, hue: Double): String {
    val radians = Math.toRadians(hue)
    for (step in 0 until 60) {
        val current = chroma - (chroma / 60.0) * step
        val rgb = oklab_to_linear(doubleArrayOf(lightness, current * cos(radians), current * sin(radians)))
        if (rgb.all { it >= -1e-4 && it <= 1 + 1e-4 }) return linear_to_hex(rgb)
    }
    return linear_to_hex(oklab_to_linear(doubleArrayOf(lightness, 0.0, 0.0)))
}

private fun relative_luminance(hex: String): Double {
    val rgb = linear_of(hex)
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
}

fun storage_contrast(first: String, second: String): Double {
    val x = relative_luminance(first)
    val y = relative_luminance(second)
    return (max(x, y) + 0.05) / (min(x, y) + 0.05)
}

fun storage_simulate_cvd(hex: String, kind: storage_cvd_kind): String {
    val rgb = linear_of(hex)
    val matrix = storage_cvd_matrices.getValue(kind)
    val simulated = DoubleArray(3) { row ->
        clamp_unit(matrix[row][0] * rgb[0] + matrix[row][1] * rgb[1] + matrix[row][2] * rgb[2])
    }
    return linear_to_hex(simulated)
}

fun storage_color_delta(first: String, second: String): Double {
    val x = linear_to_oklab(linear_of(first))
    val y = linear_to_oklab(linear_of(second))
    return sqrt3(x[0] - y[0], x[1] - y[1], x[2] - y[2])
}

private fun sqrt3(a: Double, b: Double, c: Double): Double = kotlin.math.sqrt(a * a + b * b + c * c)

fun storage_cvd_delta(first: String, second: String): Double =
    storage_cvd_kind.entries.minOf { kind ->
        storage_color_delta(storage_simulate_cvd(first, kind), storage_simulate_cvd(second, kind))
    }

private fun storage_separation(first: String, second: String): Double =
    min(
        storage_color_delta(first, second) / storage_min_normal_delta,
        storage_cvd_delta(first, second) / storage_min_cvd_delta,
    )

private fun readable_accent(accent: String, surface: String, is_dark: Boolean, minimum: Double): String {
    val start = storage_hex_to_oklch(accent)
    var lightness = start[0]
    var color = accent
    var step = 0
    while (step < 100 && storage_contrast(color, surface) < minimum) {
        lightness += if (is_dark) 0.01 else -0.01
        color = storage_oklch_to_hex(lightness, start[1], start[2])
        step++
    }
    return color
}

fun build_storage_palette(
    accent: String,
    surface: String,
    is_dark: Boolean,
    high_contrast: Boolean,
): List<String> {
    val safe_accent = if (parse_hex(accent) != null) accent.lowercase() else "#3b82f6"
    val safe_surface = when {
        parse_hex(surface) != null -> surface.lowercase()
        is_dark -> "#121212"
        else -> "#ffffff"
    }
    val minimum = if (high_contrast) storage_min_segment_contrast_high else storage_min_segment_contrast
    val accent_lch = storage_hex_to_oklch(safe_accent)
    val base_hue = if (accent_lch[1] < 0.02) storage_neutral_hue else accent_lch[2]
    val chroma = when {
        accent_lch[1] < 0.06 -> if (is_dark) 0.11 else 0.12
        else -> if (is_dark) 0.15 else 0.17
    }
    val palette = mutableListOf(readable_accent(safe_accent, safe_surface, is_dark, minimum))

    for (spec in storage_slot_specs) {
        val target = if (is_dark) spec.dark_lightness else spec.light_lightness
        var best: String? = null
        var best_score = Double.NEGATIVE_INFINITY
        for (hue_step in storage_hue_steps) {
            for (index in -storage_lightness_step_count..storage_lightness_step_count) {
                val lightness_step = index * storage_lightness_step
                val lightness = target + lightness_step
                if (lightness < 0.3 || lightness > 0.94) continue
                val hue = (base_hue + spec.hue_offset + hue_step + 720.0) % 360.0
                val candidate = storage_oklch_to_hex(lightness, chroma, hue)
                if (storage_contrast(candidate, safe_surface) < minimum) continue
                val closest = palette.minOf { storage_separation(candidate, it) }
                val score = min(1.0, closest) - 0.002 * abs(hue_step) - 0.8 * abs(lightness_step)
                if (score > best_score) {
                    best_score = score
                    best = candidate
                }
            }
        }
        palette += best ?: storage_oklch_to_hex(target, chroma, (base_hue + spec.hue_offset + 720.0) % 360.0)
    }
    return palette
}

private fun color_hex(color: Color): String {
    val opaque = color.copy(alpha = 1f)
    return format_hex(doubleArrayOf(opaque.red.toDouble(), opaque.green.toDouble(), opaque.blue.toDouble()))
}

private fun hex_color(hex: String): Color {
    val rgb = parse_hex(hex) ?: doubleArrayOf(0.0, 0.0, 0.0)
    return Color(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat())
}

fun storage_palette_for(colors: AsterSemanticColors, high_contrast: Boolean): storage_palette {
    val slots = build_storage_palette(
        accent = color_hex(colors.accent_blue),
        surface = color_hex(colors.bg_primary),
        is_dark = colors.is_dark,
        high_contrast = high_contrast,
    ).map(::hex_color)
    val track = when {
        colors.is_dark && high_contrast -> storage_track_dark_high_contrast
        colors.is_dark -> storage_track_dark
        high_contrast -> storage_track_light_high_contrast
        else -> storage_track_light
    }
    return storage_palette(slots = slots, track = track)
}

@Composable
fun current_storage_palette(): storage_palette {
    val colors = AsterMaterial.colors
    val high_contrast = local_high_contrast.current
    return remember(colors.accent_blue, colors.bg_primary, colors.is_dark, high_contrast) {
        storage_palette_for(colors, high_contrast)
    }
}
