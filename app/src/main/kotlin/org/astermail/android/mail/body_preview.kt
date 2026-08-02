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
    text = text.replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF\\u00AD\\u034F\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u3164\\uFFA0]"), "")
    text = text.replace(Regex("\\s+"), " ")
    return text.trim()
}

fun clean_body_preview(body_text: String, body_html: String?): String {
    if (body_html != null && !looks_like_ciphertext(body_html)) {
        val from_html = strip_body_html(body_html)
        if (from_html.length > 4) return from_html.take(PREVIEW_MAX_LENGTH)
    }
    if (looks_like_ciphertext(body_text)) return ""
    return strip_body_html(body_text).take(PREVIEW_MAX_LENGTH)
}

fun safe_display_text(text: String, max_length: Int = PREVIEW_MAX_LENGTH): String {
    if (text.isBlank()) return ""
    if (looks_like_ciphertext(text)) return ""
    return strip_body_html(text).take(max_length)
}
