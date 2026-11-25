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

const val PREVIEW_MAX_LENGTH = 140

private val INVISIBLE_CHAR_PATTERN = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF\\u00AD\\u034F\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u3164\\uFFA0\\uFFF9-\\uFFFC]")

private val WHITESPACE_RUN_PATTERN = Regex("\\s+")

private val BREAK_TAG_PATTERN = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

private val BLOCK_CLOSE_PATTERN = Regex("</(?:p|div|li|tr|h[1-6]|blockquote|pre)>", RegexOption.IGNORE_CASE)

private val HR_TAG_PATTERN = Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE)

private val SPACE_RUN_PATTERN = Regex("[ \\t\\u00A0]+")

private val LINE_PADDING_PATTERN = Regex("[ \\t]*\\n[ \\t]*")

private val BLANK_LINES_PATTERN = Regex("\\n{3,}")

private val NUMERIC_ENTITY_PATTERN = Regex("&#(\\d+);")

private val HEX_ENTITY_PATTERN = Regex("&#x([0-9a-fA-F]+);")

private val CIPHERTEXT_MARKERS = listOf(
    ASTER_SUBJECT_BUNDLE_MARKER,
    "ASTER_RATCHET_UNDECRYPTABLE",
    "-----BEGIN PGP",
    "\"double_ratchet_v1\"",
    "\"double_ratchet_v2\"",
    "\"double_ratchet_v3\"",
    "\"ratchet_v1\"",
    "\"ephemeral_key\"",
    "\"prekey_id\"",
)

private fun looks_like_bundle_fragment(text: String): Boolean {
    val trimmed = strip_body_framing(text).trimStart()
    if (!trimmed.startsWith("{")) return false
    return trimmed.contains("\"s\":\"") ||
        trimmed.contains("\"b\":\"") ||
        trimmed.contains("\"double_ratchet")
}

fun looks_like_ciphertext(text: String): Boolean =
    CIPHERTEXT_MARKERS.any { text.contains(it) } || looks_like_bundle_fragment(text)

private fun is_pattern_space(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

private fun skip_pattern_spaces(text: String, from: Int): Int {
    var index = from
    while (index < text.length && is_pattern_space(text[index])) index++
    return index
}

private fun ends_word(text: String, index: Int): Boolean =
    index >= text.length || !(text[index].isLetterOrDigit() || text[index] == '_')

private fun starts_with_at(text: String, index: Int, prefix: String): Boolean =
    text.regionMatches(index, prefix, 0, prefix.length, ignoreCase = true)

private fun replace_tags(text: String, replacement: String): String {
    var lt = text.indexOf('<')
    if (lt < 0) return text
    val out = StringBuilder(text.length)
    var copied = 0
    while (lt >= 0) {
        val gt = text.indexOf('>', lt + 1)
        if (gt < 0) break
        if (gt > lt + 1) {
            out.append(text, copied, lt).append(replacement)
            copied = gt + 1
            lt = text.indexOf('<', copied)
        } else {
            lt = text.indexOf('<', gt)
        }
    }
    out.append(text, copied, text.length)
    return out.toString()
}

fun strip_body_html(html: String): String {
    var text = strip_non_rendered_blocks(strip_html_comments(html))
    text = replace_tags(text, " ")
    text = decode_html_entities_for_preview(text)
    text = strip_non_rendered_blocks(text)
    text = replace_tags(text, " ")
    text = text.replace(INVISIBLE_CHAR_PATTERN, "")
    text = text.replace(WHITESPACE_RUN_PATTERN, " ")
    return text.trim()
}

private val HTML_TAG_NAMES = listOf(
    "html", "body", "div", "p", "br", "span", "a", "blockquote", "table", "tbody", "tr", "td", "th",
    "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6", "img", "b", "i", "u", "em", "strong", "pre",
    "code", "font", "hr",
)

private val HTML_DOCUMENT_TAG_NAMES = listOf("html", "body")

private fun ends_html_tag_name(text: String, index: Int): Boolean =
    index < text.length && (is_pattern_space(text[index]) || text[index] == '/' || text[index] == '>')

private fun has_html_tag(text: String, names: List<String>, allow_closing: Boolean): Boolean {
    val last_gt = text.lastIndexOf('>')
    var lt = text.indexOf('<')
    while (lt in 0 until last_gt) {
        var name_start = skip_pattern_spaces(text, lt + 1)
        if (allow_closing && name_start < text.length && text[name_start] == '/') name_start++
        if (names.any { starts_with_at(text, name_start, it) && ends_html_tag_name(text, name_start + it.length) }) {
            return true
        }
        lt = text.indexOf('<', lt + 1)
    }
    return false
}

private val HTML_CLOSING_PATTERN = Regex(
    "</\\s*(?:html|body|div|p|span|a|blockquote|table|tbody|tr|td|th|ul|ol|li|h[1-6]|b|i|u|em|strong|pre|code|font)\\s*>",
    RegexOption.IGNORE_CASE,
)

private val HTML_BREAK_PATTERN = Regex("<\\s*br\\s*/?>", RegexOption.IGNORE_CASE)

fun looks_like_html_body(text: String): Boolean {
    if (has_html_tag(text, HTML_DOCUMENT_TAG_NAMES, false)) return true
    if (!has_html_tag(text, HTML_TAG_NAMES, true)) return false
    return HTML_CLOSING_PATTERN.containsMatchIn(text) || HTML_BREAK_PATTERN.containsMatchIn(text)
}

fun html_to_plain_text(html: String): String {
    var text = strip_non_rendered_blocks(strip_html_comments(html))
    text = text.replace(BREAK_TAG_PATTERN, "\n")
    text = text.replace(BLOCK_CLOSE_PATTERN, "\n")
    text = text.replace(HR_TAG_PATTERN, "\n")
    text = replace_tags(text, "")
    text = decode_html_entities(text)
    text = text.replace("\r\n", "\n").replace('\r', '\n')
    text = text.replace(SPACE_RUN_PATTERN, " ")
    text = text.replace(LINE_PADDING_PATTERN, "\n")
    text = text.replace(BLANK_LINES_PATTERN, "\n\n")
    return text.trim()
}

private val NAMED_ENTITIES = mapOf(
    "nbsp" to " ",
    "ensp" to " ",
    "emsp" to " ",
    "thinsp" to " ",
    "shy" to "",
    "zwnj" to "",
    "zwj" to "",
    "lrm" to "",
    "rlm" to "",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "mdash" to "-",
    "ndash" to "-",
    "hellip" to "...",
    "lsquo" to "\u2018",
    "rsquo" to "\u2019",
    "sbquo" to "\u201A",
    "ldquo" to "\u201C",
    "rdquo" to "\u201D",
    "bdquo" to "\u201E",
    "lsaquo" to "\u2039",
    "rsaquo" to "\u203A",
    "laquo" to "\u00AB",
    "raquo" to "\u00BB",
    "bull" to "\u2022",
    "middot" to "\u00B7",
    "prime" to "\u2032",
    "Prime" to "\u2033",
    "dagger" to "\u2020",
    "Dagger" to "\u2021",
    "permil" to "\u2030",
    "sect" to "\u00A7",
    "para" to "\u00B6",
    "copy" to "\u00A9",
    "reg" to "\u00AE",
    "trade" to "\u2122",
    "deg" to "\u00B0",
    "plusmn" to "\u00B1",
    "times" to "\u00D7",
    "divide" to "\u00F7",
    "frac12" to "\u00BD",
    "frac14" to "\u00BC",
    "frac34" to "\u00BE",
    "euro" to "\u20AC",
    "pound" to "\u00A3",
    "yen" to "\u00A5",
    "cent" to "\u00A2",
    "curren" to "\u00A4",
    "larr" to "\u2190",
    "uarr" to "\u2191",
    "rarr" to "\u2192",
    "darr" to "\u2193",
    "harr" to "\u2194",
    "infin" to "\u221E",
    "ne" to "\u2260",
    "le" to "\u2264",
    "ge" to "\u2265",
    "micro" to "\u00B5",
    "iexcl" to "\u00A1",
    "iquest" to "\u00BF",
    "ordm" to "\u00BA",
    "ordf" to "\u00AA",
)

private val NAMED_ENTITY_PATTERN = Regex("&([a-zA-Z][a-zA-Z0-9]{1,31});")

private const val MAX_PREVIEW_ENTITY_DECODE_PASSES = 3

fun decode_html_entities_for_preview(input: String): String {
    var text = input
    repeat(MAX_PREVIEW_ENTITY_DECODE_PASSES) {
        if (!text.contains('&')) return text
        val decoded = decode_html_entities(text)
        if (decoded == text) return decoded
        text = decoded
    }
    return text
}

fun decode_html_entities(input: String): String {
    var text = input
    text = text.replace(NAMED_ENTITY_PATTERN) { m ->
        NAMED_ENTITIES[m.groupValues[1]] ?: m.value
    }
    text = text.replace(NUMERIC_ENTITY_PATTERN) { m ->
        m.groupValues[1].toIntOrNull()?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: m.value
    }
    text = text.replace(HEX_ENTITY_PATTERN) { m ->
        m.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: m.value
    }
    text = text.replace("&amp;", "&")
    return text
}

fun take_whole_chars(text: String, max_length: Int): String {
    if (max_length <= 0) return ""
    if (text.length <= max_length) return text
    val end = if (Character.isHighSurrogate(text[max_length - 1])) max_length - 1 else max_length
    return text.substring(0, end)
}

private val QUOTE_BLOCK_TAG_NAMES = listOf("div", "blockquote")

private val QUOTE_BLOCK_CLASSES = listOf("aster_quote", "gmail_quote", "protonmail_quote", "yahoo_quoted", "moz-cite-prefix")

private val QUOTE_ATTR_TAG_NAMES = listOf("div")

private val QUOTE_ATTR_CLASSES = listOf("aster_quote_attr", "gmail_attr")

private fun is_quote_char(c: Char): Boolean = c == '"' || c == '\''

private fun index_of_quote_char(text: String, from: Int): Int {
    for (index in from until text.length) if (is_quote_char(text[index])) return index
    return -1
}

private fun quote_class_value_end(html: String, from: Int, tag_close: Int, last_gt: Int, classes: List<String>): Int {
    var value_end = -1
    for (at in from..tag_close - 5) {
        if (!starts_with_at(html, at, "class")) continue
        var index = skip_pattern_spaces(html, at + 5)
        if (index >= html.length || html[index] != '=') continue
        index = skip_pattern_spaces(html, index + 1)
        if (index >= html.length || !is_quote_char(html[index])) continue
        val close_quote = index_of_quote_char(html, index + 1)
        if (close_quote < 0 || close_quote >= last_gt) continue
        val value = html.substring(index + 1, close_quote)
        if (classes.any { value.contains(it, ignoreCase = true) }) value_end = close_quote
    }
    return value_end
}

private fun find_quote_tag(html: String, names: List<String>, classes: List<String>): IntRange? {
    val last_gt = html.lastIndexOf('>')
    var lt = html.indexOf('<')
    while (lt in 0 until last_gt) {
        val name_start = skip_pattern_spaces(html, lt + 1)
        val name = names.firstOrNull { starts_with_at(html, name_start, it) && ends_word(html, name_start + it.length) }
        if (name == null) {
            lt = html.indexOf('<', lt + 1)
            continue
        }
        val name_end = name_start + name.length
        val tag_close = html.indexOf('>', name_end)
        val value_end = quote_class_value_end(html, name_end, tag_close, last_gt, classes)
        if (value_end >= 0) return lt..html.indexOf('>', value_end + 1)
        lt = html.indexOf('<', tag_close)
    }
    return null
}

private class DivTag(val closing: Boolean, val end: Int)

private fun find_div_tag(html: String, from: Int): DivTag? {
    var lt = html.indexOf('<', from)
    while (lt >= 0) {
        var name_start = skip_pattern_spaces(html, lt + 1)
        val closing = name_start < html.length && html[name_start] == '/'
        if (closing) name_start = skip_pattern_spaces(html, name_start + 1)
        if (starts_with_at(html, name_start, "div") && ends_word(html, name_start + 3)) {
            val gt = html.indexOf('>', name_start + 3)
            return if (gt < 0) null else DivTag(closing, gt)
        }
        lt = html.indexOf('<', lt + 1)
    }
    return null
}

fun strip_quoted_html(html: String): String {
    val match = find_quote_tag(html, QUOTE_BLOCK_TAG_NAMES, QUOTE_BLOCK_CLASSES) ?: return html
    return html.substring(0, match.first)
}

fun has_quote_attribution(html: String): Boolean =
    find_quote_tag(html, QUOTE_ATTR_TAG_NAMES, QUOTE_ATTR_CLASSES) != null

fun strip_quote_attribution(html: String): String {
    val start = find_quote_tag(html, QUOTE_ATTR_TAG_NAMES, QUOTE_ATTR_CLASSES) ?: return html
    var depth = 1
    var index = start.last + 1
    while (depth > 0) {
        val tag = find_div_tag(html, index) ?: return html.substring(0, start.first)
        depth += if (tag.closing) -1 else 1
        index = tag.end + 1
    }
    return html.substring(0, start.first) + html.substring(index)
}

fun thread_card_preview(body_html: String?, display_body: String): String {
    if (body_html != null && !looks_like_ciphertext(body_html)) {
        val unquoted = strip_leading_css(strip_body_html(strip_quoted_html(body_html)))
        if (unquoted.length > 4) return unquoted
        if (has_quote_attribution(body_html)) {
            val quoted = strip_leading_css(strip_body_html(strip_quote_attribution(body_html)))
            if (quoted.length > 4) return quoted
        }
    }
    return strip_leading_css(display_body)
}

fun clean_body_preview(body_text: String, body_html: String?): String {
    if (body_html != null && !looks_like_ciphertext(body_html)) {
        val preheader = extract_preheader_text(body_html)
        if (preheader.isNotEmpty()) return take_whole_chars(preheader, PREVIEW_MAX_LENGTH)
        val unquoted = strip_leading_css(strip_body_html(strip_quoted_html(body_html)))
        if (unquoted.length > 4) return take_whole_chars(unquoted, PREVIEW_MAX_LENGTH)
        val from_html = strip_leading_css(strip_body_html(strip_quote_attribution(body_html)))
        if (from_html.length > 4) return take_whole_chars(from_html, PREVIEW_MAX_LENGTH)
    }
    if (looks_like_ciphertext(body_text)) return ""
    return take_whole_chars(strip_leading_css(strip_body_html(body_text)), PREVIEW_MAX_LENGTH)
}

fun safe_display_text(text: String, max_length: Int = PREVIEW_MAX_LENGTH): String {
    if (text.isBlank()) return ""
    if (looks_like_ciphertext(text)) return ""
    return take_whole_chars(strip_leading_css(strip_body_html(text)), max_length)
}
