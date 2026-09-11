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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal val avatar_palette_hex: List<String> = listOf(
    "#1e88e5",
    "#e53935",
    "#43a047",
    "#fb8c00",
    "#8e24aa",
    "#d81b60",
    "#00acc1",
    "#5e35b1",
    "#f4511e",
    "#00897b",
    "#3949ab",
    "#c0ca33",
    "#6d4c41",
    "#039be5",
    "#7cb342",
    "#ff6f00",
)

private val avatar_text_dark = Color(0xFF111827)

private val avatar_text_light = Color(0xFFFFFFFF)

private val avatar_palette: List<Pair<Color, Color>> = avatar_palette_hex.map { hex ->
    Color(0xFF000000L or hex.removePrefix("#").toLong(16)) to contrast_text_for_hex(hex)
}

fun avatar_key_for(email: String, name: String): String = email.ifEmpty { name.ifEmpty { "?" } }

internal fun avatar_hash(value: String): Int {
    var hash = 0
    for (unit in value) {
        hash = (hash shl 5) - hash + unit.code
    }
    return hash
}

fun avatar_color_index(key: String): Int =
    (kotlin.math.abs(avatar_hash(key).toLong()) % avatar_palette_hex.size).toInt()

fun avatar_colors_for(key: String): Pair<Color, Color> = avatar_palette[avatar_color_index(key)]

private const val avatar_luminance_crossover = 0.55

private fun to_linear(channel: Double): Double =
    if (channel <= 0.03928) channel / 12.92
    else Math.pow((channel + 0.055) / 1.055, 2.4)

private fun is_ascii_hex(value: String): Boolean =
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun relative_luminance_of_hex(hex: String): Double? {
    val normalized = hex.replaceFirst("#", "")
    val full = if (normalized.length == 3) normalized.map { "$it$it" }.joinToString("") else normalized
    if (full.length != 6 || !is_ascii_hex(full)) return null
    val r = full.substring(0, 2).toInt(16) / 255.0
    val g = full.substring(2, 4).toInt(16) / 255.0
    val b = full.substring(4, 6).toInt(16) / 255.0
    return 0.2126 * to_linear(r) + 0.7152 * to_linear(g) + 0.0722 * to_linear(b)
}

fun contrast_text_for_hex(hex: String): Color {
    val luminance = relative_luminance_of_hex(hex) ?: return avatar_text_light
    return if (luminance > avatar_luminance_crossover) avatar_text_dark else avatar_text_light
}

fun parse_profile_color(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    val full = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        6 -> raw
        8 -> raw.substring(0, 6)
        else -> return null
    }
    if (!is_ascii_hex(full)) return null
    return Color(0xFF000000L or full.toLong(16))
}

fun avatar_colors_for(key: String, profile_color: String?): Pair<Color, Color> {
    val chosen = parse_profile_color(profile_color) ?: return avatar_colors_for(key)
    return chosen to contrast_text_for_hex(profile_color.orEmpty())
}

fun initial_for(name: String, fallback_email: String): String {
    val source = name.trim().ifEmpty { fallback_email.trim() }
    if (source.isEmpty()) return "?"
    val first_char = source.first()
    return first_char.uppercaseChar().toString()
}

fun avatar_initial_font_size(size: Dp): TextUnit = (size.value * 0.4f).sp

private val avatar_initial_style_cache = java.util.concurrent.ConcurrentHashMap<TextUnit, TextStyle>()

fun avatar_initial_style(font_size: TextUnit): TextStyle =
    avatar_initial_style_cache.getOrPut(font_size) { build_avatar_initial_style(font_size) }

private fun build_avatar_initial_style(font_size: TextUnit): TextStyle = TextStyle(
    fontSize = font_size,
    fontWeight = FontWeight.SemiBold,
    lineHeight = font_size,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

fun avatar_initial_style(size: Dp): TextStyle = avatar_initial_style(avatar_initial_font_size(size))
