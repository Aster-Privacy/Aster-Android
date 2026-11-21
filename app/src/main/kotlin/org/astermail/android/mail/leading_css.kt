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

private const val MAX_CSS_PRELUDE_LENGTH = 400

private const val CSS_TAG_NAMES =
    "html|body|table|tbody|thead|tfoot|tr|td|th|div|span|p|a|img|h[1-6]|ul|ol|li|blockquote|pre|code|" +
        "b|i|u|s|em|strong|font|center|hr|br|section|article|header|footer|nav|main|aside|button|input|" +
        "label|form|sup|sub|small|big|dl|dt|dd|figure|figcaption|caption|col|colgroup|iframe|video|audio|" +
        "svg|strike|q|cite|abbr|address|tt|mark|picture|source"

private const val CSS_SIMPLE_SELECTOR =
    "(?:[.#][-_a-zA-Z0-9]+|\\[[^\\[\\]{}]*\\]|::?[-a-zA-Z]+(?:\\([^(){}]*\\))?)"

private const val CSS_COMPOUND_SELECTOR =
    "(?:(?:(?:$CSS_TAG_NAMES)(?![-_a-zA-Z0-9])|\\*)$CSS_SIMPLE_SELECTOR*|$CSS_SIMPLE_SELECTOR+)"

private const val CSS_COMPLEX_SELECTOR =
    "$CSS_COMPOUND_SELECTOR(?:\\s*[>+~]\\s*$CSS_COMPOUND_SELECTOR|\\s+$CSS_COMPOUND_SELECTOR)*"

private val CSS_SELECTOR_LIST_PATTERN =
    Regex("\\s*$CSS_COMPLEX_SELECTOR(?:\\s*,\\s*$CSS_COMPLEX_SELECTOR)*\\s*")

private const val CSS_KEYFRAME_STOP = "(?:from|to|\\d+(?:\\.\\d+)?%)"

private val CSS_KEYFRAME_SELECTOR_PATTERN =
    Regex("\\s*$CSS_KEYFRAME_STOP(?:\\s*,\\s*$CSS_KEYFRAME_STOP)*\\s*")

private val CSS_DECLARATION_PATTERN = Regex("\\s*\\*?-?[a-zA-Z_][-a-zA-Z0-9_]*\\s*:[\\s\\S]*")

private val CSS_STATEMENT_AT_RULES = setOf("import", "charset", "namespace")

private val CSS_GROUPING_AT_RULES = setOf("media", "supports", "document", "-moz-document", "container", "layer")

private val CSS_DECLARATION_AT_RULES = setOf(
    "font-face",
    "page",
    "viewport",
    "-ms-viewport",
    "counter-style",
    "property",
)

private val CSS_KEYFRAMES_AT_RULES = setOf("keyframes", "-webkit-keyframes", "-moz-keyframes", "-o-keyframes")

fun strip_leading_css(text: String): String {
    val end = leading_css_end(text)
    if (end <= 0) return text
    return text.substring(skip_css_trivia(text, end))
}

fun has_leading_css(text: String): Boolean = leading_css_end(text) > 0

private fun leading_css_end(text: String): Int {
    if (text.indexOf('{') < 0 && text.indexOf(';') < 0) return -1
    return css_rules_end(text, 0, false)
}

private fun css_rules_end(text: String, start: Int, keyframes: Boolean): Int {
    var pos = start
    var committed = -1
    while (true) {
        pos = skip_css_trivia(text, pos)
        if (pos >= text.length) return committed
        val next = parse_css_rule(text, pos, keyframes) ?: return committed
        pos = next
        committed = next
    }
}

private fun is_css_stylesheet(text: String, keyframes: Boolean): Boolean {
    if (text.isBlank()) return true
    val end = css_rules_end(text, 0, keyframes)
    return end > 0 && skip_css_trivia(text, end) >= text.length
}

private fun skip_css_trivia(text: String, start: Int): Int {
    var pos = start
    while (pos < text.length) {
        when {
            text[pos].isWhitespace() -> pos++
            text.startsWith("/*", pos) -> {
                val close = text.indexOf("*/", pos + 2)
                if (close < 0) return pos
                pos = close + 2
            }
            text.startsWith("<!--", pos) -> pos += 4
            text.startsWith("-->", pos) -> pos += 3
            else -> return pos
        }
    }
    return pos
}

private fun parse_css_rule(text: String, pos: Int, keyframes: Boolean): Int? {
    if (text[pos] == '@') return parse_css_at_rule(text, pos)
    val open = text.indexOf('{', pos)
    if (open < 0 || open - pos > MAX_CSS_PRELUDE_LENGTH) return null
    val prelude = text.substring(pos, open)
    val selector_pattern = if (keyframes) CSS_KEYFRAME_SELECTOR_PATTERN else CSS_SELECTOR_LIST_PATTERN
    if (!selector_pattern.matches(prelude)) return null
    val close = find_css_block_end(text, open)
    if (close < 0) return null
    if (!looks_like_css_declarations(text.substring(open + 1, close))) return null
    return close + 1
}

private fun parse_css_at_rule(text: String, pos: Int): Int? {
    var prelude_start = pos + 1
    while (prelude_start < text.length && (text[prelude_start].isLetter() || text[prelude_start] == '-')) {
        prelude_start++
    }
    val name = text.substring(pos + 1, prelude_start).lowercase()
    if (name.isEmpty()) return null
    if (name in CSS_STATEMENT_AT_RULES) {
        val semicolon = text.indexOf(';', prelude_start)
        if (semicolon < 0 || semicolon - pos > MAX_CSS_PRELUDE_LENGTH) return null
        val prelude = text.substring(prelude_start, semicolon)
        if (prelude.contains('{') || prelude.contains('}')) return null
        val trimmed = prelude.trim()
        val is_reference = trimmed.startsWith("url(", ignoreCase = true) ||
            trimmed.startsWith('"') ||
            trimmed.startsWith('\'')
        return if (is_reference) semicolon + 1 else null
    }
    val is_grouping = name in CSS_GROUPING_AT_RULES
    val is_keyframes = name in CSS_KEYFRAMES_AT_RULES
    val is_declaration = name in CSS_DECLARATION_AT_RULES
    if (!is_grouping && !is_keyframes && !is_declaration) return null
    val open = text.indexOf('{', prelude_start)
    if (open < 0 || open - pos > MAX_CSS_PRELUDE_LENGTH) return null
    val prelude = text.substring(prelude_start, open)
    if (prelude.contains('}') || prelude.contains(';')) return null
    val close = find_css_block_end(text, open)
    if (close < 0) return null
    val inner = text.substring(open + 1, close)
    val valid = when {
        is_grouping -> is_css_stylesheet(inner, false)
        is_keyframes -> is_css_stylesheet(inner, true)
        else -> looks_like_css_declarations(inner)
    }
    return if (valid) close + 1 else null
}

private fun find_css_block_end(text: String, open: Int): Int {
    var depth = 0
    var quote: Char? = null
    var index = open
    while (index < text.length) {
        val c = text[index]
        if (quote != null) {
            if (c == '\\') {
                index += 2
                continue
            }
            if (c == quote) quote = null
        } else {
            when (c) {
                '"', '\'' -> quote = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        index++
    }
    return -1
}

private fun looks_like_css_declarations(block: String): Boolean {
    if (block.contains('{') || block.contains('}')) return false
    return split_css_declarations(block).all { it.isBlank() || CSS_DECLARATION_PATTERN.matches(it) }
}

private fun split_css_declarations(block: String): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var quote: Char? = null
    var start = 0
    for (index in block.indices) {
        val c = block[index]
        if (quote != null) {
            if (c == quote) quote = null
            continue
        }
        when (c) {
            '"', '\'' -> quote = c
            '(' -> depth++
            ')' -> if (depth > 0) depth--
            ';' -> if (depth == 0) {
                parts.add(block.substring(start, index))
                start = index + 1
            }
        }
    }
    parts.add(block.substring(start))
    return parts
}
