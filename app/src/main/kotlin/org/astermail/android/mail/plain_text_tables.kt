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

package org.astermail.android.mail

const val MAX_ALIGNED_TABLE_WIDTH = 120

private const val TABLE_COLUMN_GAP = "  "

private val cell_break_pattern = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

private val cell_block_close_pattern = Regex(
    "</(?:p|div|li|h[1-6]|blockquote|pre)\\s*>",
    RegexOption.IGNORE_CASE,
)

private val cell_tag_pattern = Regex("<[^>]*>")

private val cell_source_whitespace = Regex("[ \t\r\n\u000C]+")

private val cell_nbsp_entity = Regex("&(?:nbsp|#160|#x0*a0);", RegexOption.IGNORE_CASE)

private fun display_width(text: String): Int = text.codePointCount(0, text.length)

private fun find_tag_open(html: String, name: String, from: Int): Int {
    var cursor = from
    val pattern = "<$name"
    while (cursor < html.length) {
        val position = html.indexOf(pattern, cursor, ignoreCase = true)
        if (position < 0) return -1
        val next = html.getOrNull(position + pattern.length)
        if (next == '>' || next == '/' || (next != null && next.isWhitespace())) return position
        cursor = position + pattern.length
    }
    return -1
}

private fun cell_lines(inner: String): List<String> {
    var text = cell_source_whitespace.replace(inner, " ")
    text = cell_break_pattern.replace(text, "\n")
    text = cell_block_close_pattern.replace(text, "\n")
    text = cell_tag_pattern.replace(text, "")
    text = cell_nbsp_entity.replace(text, "\u00A0")
    text = decode_html_entities(text)
    val lines = text.split("\n").map { it.trimStart(' ').replace('\u00A0', ' ').trimEnd() }
    val first = lines.indexOfFirst { it.isNotEmpty() }
    if (first < 0) return emptyList()
    val last = lines.indexOfLast { it.isNotEmpty() }
    return lines.subList(first, last + 1)
}

private fun parse_table_grid(inner: String): List<List<List<String>>>? {
    val rows = mutableListOf<MutableList<List<String>>>()
    var open_cell = -1
    var cursor = 0

    fun close_cell(end: Int) {
        if (open_cell < 0) return
        if (rows.isEmpty()) rows.add(mutableListOf())
        rows.last().add(cell_lines(inner.substring(open_cell, end)))
        open_cell = -1
    }

    while (true) {
        val tag_start = inner.indexOf('<', cursor)
        if (tag_start < 0) break
        val close = inner.indexOf('>', tag_start)
        if (close < 0) break
        val tag_end = close + 1
        val tag = inner.substring(tag_start, tag_end).lowercase()
        val is_closing = tag.startsWith("</")
        val name = tag.trimStart('<', '/').takeWhile { !it.isWhitespace() && it != '>' && it != '/' }
        when {
            name == "caption" -> return null
            name == "tr" && !is_closing -> {
                close_cell(tag_start)
                rows.add(mutableListOf())
            }
            (name == "td" || name == "th") && !is_closing -> {
                close_cell(tag_start)
                if (rows.isEmpty()) rows.add(mutableListOf())
                open_cell = tag_end
            }
            name in setOf("td", "th", "tr", "thead", "tbody", "tfoot") -> close_cell(tag_start)
        }
        cursor = tag_end
    }
    close_cell(inner.length)
    return rows
}

private fun escape_aligned_line(line: String): String {
    val out = StringBuilder(line.length)
    for (ch in line) {
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            ' ' -> out.append("&nbsp;")
            else -> out.append(ch)
        }
    }
    return out.toString()
}

internal fun render_aligned_table(inner: String): String? {
    val grid = parse_table_grid(inner) ?: return null
    val column_count = grid.maxOfOrNull { it.size } ?: 0
    if (column_count < 2) return null
    val widths = (0 until column_count).map { column ->
        grid.mapNotNull { it.getOrNull(column) }
            .flatMap { cell -> cell.map { display_width(it) } }
            .maxOrNull() ?: 0
    }
    val used = widths.filter { it > 0 }
    val total = used.sum() + TABLE_COLUMN_GAP.length * (used.size - 1).coerceAtLeast(0)
    if (total > MAX_ALIGNED_TABLE_WIDTH) return null

    val lines = mutableListOf<String>()
    for (row in grid) {
        val height = row.maxOfOrNull { it.size } ?: 0
        for (line_index in 0 until height) {
            val parts = (0 until column_count).filter { widths[it] > 0 }.map { column ->
                val value = row.getOrNull(column)?.getOrNull(line_index).orEmpty()
                value + " ".repeat((widths[column] - display_width(value)).coerceAtLeast(0))
            }
            lines.add(parts.joinToString(TABLE_COLUMN_GAP).trimEnd())
        }
    }
    if (lines.all { it.isEmpty() }) return null
    return "<div>" + lines.joinToString("<br>") { escape_aligned_line(it) } + "</div>"
}

fun align_simple_tables(html: String): String {
    val out = StringBuilder(html.length)
    var cursor = 0
    while (true) {
        val table_start = find_tag_open(html, "table", cursor)
        if (table_start < 0) break
        val open_close = html.indexOf('>', table_start)
        if (open_close < 0) break
        val open_end = open_close + 1
        val close_start = html.indexOf("</table", open_end, ignoreCase = true)
        if (close_start < 0) break
        val nested = find_tag_open(html, "table", open_end)
        if (nested in 0 until close_start) {
            out.append(html, cursor, open_end)
            cursor = open_end
            continue
        }
        val close_gt = html.indexOf('>', close_start)
        val close_end = if (close_gt < 0) html.length else close_gt + 1
        val aligned = render_aligned_table(html.substring(open_end, close_start))
        if (aligned != null) {
            out.append(html, cursor, table_start)
            out.append(aligned)
        } else {
            out.append(html, cursor, close_end)
        }
        cursor = close_end
    }
    out.append(html, cursor, html.length)
    return out.toString()
}
