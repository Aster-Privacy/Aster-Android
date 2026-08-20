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
    text = text.replace("&nbsp;", " ")
    text = text.replace("&amp;", "&")
    text = text.replace("&lt;", "<")
    text = text.replace("&gt;", ">")
    text = text.replace("&quot;", "\"")
    text = text.replace("&#39;", "'")
    text = text.replace("&apos;", "'")
    text = text.replace("&mdash;", "-")
    text = text.replace("&ndash;", "-")
    text = text.replace("&hellip;", "...")
    text = text.replace(Regex("&#(\\d+);")) { m ->
        m.groupValues[1].toIntOrNull()?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: " "
    }
    text = text.replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
        m.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: " "
    }
    text = text.replace(Regex("&[a-zA-Z]+;"), " ")
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

fun decode_html_entities(input: String): String {
    var text = input
    text = text.replace("&nbsp;", " ")
    text = text.replace("&lt;", "<")
    text = text.replace("&gt;", ">")
    text = text.replace("&quot;", "\"")
    text = text.replace("&#39;", "'")
    text = text.replace("&apos;", "'")
    text = text.replace("&mdash;", "-")
    text = text.replace("&ndash;", "-")
    text = text.replace("&hellip;", "...")
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
