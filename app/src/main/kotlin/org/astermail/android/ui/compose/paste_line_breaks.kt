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

private const val min_paste_skip_budget = 24

private const val object_replacement_char = '\uFFFC'

internal fun normalize_paste_line_breaks(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n')

private fun is_layout_char(ch: Char): Boolean =
    ch.isWhitespace() || ch == object_replacement_char || ch == '\u200B'

internal fun align_styled_to_plain(styled: String, plain: String): IntArray? {
    val map = IntArray(styled.length + 1)
    var cursor = 0
    var skipped = 0
    var matched = 0
    for (i in styled.indices) {
        val ch = styled[i]
        if (is_layout_char(ch)) {
            map[i] = cursor
            continue
        }
        while (cursor < plain.length && plain[cursor] != ch) {
            if (!is_layout_char(plain[cursor])) skipped++
            cursor++
        }
        if (cursor >= plain.length) return null
        map[i] = cursor
        cursor++
        matched++
    }
    map[styled.length] = cursor
    for (rest in cursor until plain.length) {
        if (!is_layout_char(plain[rest])) skipped++
    }
    if (matched == 0) return null
    val budget = maxOf(min_paste_skip_budget, (matched + 9) / 10)
    return if (skipped > budget) null else map
}

internal fun map_styled_range(
    styled: String,
    map: IntArray,
    start: Int,
    end: Int,
): Pair<Int, Int>? {
    val first = (start until end).firstOrNull { !is_layout_char(styled[it]) } ?: return null
    val last = (end - 1 downTo start).first { !is_layout_char(styled[it]) }
    return map[first] to map[last] + 1
}

private val kept_paste_span_types: List<Class<*>> = listOf(
    android.text.style.StyleSpan::class.java,
    android.text.style.UnderlineSpan::class.java,
    android.text.style.StrikethroughSpan::class.java,
    android.text.style.URLSpan::class.java,
)

internal fun merge_paste_line_breaks(styled: CharSequence, plain: String): CharSequence {
    val text = normalize_paste_line_breaks(plain)
    val styled_text = styled.toString()
    val map = align_styled_to_plain(styled_text, text) ?: return text
    val out = android.text.SpannableStringBuilder(text)
    if (styled !is android.text.Spanned) return out
    for (span in styled.getSpans(0, styled.length, Any::class.java)) {
        if (kept_paste_span_types.none { it.isInstance(span) }) continue
        val range = map_styled_range(
            styled_text,
            map,
            styled.getSpanStart(span),
            styled.getSpanEnd(span),
        ) ?: continue
        out.setSpan(span, range.first, range.second, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return out
}
