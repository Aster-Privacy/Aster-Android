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

package org.astermail.android.ui.settings.detail

private val signature_allowed_css_properties = setOf(
    "color", "background-color", "font-size", "font-weight", "font-style", "font-family",
    "text-align", "text-decoration", "text-transform", "letter-spacing", "white-space", "line-height",
    "border", "border-top", "border-right", "border-bottom", "border-left",
    "border-inline-start", "border-inline-end", "border-width", "border-style", "border-color",
    "border-collapse", "border-spacing", "border-radius", "border-top-left-radius",
    "border-top-right-radius", "border-bottom-left-radius", "border-bottom-right-radius",
    "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
    "padding-inline-start", "padding-inline-end",
    "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
    "margin-inline-start", "margin-inline-end",
    "max-width", "min-width", "max-height", "min-height", "height", "width",
    "table-layout", "object-fit", "float", "clear", "list-style-type", "list-style-position",
    "direction", "unicode-bidi", "display", "vertical-align",
)

private val signature_allowed_display_values = setOf(
    "block", "inline", "inline-block", "table", "inline-table", "table-row", "table-cell",
    "table-row-group", "table-header-group", "table-footer-group", "table-column",
    "table-column-group", "table-caption",
)

private val signature_allowed_vertical_align_values = setOf(
    "baseline", "top", "middle", "bottom", "text-top", "text-bottom",
)

private val signature_blocked_style_tokens = listOf(
    "url(", "expression", "behavior", "-moz-binding", "@import", "javascript:", "vbscript:",
    "position", "image-set(", "paint(", "-moz-document", "image(", "element(",
)

internal fun split_signature_style_declarations(body: String): List<String> {
    val declarations = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var depth = 0
    for (c in body) {
        val open = quote
        if (open != null) {
            current.append(c)
            if (c == open) quote = null
            continue
        }
        when {
            c == '"' || c == '\'' -> {
                quote = c
                current.append(c)
            }
            c == '(' -> {
                depth += 1
                current.append(c)
            }
            c == ')' -> {
                depth = maxOf(depth - 1, 0)
                current.append(c)
            }
            c == ';' && depth == 0 -> {
                declarations.add(current.toString())
                current.setLength(0)
            }
            else -> current.append(c)
        }
    }
    declarations.add(current.toString())
    return declarations.map { it.trim() }.filter { it.isNotEmpty() }
}

internal fun signature_style_declaration(declaration: String): String? {
    if (declaration.contains('\\') || declaration.contains("/*") || declaration.contains("*/")) return null
    if (declaration.any { it.isISOControl() }) return null
    val colon = declaration.indexOf(':')
    if (colon <= 0) return null
    val property = declaration.substring(0, colon).trim().lowercase()
    val value = declaration.substring(colon + 1).trim()
    if (value.isEmpty()) return null
    if (property !in signature_allowed_css_properties) return null
    val compact = value.lowercase().filterNot { it.isWhitespace() }
    if (signature_blocked_style_tokens.any { compact.contains(it) }) return null
    if (property == "display" && compact !in signature_allowed_display_values) return null
    if (property == "vertical-align" && compact !in signature_allowed_vertical_align_values) return null
    return "$property: $value"
}

internal fun scrub_signature_style(raw: String): String? {
    val kept = split_signature_style_declarations(raw).mapNotNull(::signature_style_declaration)
    return if (kept.isEmpty()) null else kept.joinToString("; ")
}

internal fun scrub_signature_styles(html: String): String {
    if (!html.contains("style", ignoreCase = true)) return html
    val body = org.jsoup.Jsoup.parseBodyFragment(html).apply { outputSettings().prettyPrint(false) }.body()
    body.select("[style]").forEach { element ->
        val scrubbed = scrub_signature_style(element.attr("style"))
        if (scrubbed == null) element.removeAttr("style") else element.attr("style", scrubbed)
    }
    return body.html()
}
