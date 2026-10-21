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

private const val PGP_ENCRYPTED_MESSAGE_FOOTER = "-----END PGP MESSAGE-----"

private val html_line_break = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val html_block_close = Regex("</(?:p|div|pre|li|tr|blockquote)\\s*>", RegexOption.IGNORE_CASE)
private val html_tag = Regex("<[^>]*>")
private val armor_header_line = Regex("^[A-Za-z][A-Za-z0-9-]*: ")
private val quoted_pgp_begin_line = Regex("^[ \\t]*>[ \\t>]*-----BEGIN PGP MESSAGE-----", RegexOption.MULTILINE)
private val unquoted_pgp_begin_line = Regex("^[ \\t]*-----BEGIN PGP MESSAGE-----", RegexOption.MULTILINE)
private val html_blockquote_open = Regex("<blockquote\\b", RegexOption.IGNORE_CASE)
private val html_blockquote_close = Regex("</blockquote\\s*>", RegexOption.IGNORE_CASE)

private val html_armor_entities = mapOf(
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&#x27;" to "'",
    "&#43;" to "+",
    "&#x2b;" to "+",
    "&#47;" to "/",
    "&#x2f;" to "/",
    "&#61;" to "=",
    "&#x3d;" to "=",
    "&nbsp;" to " ",
    "&amp;" to "&",
)

private val html_armor_entity = Regex(
    "&(?:lt|gt|quot|nbsp|amp|#39|#x27|#43|#x2b|#47|#x2f|#61|#x3d);",
    RegexOption.IGNORE_CASE,
)

fun is_quoted_inline_pgp(text: String?, html: String): Boolean {
    if (text != null &&
        quoted_pgp_begin_line.containsMatchIn(text) &&
        !unquoted_pgp_begin_line.containsMatchIn(text)
    ) {
        return true
    }
    val begin = html.indexOf(PGP_ENCRYPTED_MESSAGE_HEADER)
    if (begin < 0) return false
    val before = html.substring(0, begin)
    return html_blockquote_open.findAll(before).count() > html_blockquote_close.findAll(before).count()
}

fun inline_pgp_html_armor(html: String): String? {
    if (!html.contains(PGP_ENCRYPTED_MESSAGE_HEADER)) return null
    val plain = html
        .replace(html_line_break, "\n")
        .replace(html_block_close, "\n")
        .replace(html_tag, "")
        .replace(html_armor_entity) { html_armor_entities[it.value.lowercase()] ?: it.value }
        .replace(' ', ' ')
    val start = plain.indexOf(PGP_ENCRYPTED_MESSAGE_HEADER)
    if (start < 0) return null
    val end = plain.indexOf(PGP_ENCRYPTED_MESSAGE_FOOTER, start)
    if (end < 0) return null
    val lines = plain.substring(start, end + PGP_ENCRYPTED_MESSAGE_FOOTER.length)
        .split(Regex("\r?\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 3) return null
    val inner = lines.subList(1, lines.size - 1)
    val header_count = inner.takeWhile { armor_header_line.containsMatchIn(it) }.size
    return (
        listOf(lines.first()) +
            inner.subList(0, header_count) +
            "" +
            inner.subList(header_count, inner.size) +
            lines.last()
        ).joinToString("\n")
}
