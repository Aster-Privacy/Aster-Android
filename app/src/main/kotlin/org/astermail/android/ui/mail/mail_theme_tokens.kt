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
import kotlin.math.absoluteValue

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
    val idx = (seed.hashCode().absoluteValue) % avatar_palette.size
    return avatar_palette[idx]
}

fun initial_for(name: String, fallback_email: String): String {
    val source = name.trim().ifEmpty { fallback_email.trim() }
    if (source.isEmpty()) return "?"
    val first_char = source.first()
    return first_char.uppercaseChar().toString()
}
