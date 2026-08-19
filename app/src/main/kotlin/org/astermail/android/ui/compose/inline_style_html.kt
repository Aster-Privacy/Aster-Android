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

package org.astermail.android.ui.compose

private const val min_serializable_font_size_px = 8

private const val max_serializable_font_size_px = 96

private val serializable_font_color_pattern = Regex("^#[0-9a-f]{6}$")

data class inline_style_state(
    val font_size_px: Int? = null,
    val font_color: String? = null,
    val href: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
)

private data class inline_tag(val open: String, val close: String)

fun serializable_font_size_px(raw_px: Int): Int? =
    if (raw_px in min_serializable_font_size_px..max_serializable_font_size_px) raw_px else null

fun serializable_font_color(argb: Int): String =
    "#%06x".format(argb and 0xFFFFFF)

private fun inline_tags_for(state: inline_style_state): List<inline_tag> {
    val tags = mutableListOf<inline_tag>()
    state.font_size_px
        ?.let { serializable_font_size_px(it) }
        ?.let { tags.add(inline_tag("<span style=\"font-size:${it}px\">", "</span>")) }
    state.font_color
        ?.takeIf { serializable_font_color_pattern.matches(it) }
        ?.let { tags.add(inline_tag("<span style=\"color:$it\">", "</span>")) }
    state.href?.let { tags.add(inline_tag("<a href=\"$it\">", "</a>")) }
    if (state.bold) tags.add(inline_tag("<b>", "</b>"))
    if (state.italic) tags.add(inline_tag("<i>", "</i>"))
    if (state.underline) tags.add(inline_tag("<u>", "</u>"))
    if (state.strike) tags.add(inline_tag("<s>", "</s>"))
    return tags
}

fun render_inline_style_html(
    length: Int,
    style_at: (Int) -> inline_style_state,
    char_at: (Int) -> String,
): String {
    if (length <= 0) return ""
    val out = StringBuilder()
    var open_tags = emptyList<inline_tag>()
    for (index in 0 until length) {
        val next_tags = inline_tags_for(style_at(index))
        var shared = 0
        while (
            shared < open_tags.size &&
            shared < next_tags.size &&
            open_tags[shared] == next_tags[shared]
        ) {
            shared++
        }
        for (position in open_tags.size - 1 downTo shared) out.append(open_tags[position].close)
        for (position in shared until next_tags.size) out.append(next_tags[position].open)
        open_tags = next_tags
        out.append(char_at(index))
    }
    for (position in open_tags.size - 1 downTo 0) out.append(open_tags[position].close)
    return out.toString()
}
