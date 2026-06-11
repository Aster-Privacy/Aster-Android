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

private val hex_pattern = Regex("^[0-9a-fA-F]+$")

fun parse_hex_color_safe(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    if (!hex_pattern.matches(cleaned)) return null
    return when (cleaned.length) {
        3 -> {
            val r = cleaned[0].digitToInt(16) * 17
            val g = cleaned[1].digitToInt(16) * 17
            val b = cleaned[2].digitToInt(16) * 17
            Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
        }
        6 -> {
            val v = cleaned.toLong(16)
            Color(0xFF000000 or v)
        }
        8 -> {
            val v = cleaned.toLong(16)
            Color(v)
        }
        else -> null
    }
}

fun parse_hex_color_or(hex: String?, fallback: Color): Color =
    parse_hex_color_safe(hex) ?: fallback
