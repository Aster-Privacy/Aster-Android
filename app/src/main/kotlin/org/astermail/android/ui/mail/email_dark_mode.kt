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

internal const val FORCED_DARK_INK = "#e5e5e5"

private const val DARK_INK_LUMINANCE_LIMIT = 0.25

private const val PAGE_SURFACE_LUMINANCE_LIMIT = 0.5

internal const val KEEP_BACKGROUND_ATTRIBUTE = "data-aster-keep-bg"

private val NAMED_COLOR_LUMINANCE: Map<String, Double> = mapOf(
    "black" to 0.0, "navy" to 0.0018, "darkblue" to 0.0043, "midnightblue" to 0.0083,
    "darkgreen" to 0.0356, "darkslategray" to 0.0339, "darkslategrey" to 0.0339,
    "maroon" to 0.0265, "purple" to 0.0611, "indigo" to 0.0319, "darkred" to 0.0561,
    "dimgray" to 0.1413, "dimgrey" to 0.1413, "gray" to 0.2159, "grey" to 0.2159,
    "darkgray" to 0.3968, "darkgrey" to 0.3968, "brown" to 0.1119, "saddlebrown" to 0.0705,
    "darkolivegreen" to 0.0578, "darkslateblue" to 0.0651, "teal" to 0.1685,
    "olive" to 0.1637, "green" to 0.1544, "slategray" to 0.2079, "slategrey" to 0.2079,
)

private val COLOR_DECLARATION =
    Regex("(?<![-a-z])color\\s*:\\s*([^;}\"'<]+)", RegexOption.IGNORE_CASE)
private val STYLE_RULE_BODY = Regex("\\{([^{}]*)\\}")
private val BACKGROUND_COLOR_DECLARATION =
    Regex("background(?:-color)?\\s*:\\s*([^;}\"'<]+)", RegexOption.IGNORE_CASE)
private val BACKGROUND_IMAGE_DECLARATION =
    Regex("background(?:-image)?\\s*:[^;}\"'<]*url\\s*\\(", RegexOption.IGNORE_CASE)

private fun channel(value: Double): Double {
    val clamped = value.coerceIn(0.0, 1.0)
    return if (clamped <= 0.03928) clamped / 12.92 else Math.pow((clamped + 0.055) / 1.055, 2.4)
}

private fun relative_luminance(r: Double, g: Double, b: Double): Double =
    0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)

private fun functional_components(value: String): List<Double> {
    val open = value.indexOf('(')
    if (open < 0) return emptyList()
    val close = value.indexOf(')', open + 1)
    if (close < 0) return emptyList()
    return value.substring(open + 1, close)
        .split(',', ' ', '/')
        .mapNotNull { part -> part.trim().removeSuffix("%").removeSuffix("deg").trim().toDoubleOrNull() }
}

private fun hsl_to_rgb(hue: Double, saturation: Double, lightness: Double): Triple<Double, Double, Double> {
    val s = saturation.coerceIn(0.0, 1.0)
    val l = lightness.coerceIn(0.0, 1.0)
    val c = (1 - Math.abs(2 * l - 1)) * s
    val wrapped = hue % 360
    val h = (if (wrapped < 0) wrapped + 360 else wrapped) / 60
    val x = c * (1 - Math.abs(h % 2 - 1))
    val base = when {
        h < 1 -> Triple(c, x, 0.0)
        h < 2 -> Triple(x, c, 0.0)
        h < 3 -> Triple(0.0, c, x)
        h < 4 -> Triple(0.0, x, c)
        h < 5 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    val m = l - c / 2
    return Triple(base.first + m, base.second + m, base.third + m)
}

internal fun color_luminance(raw: String): Double? {
    val value = raw.trim().lowercase()
    if (value.isEmpty()) return null
    NAMED_COLOR_LUMINANCE[value]?.let { return it }
    if (value.startsWith("#")) {
        val hex = value.drop(1)
        val expanded = when (hex.length) {
            3, 4 -> hex.take(3).map { "$it$it" }.joinToString("")
            6, 8 -> hex.take(6)
            else -> return null
        }
        val parsed = expanded.toLongOrNull(16) ?: return null
        return relative_luminance(
            ((parsed shr 16) and 0xFF) / 255.0,
            ((parsed shr 8) and 0xFF) / 255.0,
            (parsed and 0xFF) / 255.0,
        )
    }
    if (value.startsWith("rgb")) {
        val numbers = functional_components(value)
        if (numbers.size < 3) return null
        return relative_luminance(numbers[0] / 255, numbers[1] / 255, numbers[2] / 255)
    }
    if (value.startsWith("hsl")) {
        val numbers = functional_components(value)
        if (numbers.size < 3) return null
        val rgb = hsl_to_rgb(numbers[0], numbers[1] / 100, numbers[2] / 100)
        return relative_luminance(rgb.first, rgb.second, rgb.third)
    }
    return null
}

internal fun reads_too_dark_on_dark(raw: String): Boolean {
    val value = color_luminance(raw) ?: return false
    return value < DARK_INK_LUMINANCE_LIMIT
}

internal fun is_page_surface(raw: String): Boolean {
    val value = color_luminance(raw) ?: return false
    return value >= PAGE_SURFACE_LUMINANCE_LIMIT
}

private fun declared_background(element: org.jsoup.nodes.Element): String? {
    val attribute = element.attr("bgcolor").trim()
    if (attribute.isNotEmpty()) return attribute
    val style = element.attr("style")
    if (style.isEmpty()) return null
    if (BACKGROUND_IMAGE_DECLARATION.containsMatchIn(style)) return null
    val match = BACKGROUND_COLOR_DECLARATION.find(style) ?: return null
    return match.groupValues[1].trim().removeSuffix("!important").trim()
}

private fun lighten_color_declarations(css: String): String {
    if (BACKGROUND_IMAGE_DECLARATION.containsMatchIn(css)) return css
    return COLOR_DECLARATION.replace(css) { match ->
        val raw = match.groupValues[1].trim()
        val important = raw.lowercase().endsWith("!important")
        val color = if (important) raw.dropLast("!important".length).trim() else raw
        when {
            !reads_too_dark_on_dark(color) -> match.value
            important -> "color:$FORCED_DARK_INK !important"
            else -> "color:$FORCED_DARK_INK"
        }
    }
}

internal fun lighten_dark_email_text(body: String): String = try {
    val doc = org.jsoup.Jsoup.parseBodyFragment(body)
    doc.outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
    var changed = false
    for (element in doc.select("[bgcolor],[style]")) {
        val background = declared_background(element) ?: continue
        if (color_luminance(background) == null || is_page_surface(background)) continue
        element.attr(KEEP_BACKGROUND_ATTRIBUTE, "1")
        for (link in element.select("a")) link.attr(KEEP_BACKGROUND_ATTRIBUTE, "1")
        changed = true
    }
    for (element in doc.select("[style]")) {
        if (element.hasAttr(KEEP_BACKGROUND_ATTRIBUTE)) continue
        val style = element.attr("style")
        val lightened = lighten_color_declarations(style)
        if (lightened != style) {
            element.attr("style", lightened)
            changed = true
        }
    }
    for (element in doc.select("style")) {
        val css = element.data()
        val lightened = STYLE_RULE_BODY.replace(css) { rule ->
            "{" + lighten_color_declarations(rule.groupValues[1]) + "}"
        }
        if (lightened != css) {
            element.empty()
            element.appendChild(org.jsoup.nodes.DataNode(lightened))
            changed = true
        }
    }
    for (element in doc.select("font[color]")) {
        if (reads_too_dark_on_dark(element.attr("color"))) {
            element.attr("color", FORCED_DARK_INK)
            changed = true
        }
    }
    if (changed) doc.body().html() else body
} catch (_: Throwable) {
    body
}

private const val KEEPS_BACKGROUND =
    ":not([style*=\"background-image\" i]):not([style*=\"url(\" i]):not([background])" +
        ":not([$KEEP_BACKGROUND_ATTRIBUTE])"

private val NEUTRALIZED_TAGS = listOf(
    "div", "table", "tbody", "thead", "tfoot", "tr", "td", "th", "section", "header",
    "footer", "main", "article", "aside", "nav", "center", "form", "fieldset", "legend",
    "figure", "figcaption", "details", "summary", "address", "hgroup", "p", "ul", "ol",
    "li", "h1", "h2", "h3", "h4", "h5", "h6", "span", "font", "b", "strong", "em", "i",
)

internal fun forced_dark_mode_css(
    link_hex: String,
    quote_border: String,
    quote_color: String,
): String {
    val neutralized = NEUTRALIZED_TAGS.joinToString(",") { "$it$KEEPS_BACKGROUND" }
    val readable_link = "a:not(.aster-email-button):not([$KEEP_BACKGROUND_ATTRIBUTE])"
    val readable_links = "$readable_link,$readable_link *"
    return """
html{color-scheme:dark!important}
html,body{background-color:transparent!important;color:$FORCED_DARK_INK!important}
$neutralized{background-color:transparent!important;background-image:none!important}
$readable_links{color:$link_hex!important}
a[style*="background" i] *,[bgcolor] > a *{color:inherit!important}
img{opacity:0.87}
hr{border-color:#374151!important;background-color:#374151!important;color:#374151!important}
blockquote{border-left-color:$quote_border!important;color:$quote_color!important}
"""
}
