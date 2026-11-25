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

internal const val MAIL_SELECTOR_MAX_LENGTH = 256
internal const val MAIL_SELECTOR_MAX_COMPOUNDS = 4
internal const val MAIL_SELECTOR_MAX_COUNT = 100

private val ATTRIBUTE_OPERATORS = listOf("^=", "$=", "*=", "=")

private fun is_selector_name_char(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_'

private fun skip_selector_spaces(text: String, from: Int): Int {
    var i = from
    while (i < text.length && text[i].isWhitespace()) i++
    return i
}

private fun skip_selector_name(text: String, from: Int): Int {
    var i = from
    while (i < text.length && is_selector_name_char(text[i])) i++
    return i
}

private fun attribute_selector_end(text: String, open: Int): Int {
    var i = skip_selector_spaces(text, open + 1)
    val name_end = skip_selector_name(text, i)
    if (name_end == i) return -1
    i = skip_selector_spaces(text, name_end)
    if (i < text.length && text[i] == ']') return i + 1
    val operator = ATTRIBUTE_OPERATORS.firstOrNull { text.startsWith(it, i) } ?: return -1
    i = skip_selector_spaces(text, i + operator.length)
    if (i >= text.length) return -1
    val quote = text[i]
    if (quote == '"' || quote == '\'') {
        val close = text.indexOf(quote, i + 1)
        if (close < 0) return -1
        val value = text.substring(i + 1, close)
        if (value.any { it == '\\' || it == '[' || it == ']' }) return -1
        i = close + 1
    } else {
        val value_end = skip_selector_name(text, i)
        if (value_end == i) return -1
        i = value_end
    }
    i = skip_selector_spaces(text, i)
    return if (i < text.length && text[i] == ']') i + 1 else -1
}

internal fun is_simple_mail_selector(selector: String): Boolean {
    val text = selector.trim()
    if (text.isEmpty() || text.length > MAIL_SELECTOR_MAX_LENGTH) return false
    var compounds = 1
    var in_compound = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c.isWhitespace() || c == '>' || c == '+' -> {
                if (!in_compound) return false
                var explicit = false
                while (i < text.length && (text[i].isWhitespace() || text[i] == '>' || text[i] == '+')) {
                    if (!text[i].isWhitespace()) {
                        if (explicit) return false
                        explicit = true
                    }
                    i++
                }
                compounds++
                if (compounds > MAIL_SELECTOR_MAX_COMPOUNDS) return false
                in_compound = false
            }
            c == '[' -> {
                val end = attribute_selector_end(text, i)
                if (end < 0) return false
                i = end
                in_compound = true
            }
            c == '.' || c == '#' -> {
                val end = skip_selector_name(text, i + 1)
                if (end == i + 1) return false
                i = end
                in_compound = true
            }
            c == '*' -> {
                if (in_compound) return false
                i++
                in_compound = true
            }
            is_selector_name_char(c) -> {
                if (in_compound) return false
                i = skip_selector_name(text, i)
                in_compound = true
            }
            else -> return false
        }
    }
    return in_compound
}

internal fun mail_selectors(groups: Sequence<String>): List<String> {
    val selectors = LinkedHashSet<String>()
    for (group in groups) {
        for (part in group.split(',')) {
            val selector = part.trim()
            if (is_simple_mail_selector(selector)) selectors.add(selector)
            if (selectors.size >= MAIL_SELECTOR_MAX_COUNT) return selectors.toList()
        }
    }
    return selectors.toList()
}

internal fun strip_css_block_comments(css: String): String {
    if (!css.contains("/*")) return css
    val out = StringBuilder(css.length)
    var i = 0
    while (i < css.length) {
        val start = css.indexOf("/*", i)
        if (start < 0) break
        val end = css.indexOf("*/", start + 2)
        if (end < 0) break
        out.append(css, i, start)
        i = end + 2
    }
    out.append(css, i, css.length)
    return out.toString()
}
