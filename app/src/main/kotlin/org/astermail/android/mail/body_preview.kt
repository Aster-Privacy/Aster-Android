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

fun strip_body_html(html: String): String {
    var text = html
    text = text.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
    text = text.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
    text = text.replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), " ")
    text = text.replace(Regex("<[^>]+>"), " ")
    text = decode_html_entities(text)
    text = text.replace(Regex("<[^>]+>"), " ")
    text = text.replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF\\u00AD\\u034F\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u3164\\uFFA0\\uFFF9-\\uFFFC]"), "")
    text = text.replace(Regex("\\s+"), " ")
    return text.trim()
}

private val HTML_TAG_PATTERN = Regex(
    "<\\s*(?:/?)(?:html|body|div|p|br|span|a|blockquote|table|tbody|tr|td|th|ul|ol|li|h[1-6]|img|b|i|u|em|strong|pre|code|font|hr)\\b[^>]*>",
    RegexOption.IGNORE_CASE,
)

private val HTML_DOCUMENT_PATTERN = Regex("<\\s*(?:html|body)\\b[^>]*>", RegexOption.IGNORE_CASE)

private val HTML_CLOSING_PATTERN = Regex(
    "</\\s*(?:html|body|div|p|span|a|blockquote|table|tbody|tr|td|th|ul|ol|li|h[1-6]|b|i|u|em|strong|pre|code|font)\\s*>",
    RegexOption.IGNORE_CASE,
)

private val HTML_BREAK_PATTERN = Regex("<\\s*br\\s*/?>", RegexOption.IGNORE_CASE)

fun looks_like_html_body(text: String): Boolean {
    if (HTML_DOCUMENT_PATTERN.containsMatchIn(text)) return true
    if (!HTML_TAG_PATTERN.containsMatchIn(text)) return false
    return HTML_CLOSING_PATTERN.containsMatchIn(text) || HTML_BREAK_PATTERN.containsMatchIn(text)
}

fun html_to_plain_text(html: String): String {
    var text = html
    text = text.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
    text = text.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
    text = text.replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), " ")
    text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(Regex("</(?:p|div|li|tr|h[1-6]|blockquote|pre)>", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(Regex("<[^>]+>"), "")
    text = decode_html_entities(text)
    text = text.replace("\r\n", "\n").replace('\r', '\n')
    text = text.replace(Regex("[ \\t\\u00A0]+"), " ")
    text = text.replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
    text = text.replace(Regex("\\n{3,}"), "\n\n")
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

fun decode_html_entities(input: String): String {
    var text = input
    text = text.replace(NAMED_ENTITY_PATTERN) { m ->
        NAMED_ENTITIES[m.groupValues[1]] ?: m.value
    }
    text = text.replace(Regex("&#(\\d+);")) { m ->
        m.groupValues[1].toIntOrNull()?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: m.value
    }
    text = text.replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
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

fun clean_body_preview(body_text: String, body_html: String?): String {
    if (body_html != null && !looks_like_ciphertext(body_html)) {
        val preheader = extract_preheader_text(body_html)
        if (preheader.isNotEmpty()) return take_whole_chars(preheader, PREVIEW_MAX_LENGTH)
        val from_html = strip_body_html(body_html)
        if (from_html.length > 4) return take_whole_chars(from_html, PREVIEW_MAX_LENGTH)
    }
    if (looks_like_ciphertext(body_text)) return ""
    return take_whole_chars(strip_body_html(body_text), PREVIEW_MAX_LENGTH)
}

fun safe_display_text(text: String, max_length: Int = PREVIEW_MAX_LENGTH): String {
    if (text.isBlank()) return ""
    if (looks_like_ciphertext(text)) return ""
    return take_whole_chars(strip_body_html(text), max_length)
}
