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

package org.astermail.android.api.preferences

const val compose_font_size_default = "normal"

const val compose_font_color_default = ""

private val compose_font_size_px_by_label = linkedMapOf(
    "small" to 12,
    "normal" to 14,
    "large" to 18,
    "huge" to 24,
)

val compose_font_size_labels: List<String> = compose_font_size_px_by_label.keys.toList()

private val compose_font_color_pattern = Regex("^#[0-9a-fA-F]{6}$")

fun normalize_compose_font_size(raw: String?): String {
    val candidate = raw?.trim()?.lowercase().orEmpty()
    return if (compose_font_size_px_by_label.containsKey(candidate)) candidate else compose_font_size_default
}

fun compose_font_size_px(raw: String?): Int =
    compose_font_size_px_by_label.getValue(normalize_compose_font_size(raw))

fun normalize_compose_font_color(raw: String?): String {
    val candidate = raw?.trim().orEmpty()
    if (!compose_font_color_pattern.matches(candidate)) return compose_font_color_default
    return "#" + candidate.substring(1).lowercase()
}

fun compose_font_color_argb(raw: String?): Int? {
    val normalized = normalize_compose_font_color(raw)
    if (normalized.isEmpty()) return null
    val rgb = normalized.substring(1).toLongOrNull(16) ?: return null
    return (0xFF000000L or rgb).toInt()
}

fun effective_compose_font_size(preferences: UserPreferences?): String =
    normalize_compose_font_size(preferences?.compose_font_size)

fun effective_compose_font_color(preferences: UserPreferences?): String =
    normalize_compose_font_color(preferences?.compose_font_color)
