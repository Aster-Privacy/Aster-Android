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

private val avatar_palette: List<Pair<Color, Color>> = listOf(
    Color(0xFF3B82F6) to Color.White,
    Color(0xFF10B981) to Color.White,
    Color(0xFFF59E0B) to Color(0xFF1F1300),
    Color(0xFFEC4899) to Color.White,
    Color(0xFF8B5CF6) to Color.White,
    Color(0xFF14B8A6) to Color.White,
    Color(0xFFEF4444) to Color.White,
    Color(0xFF6366F1) to Color.White,
    Color(0xFF0EA5E9) to Color.White,
    Color(0xFFF97316) to Color.White,
)

fun avatar_colors_for(seed: String): Pair<Color, Color> {
    if (seed.isEmpty()) return avatar_palette[0]
    val idx = (seed.hashCode() % avatar_palette.size + avatar_palette.size) % avatar_palette.size
    return avatar_palette[idx]
}

private const val avatar_luminance_crossover = 0.55f

private fun to_linear(channel: Float): Float =
    if (channel <= 0.03928f) channel / 12.92f
    else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

fun contrast_text_for(background: Color): Color {
    val luminance = 0.2126f * to_linear(background.red) +
        0.7152f * to_linear(background.green) +
        0.0722f * to_linear(background.blue)
    return if (luminance > avatar_luminance_crossover) Color(0xFF111827) else Color.White
}

fun parse_profile_color(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    val full = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        6 -> raw
        8 -> raw.substring(0, 6)
        else -> return null
    }
    if (!full.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    val value = full.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

fun avatar_colors_for(seed: String, profile_color: String?): Pair<Color, Color> {
    val chosen = parse_profile_color(profile_color) ?: return avatar_colors_for(seed)
    return chosen to contrast_text_for(chosen)
}

fun initial_for(name: String, fallback_email: String): String {
    val source = name.trim().ifEmpty { fallback_email.trim() }
    if (source.isEmpty()) return "?"
    val first_char = source.first()
    return first_char.uppercaseChar().toString()
}

fun avatar_initial_font_size(size: Dp): TextUnit = (size.value * 0.4f).sp

fun avatar_initial_style(font_size: TextUnit): TextStyle = TextStyle(
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
