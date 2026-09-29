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

fun signature_below_quote(signature_placement: Int?, preference: String?): Boolean {
    if (signature_placement == 1) return false
    if (signature_placement == 0) return true
    return preference != "above"
}

fun split_trailing_signature(body: String, signature: String): Pair<String, String>? {
    if (signature.isBlank()) return null
    val trimmed = body.trimEnd('\n', ' ')
    if (!trimmed.endsWith(signature)) return null
    val before = trimmed.substring(0, trimmed.length - signature.length).trimEnd('\n', ' ')
    return before to signature
}

fun plain_signature_with_separator(content: String, preference: Boolean?): String {
    val trimmed = content.trim('\n', '\r')
    return if (trimmed.isBlank() || preference == false) trimmed else "--\n" + trimmed
}

fun seeded_body_with_signature(prefix: String, signature: String, watermark: String): String = when {
    signature.isBlank() -> prefix + watermark
    prefix.isBlank() -> "\n\n" + signature + watermark
    else -> prefix + signature + watermark
}

fun append_signature(core: String, signature: String): String =
    if (core.isBlank()) "\n\n" + signature else core + "\n\n" + signature

fun caret_starts_above_signature(body: String): Boolean =
    body.startsWith("\n") && body.length > 1

fun html_signature_with_separator(html: String, preference: Boolean?): String =
    if (html.isBlank() || preference == false) html else "--<br>" + html

const val SIGNATURE_GAP_HTML = "<div><br></div>"

const val IMG_TOKEN_PATTERN = """\[\[ASTER_IMG_\d+]]"""

private val HTML_TAG_RE = Regex("<[^>]*>")

private fun escape_body_text(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun plain_lines_html(text: String): String =
    text.split("\n").joinToString("") { line ->
        if (line.isEmpty()) SIGNATURE_GAP_HTML else "<div>" + escape_body_text(line) + "</div>"
    }

fun plain_signature_html(signature: String): String =
    signature.split("\n").joinToString("<br>") { escape_body_text(it) }

fun signature_div_html(signature_id: String?, inner_html: String): String {
    val id_attr = signature_id?.takeIf { it.isNotBlank() }
        ?.let { " data-aster-signature-id=\"" + escape_body_text(it) + "\"" }
        .orEmpty()
    return "<div data-aster-signature=\"1\"$id_attr>$inner_html</div>"
}

fun trim_trailing_breaks(html: String): String {
    var out = html.trimEnd('\n', ' ')
    while (out.endsWith("<br>")) out = out.removeSuffix("<br>").trimEnd('\n', ' ')
    return out
}

fun split_body_before_signature(body: String, signature: String, is_rich: Boolean): String? {
    if (signature.isBlank()) return null
    val marker = if (is_rich) plain_signature_html(signature) else signature
    val trimmed = if (is_rich) trim_trailing_breaks(body) else body.trimEnd('\n', ' ')
    if (!trimmed.endsWith(marker)) return null
    return trimmed.dropLast(marker.length)
}

private fun has_typed_content(before: String, is_rich: Boolean): Boolean =
    if (is_rich) {
        HTML_TAG_RE.replace(before, "").replace("&nbsp;", " ").isNotBlank() ||
            before.contains("<hr", ignoreCase = true)
    } else {
        before.isNotBlank()
    }

private fun lines_before_signature_html(before: String, is_rich: Boolean, drop_gap: Boolean): String {
    if (before.isEmpty()) return ""
    if (is_rich) {
        val kept = if (drop_gap && before.endsWith("<br><br>")) before.removeSuffix("<br>") else before
        return "<div>$kept</div>"
    }
    val lines = before.removeSuffix("\n").split("\n")
    val kept = if (drop_gap && lines.size >= 2 && lines.last().isEmpty()) lines.dropLast(1) else lines
    return plain_lines_html(kept.joinToString("\n"))
}

fun assemble_body_with_signature(
    body: String,
    is_rich: Boolean,
    plain_signature: String,
    html_signature: String,
    signature_id: String?,
    quote_html: String,
    place_below: Boolean,
): String {
    val split = split_body_before_signature(body, plain_signature, is_rich)
    val signature_div = when {
        split != null -> signature_div_html(signature_id, plain_signature_html(plain_signature))
        html_signature.isNotBlank() -> signature_div_html(signature_id, html_signature)
        else -> {
            val core = if (is_rich) body else body.trimEnd('\n', ' ')
            val core_html = when {
                is_rich -> core
                core.isEmpty() -> ""
                else -> plain_lines_html(core)
            }
            return core_html + quote_html
        }
    }
    val before = split ?: if (is_rich) {
        trim_trailing_breaks(body) + "<br><br>"
    } else {
        body.trimEnd('\n', ' ') + "\n\n"
    }
    val move_below = place_below && quote_html.isNotEmpty() && has_typed_content(before, is_rich)
    val lines_html = lines_before_signature_html(before, is_rich, move_below)
    return if (move_below) {
        lines_html + quote_html + SIGNATURE_GAP_HTML + signature_div
    } else {
        lines_html + signature_div + quote_html
    }
}

fun draft_html_with_signature(formatted: String, is_rich: Boolean, signature_html: String): String {
    if (signature_html.isBlank()) return formatted
    val body_html = if (is_rich) formatted else escape_body_text(formatted).replace("\n", "<br>")
    return body_html + "<br><br><div class=\"aster_signature\">" + signature_html + "</div>"
}
