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

internal const val ESTIMATE_SAMPLE_CHARS = 60_000
internal const val ESTIMATE_MIN_DP = 24
internal const val ESTIMATE_MAX_DP = 900
internal const val ESTIMATE_LINE_DP = 20
internal const val ESTIMATE_CHAR_DP = 7.5f
internal const val ESTIMATE_SIDE_PADDING_DP = 32
internal const val ESTIMATE_BODY_PADDING_DP = 12
internal const val ESTIMATE_QUOTE_TOGGLE_DP = 46
internal const val ESTIMATE_UNKNOWN_IMAGE_DP = 120
internal const val ESTIMATE_MAX_IMAGES = 8

private val non_rendered_block = Regex(
    """<(style|script|head|title|template|noscript)\b[^>]*>.*?</\1\s*>|<!--.*?-->""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val quoted_history_marker = Regex(
    """<blockquote\b|class\s*=\s*["'][^"']*(?:gmail_quote|moz-cite-prefix|yahoo_quoted|protonmail_quote|aster-quoted|divRplyFwdMsg)|id\s*=\s*["'](?:divRplyFwdMsg|appendonsend)""",
    RegexOption.IGNORE_CASE,
)

private val line_break_tag = Regex("""<(?:br\b[^>]*|/(?:p|div|tr|li|h[1-6]|table|blockquote)\s*)>""", RegexOption.IGNORE_CASE)

private val any_tag = Regex("<[^>]{0,4000}>")

private val image_tag = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)

private val image_width_attribute = Regex("""\bwidth\s*=\s*["']?\s*(\d{1,5})(?:px)?\s*["']?(?=[\s/>])""", RegexOption.IGNORE_CASE)

private val image_height_attribute = Regex("""\bheight\s*=\s*["']?\s*(\d{1,5})(?:px)?\s*["']?(?=[\s/>])""", RegexOption.IGNORE_CASE)

private val html_entity = Regex("""&(?:#\d{1,7}|#x[0-9a-f]{1,6}|[a-z]{2,8});""", RegexOption.IGNORE_CASE)

private val horizontal_space = Regex("""[ \t\r\u000C ]+""")

private val repeated_blank_lines = Regex("""\n\s*\n(?:\s*\n)+""")

internal data class estimated_body_parts(val text_lines: Int, val image_dp: Int, val has_quote: Boolean)

internal fun estimate_body_parts(html: String, width_dp: Int): estimated_body_parts {
    val sample = if (html.length > ESTIMATE_SAMPLE_CHARS) html.substring(0, ESTIMATE_SAMPLE_CHARS) else html
    val rendered = non_rendered_block.replace(sample, " ")
    val quote_at = quoted_history_marker.find(rendered)?.range?.first?.let { at ->
        if (rendered[at] == '<') at else rendered.lastIndexOf('<', at).takeIf { it >= 0 } ?: at
    }
    val visible = if (quote_at != null) rendered.substring(0, quote_at) else rendered
    val content_width = (width_dp - ESTIMATE_SIDE_PADDING_DP).coerceAtLeast(120)
    var image_dp = 0
    for (match in image_tag.findAll(visible).take(ESTIMATE_MAX_IMAGES)) {
        image_dp += estimated_image_dp(match.value, content_width)
    }
    val text = repeated_blank_lines.replace(
        html_entity.replace(any_tag.replace(line_break_tag.replace(visible, "\n"), " "), "x"),
        "\n\n",
    )
    val chars_per_line = (content_width / ESTIMATE_CHAR_DP).toInt().coerceAtLeast(16)
    var lines = 0
    for (raw_line in text.trim().split('\n')) {
        val length = horizontal_space.replace(raw_line, " ").trim().length
        lines += if (length == 0) 1 else (length + chars_per_line - 1) / chars_per_line
    }
    return estimated_body_parts(text_lines = lines, image_dp = image_dp, has_quote = quote_at != null)
}

internal fun estimated_image_dp(tag: String, content_width_dp: Int): Int {
    val width = image_width_attribute.find(tag)?.groupValues?.get(1)?.toIntOrNull()
    val height = image_height_attribute.find(tag)?.groupValues?.get(1)?.toIntOrNull()
    if ((width != null && width <= 2) || (height != null && height <= 2)) return 0
    if (height == null) return ESTIMATE_UNKNOWN_IMAGE_DP
    if (width == null || width <= content_width_dp) return height
    return (height.toLong() * content_width_dp / width).toInt()
}

internal fun estimated_body_height_dp(html: String, width_dp: Int): Int {
    val parts = estimate_body_parts(html, width_dp)
    val quote_dp = if (parts.has_quote) ESTIMATE_QUOTE_TOGGLE_DP else 0
    val total = parts.text_lines * ESTIMATE_LINE_DP + parts.image_dp + quote_dp + ESTIMATE_BODY_PADDING_DP
    return total.coerceIn(ESTIMATE_MIN_DP, ESTIMATE_MAX_DP)
}

internal fun body_height_key(
    base_key: Long,
    dyslexia_font: Boolean,
    email_font_id: Any?,
    underline_links: Boolean,
    is_dark: Boolean,
    force_dark: Boolean,
): Long {
    var key = base_key
    key = key * 31L + (if (dyslexia_font) 1L else 0L)
    key = key * 31L + (email_font_id?.hashCode() ?: 0).toLong()
    key = key * 31L + (if (underline_links) 1L else 0L)
    key = key * 31L + (if (is_dark) 1L else 0L)
    key = key * 31L + (if (force_dark) 1L else 0L)
    return key
}
