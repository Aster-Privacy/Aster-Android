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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.astermail.android.design.AsterSemanticColors

internal const val FORCED_DARK_INK = "#e5e5e5"

internal fun forces_dark_emails(preference: Boolean, theme_dark: Boolean): Boolean =
    preference && theme_dark

internal fun email_theme_is_dark(colors: AsterSemanticColors): Boolean =
    if (colors.is_glass) colors.is_dark else colors.bg_primary.luminance() < colors.text_primary.luminance()

private const val GLASS_EMAIL_SURFACE_COVER = 0.82f

internal fun email_glass_backing(colors: AsterSemanticColors, white_page: Boolean): Color {
    if (white_page || !colors.is_translucent) return Color.Transparent
    val alpha = 1f - (1f - GLASS_EMAIL_SURFACE_COVER) / (1f - colors.glass_opacity)
    return if (alpha <= 0f) Color.Transparent else colors.thread_content_bg.copy(alpha = alpha)
}

private const val DARK_INK_LUMINANCE_LIMIT = 0.25

private const val PAGE_SURFACE_LUMINANCE_LIMIT = 0.5

internal const val KEEP_BACKGROUND_ATTRIBUTE = "data-aster-keep-bg"

internal const val BACKGROUND_IMAGE_ATTRIBUTE = "data-aster-bg-image"

internal const val PAPER_ATTRIBUTE = "data-aster-paper"

internal const val PAPER_FILL_VALUE = "fill"

internal const val PAPER_INK = "#1f2937"

internal const val PAPER_LINK = "#1a56db"

internal const val PAPER_FILL = "#f3f4f6"

private val NAMED_COLOR_LUMINANCE: Map<String, Double> = mapOf(
    "black" to 0.0, "navy" to 0.0018, "darkblue" to 0.0043, "midnightblue" to 0.0083,
    "darkgreen" to 0.0356, "darkslategray" to 0.0339, "darkslategrey" to 0.0339,
    "maroon" to 0.0265, "purple" to 0.0611, "indigo" to 0.0319, "darkred" to 0.0561,
    "dimgray" to 0.1413, "dimgrey" to 0.1413, "gray" to 0.2159, "grey" to 0.2159,
    "darkgray" to 0.3968, "darkgrey" to 0.3968, "brown" to 0.1119, "saddlebrown" to 0.0705,
    "darkolivegreen" to 0.0578, "darkslateblue" to 0.0651, "teal" to 0.1685,
    "olive" to 0.1637, "green" to 0.1544, "slategray" to 0.2079, "slategrey" to 0.2079,
)

private val LIGHT_NAMED_COLOR_LUMINANCE: Map<String, Double> = mapOf(
    "white" to 1.0, "snow" to 0.9647, "ghostwhite" to 0.9486, "whitesmoke" to 0.9131,
    "ivory" to 0.9922, "floralwhite" to 0.9636, "seashell" to 0.9466, "oldlace" to 0.9306,
    "linen" to 0.9113, "cornsilk" to 0.9471, "beige" to 0.9157, "lightyellow" to 0.9867,
    "honeydew" to 0.9643, "mintcream" to 0.9773, "azure" to 0.9775, "aliceblue" to 0.9289,
    "lavenderblush" to 0.9297, "gainsboro" to 0.7157, "lightgray" to 0.6518, "lightgrey" to 0.6518,
)

private val COLOR_DECLARATION =
    Regex("(?<![-a-z])color\\s*:\\s*([^;}\"'<]+)", RegexOption.IGNORE_CASE)
private val STYLE_RULE_BODY = Regex("\\{([^{}]*)\\}")
private val BACKGROUND_COLOR_DECLARATION =
    Regex("background(?:-color)?\\s*:\\s*([^;}\"'<]+)", RegexOption.IGNORE_CASE)
private val BACKGROUND_IMAGE_PROPERTY =
    Regex("background(?:-image)?\\s*+:", RegexOption.IGNORE_CASE)
private val URL_OPENING = Regex("url\\s*+\\(", RegexOption.IGNORE_CASE)
private const val DECLARATION_VALUE_END = ";}\"'<"
private val BACKGROUND_IMAGE_VALUE =
    Regex("background-image\\s*:\\s*([^;}\"'<]*)", RegexOption.IGNORE_CASE)
private val NO_BACKGROUND_IMAGE =
    Regex("^(?:none|initial|unset|inherit|revert|revert-layer)?$", RegexOption.IGNORE_CASE)
private val IMPORTANT_FLAG = Regex("!\\s*important", RegexOption.IGNORE_CASE)
private val STYLE_RULE = Regex("(?<![^{}])([^{}]+)\\{([^{}]*)\\}")
private val BACKGROUND_SOURCE = Regex(
    "background(?:-color)?\\s*:\\s*([^;}\"'<]+)|bgcolor\\s*=\\s*[\"']?([^\"'\\s>]+)",
    RegexOption.IGNORE_CASE,
)
private val COLOR_TOKEN = Regex(
    "#[0-9a-f]{3,8}\\b|(?:rgb|hsl)a?\\([^)]{0,128}\\)|(?<![-\\w])(?:" +
        LIGHT_NAMED_COLOR_LUMINANCE.keys.joinToString("|") + ")(?![-\\w])",
    RegexOption.IGNORE_CASE,
)

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
    LIGHT_NAMED_COLOR_LUMINANCE[value]?.let { return it }
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

private fun declares_background_image_url(css: String): Boolean {
    var from = 0
    while (from < css.length) {
        val property = BACKGROUND_IMAGE_PROPERTY.find(css, from) ?: return false
        val value_start = property.range.last + 1
        var value_end = value_start
        while (value_end < css.length && css[value_end] !in DECLARATION_VALUE_END) value_end++
        if (URL_OPENING.toPattern().matcher(css).region(value_start, value_end).find()) return true
        from = maxOf(value_end, property.range.first + 1)
    }
    return false
}

internal fun declares_background_image(css: String): Boolean {
    if (declares_background_image_url(css)) return true
    return BACKGROUND_IMAGE_VALUE.findAll(css).any { match ->
        !NO_BACKGROUND_IMAGE.matches(IMPORTANT_FLAG.replace(match.groupValues[1], "").trim())
    }
}

private fun is_translucent(raw: String): Boolean {
    val value = raw.trim().lowercase()
    if (value.startsWith("#")) {
        val hex = value.drop(1)
        val alpha = when (hex.length) {
            4 -> "${hex[3]}${hex[3]}"
            8 -> hex.substring(6, 8)
            else -> return false
        }
        return (alpha.toIntOrNull(16) ?: 255) < 128
    }
    val numbers = functional_components(value)
    if (numbers.size < 4) return false
    val alpha = if (numbers[3] > 1.0) numbers[3] / 100 else numbers[3]
    return alpha < 0.5
}

private fun reads_as_light_surface(raw: String): Boolean =
    !is_translucent(raw) && (raw.trim().equals("white", ignoreCase = true) || is_page_surface(raw))

internal fun declares_light_background(body: String): Boolean =
    BACKGROUND_SOURCE.findAll(body).any { match ->
        val value = match.groupValues[1].ifEmpty { match.groupValues[2] }
        COLOR_TOKEN.findAll(value).any { reads_as_light_surface(it.value) }
    }

private const val PLAIN_TEXT_IMAGE_LIMIT = 2

private const val PLAIN_WHITE_LUMINANCE = 0.85

internal fun reads_as_plain_light_text(body: String): Boolean = reads_as_plain_text(body, allow_unstyled = false)

internal fun reads_as_plain_table_correspondence(body: String): Boolean = try {
    when {
        BACKGROUND_ATTRIBUTE.containsMatchIn(body) -> false
        else -> {
            val doc = org.jsoup.Jsoup.parseBodyFragment(body)
            when {
                doc.select("style").any { BACKGROUND_SOURCE.containsMatchIn(it.data()) } -> false
                doc.select("table, thead, tbody, tfoot, tr, td, th")
                    .any { BACKGROUND_SOURCE.containsMatchIn(it.attr("style")) } -> false
                else -> reads_as_plain_text(body, allow_unstyled = true)
            }
        }
    }
} catch (_: Throwable) {
    false
}

private val BGCOLOR_ATTRIBUTE = Regex("<[^>]*\\sbgcolor\\s*=", RegexOption.IGNORE_CASE)

private val BACKGROUND_ATTRIBUTE = Regex("<[^>]*\\sbackground\\s*=", RegexOption.IGNORE_CASE)

private fun reads_as_plain_text(body: String, allow_unstyled: Boolean): Boolean = try {
    val doc = org.jsoup.Jsoup.parseBodyFragment(body)
    val decorated = doc.select("img").size
    val sources = BACKGROUND_SOURCE.findAll(body).map { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }.toList()
    when {
        decorated > PLAIN_TEXT_IMAGE_LIMIT -> false
        BGCOLOR_ATTRIBUTE.containsMatchIn(body) -> false
        sources.isEmpty() -> allow_unstyled && doc.body().text().isNotBlank()
        sources.any { declares_background_image("background:$it") } -> false
        doc.select("[background]").isNotEmpty() -> false
        doc.body().text().isBlank() -> false
        else -> sources.all { value ->
            val tokens = COLOR_TOKEN.findAll(value).map { it.value }.toList()
            val bare = value.trim().removeSuffix("!important").trim().lowercase()
            if (tokens.isEmpty()) {
                bare in PLAIN_NEUTRAL_BACKGROUNDS
            } else {
                tokens.all { token -> is_translucent(token) || reads_as_neutral_white(token) }
            }
        }
    }
} catch (_: Throwable) {
    false
}

private fun reads_as_neutral_white(raw: String): Boolean {
    val value = raw.trim().lowercase()
    if (value == "white") return true
    val channels = when {
        value.startsWith("#") -> {
            val hex = value.drop(1)
            val expanded = when (hex.length) {
                3, 4 -> hex.take(3).map { "$it$it" }.joinToString("")
                6, 8 -> hex.take(6)
                else -> return false
            }
            val parsed = expanded.toLongOrNull(16) ?: return false
            listOf((parsed shr 16) and 0xFF, (parsed shr 8) and 0xFF, parsed and 0xFF).map { it.toDouble() }
        }
        value.startsWith("rgb") -> functional_components(value).take(3).takeIf { it.size == 3 } ?: return false
        else -> return false
    }
    val spread = channels.max() - channels.min()
    return spread <= PLAIN_WHITE_SPREAD && (color_luminance(value) ?: 0.0) >= PLAIN_WHITE_LUMINANCE
}

private const val PLAIN_WHITE_SPREAD = 24.0

private val PLAIN_NEUTRAL_BACKGROUNDS = setOf("transparent", "none", "inherit", "initial", "unset", "window", "")

internal fun is_page_surface(raw: String): Boolean {
    val value = color_luminance(raw) ?: return false
    return value >= PAGE_SURFACE_LUMINANCE_LIMIT
}

private fun declared_background(element: org.jsoup.nodes.Element): String? {
    val attribute = element.attr("bgcolor").trim()
    if (attribute.isNotEmpty()) return attribute
    val style = element.attr("style")
    if (style.isEmpty()) return null
    if (declares_background_image_url(style)) return null
    val match = BACKGROUND_COLOR_DECLARATION.find(style) ?: return null
    return match.groupValues[1].trim().removeSuffix("!important").trim()
}

private fun lighten_color_declarations(css: String): String {
    if (declares_background_image(css)) return css
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

private fun clear_light_backgrounds(css: String): String {
    if (declares_background_image(css)) return css
    return BACKGROUND_COLOR_DECLARATION.replace(css) { match ->
        val value = match.groups[1]!!
        val cleared = COLOR_TOKEN.replace(value.value) { token ->
            if (reads_as_light_surface(token.value)) "transparent" else token.value
        }
        if (cleared == value.value) {
            match.value
        } else {
            match.value.substring(0, value.range.first - match.range.first) + cleared
        }
    }
}

private fun declares_text_color(element: org.jsoup.nodes.Element): Boolean {
    if (element.tagName() == "font" && element.attr("color").isNotBlank()) return true
    val style = element.attr("style")
    return style.isNotEmpty() && COLOR_DECLARATION.containsMatchIn(style)
}

private fun inherits_declared_text_color(element: org.jsoup.nodes.Element): Boolean {
    var current: org.jsoup.nodes.Element? = element
    while (current != null && current.tagName() != "body") {
        if (declares_text_color(current)) return true
        current = current.parent()
    }
    return false
}

private fun solid_background_token(element: org.jsoup.nodes.Element): String? {
    val attribute = element.attr("bgcolor").trim()
    if (attribute.isNotEmpty()) return attribute
    val style = element.attr("style")
    if (style.isEmpty()) return null
    return BACKGROUND_SOURCE.findAll(style).firstNotNullOfOrNull { match ->
        COLOR_TOKEN.find(match.groupValues[1].ifEmpty { match.groupValues[2] })?.value
    }
}

internal fun has_image_background(element: org.jsoup.nodes.Element): Boolean =
    element.attr("background").isNotBlank() ||
        element.hasAttr(BACKGROUND_IMAGE_ATTRIBUTE) ||
        declares_background_image(element.attr("style"))

private fun mark_image_paper(doc: org.jsoup.nodes.Document): Boolean {
    var changed = false
    for (element in doc.select("[style],[background],[$BACKGROUND_IMAGE_ATTRIBUTE]")) {
        if (element.hasAttr(PAPER_ATTRIBUTE) || !has_image_background(element)) continue
        if (inherits_declared_text_color(element)) continue
        val solid = solid_background_token(element)
        val solid_luminance = solid?.let { color_luminance(it) }
        if (solid_luminance != null && solid_luminance < PAGE_SURFACE_LUMINANCE_LIMIT) continue
        element.attr(PAPER_ATTRIBUTE, if (solid_luminance == null) PAPER_FILL_VALUE else "1")
        changed = true
    }
    return changed
}

private fun on_paper(element: org.jsoup.nodes.Element): Boolean =
    element.closest("[$PAPER_ATTRIBUTE]") != null

internal fun image_paper_css(): String {
    val plain_link = "[$PAPER_ATTRIBUTE] a:not([style*=\"color\" i]):not([color])"
    return "[$PAPER_ATTRIBUTE]{color:$PAPER_INK!important}" +
        "[$PAPER_ATTRIBUTE=\"$PAPER_FILL_VALUE\"]{background-color:$PAPER_FILL!important}" +
        "$plain_link,$plain_link *{color:$PAPER_LINK!important}"
}

private fun mark_stylesheet_background_images(doc: org.jsoup.nodes.Document): Boolean {
    val groups = doc.select("style").asSequence().flatMap { sheet ->
        STYLE_RULE.findAll(sheet.data())
            .filter { declares_background_image(it.groupValues[2]) }
            .map { it.groupValues[1] }
    }
    var marked = false
    for (selector in mail_selectors(groups)) {
        val matches = try {
            doc.body().select(selector)
        } catch (_: org.jsoup.select.Selector.SelectorParseException) {
            continue
        }
        for (element in matches) {
            element.attr(BACKGROUND_IMAGE_ATTRIBUTE, "1")
            marked = true
        }
    }
    return marked
}

private fun lighten_style_blocks(
    doc: org.jsoup.nodes.Document,
    rewrite: (String) -> String = ::lighten_color_declarations,
): Boolean {
    var changed = false
    for (element in doc.select("style")) {
        val css = element.data()
        val lightened = STYLE_RULE_BODY.replace(css) { rule ->
            "{" + rewrite(rule.groupValues[1]) + "}"
        }
        if (lightened != css) {
            element.empty()
            element.appendChild(org.jsoup.nodes.DataNode(lightened))
            changed = true
        }
    }
    return changed
}

internal fun lighten_dark_stylesheet_text(body: String): String = try {
    val doc = org.jsoup.Jsoup.parseBodyFragment(body)
    doc.outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
    var changed = false
    if (mark_stylesheet_background_images(doc)) changed = true
    if (mark_image_paper(doc)) changed = true
    if (lighten_style_blocks(doc)) changed = true
    if (changed) doc.body().html() else body
} catch (_: Throwable) {
    body
}

internal fun lighten_dark_email_text(body: String): String = try {
    val doc = org.jsoup.Jsoup.parseBodyFragment(body)
    doc.outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
    var changed = false
    for (element in doc.select("[style],[background]")) {
        if (element.attr("background").isBlank() && !declares_background_image(element.attr("style"))) continue
        element.attr(BACKGROUND_IMAGE_ATTRIBUTE, "1")
        changed = true
    }
    if (mark_stylesheet_background_images(doc)) changed = true
    if (mark_image_paper(doc)) changed = true
    for (element in doc.select("[bgcolor],[style]")) {
        val background = declared_background(element) ?: continue
        if (color_luminance(background) == null || is_page_surface(background)) continue
        element.attr(KEEP_BACKGROUND_ATTRIBUTE, "1")
        for (link in element.select("a")) link.attr(KEEP_BACKGROUND_ATTRIBUTE, "1")
        changed = true
    }
    for (element in doc.select("[style]")) {
        if (element.hasAttr(KEEP_BACKGROUND_ATTRIBUTE) || element.hasAttr(BACKGROUND_IMAGE_ATTRIBUTE)) continue
        if (on_paper(element)) continue
        val style = element.attr("style")
        val lightened = clear_light_backgrounds(lighten_color_declarations(style))
        if (lightened != style) {
            element.attr("style", lightened)
            changed = true
        }
    }
    if (lighten_style_blocks(doc) { clear_light_backgrounds(lighten_color_declarations(it)) }) changed = true
    for (element in doc.select("font[color]")) {
        if (on_paper(element)) continue
        if (reads_too_dark_on_dark(element.attr("color"))) {
            element.attr("color", FORCED_DARK_INK)
            changed = true
        }
    }
    if (changed) doc.body().html() else body
} catch (_: Throwable) {
    body
}

internal const val DARK_PAGE_SELECTOR = "html:root,html:root>body"

private const val KEEPS_BACKGROUND =
    ":not([style*=\"url(\" i]):not([background]):not([$BACKGROUND_IMAGE_ATTRIBUTE])" +
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
$DARK_PAGE_SELECTOR{background:transparent!important;color:$FORCED_DARK_INK!important}
$neutralized{background-color:transparent!important;background-image:none!important}
$readable_links{color:$link_hex!important}
a[style*="background" i] *,[bgcolor] > a *{color:inherit!important}
img{opacity:0.87}
hr{border-color:#374151!important;background-color:#374151!important;color:#374151!important}
blockquote{border-left-color:$quote_border!important;color:$quote_color!important}
${image_paper_css()}
"""
}

internal const val INK_ATTRIBUTE = "data-aster-ink"

internal const val INK_LINK_ATTRIBUTE = "data-aster-ink-link"

internal const val INK_DARK = "dark"

internal const val INK_LIGHT = "light"

internal const val LIGHT_INK_LINK = "#8ab4f8"

internal const val MIN_TEXT_CONTRAST = 2.5

internal const val DARK_PAGE_SURFACE = "#1a1a1a"

private const val INK_SWITCH_LUMINANCE = 0.179

private const val MAX_CONTRAST_RULES = 400

private val INHERITED_BACKGROUND_VALUES = setOf("", "transparent", "none", "inherit", "initial", "unset", "revert", "currentcolor")

private val CONTRAST_SKIPPED_TAGS = setOf(
    "style", "script", "head", "meta", "link", "title", "img", "br", "hr", "svg", "picture",
    "source", "video", "audio", "iframe", "object", "embed", "input", "textarea", "select",
)

internal fun contrast_ratio(first: Double, second: Double): Double =
    (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)

internal fun keeps_readable_contrast(background: String, ink: String): Boolean {
    val surface = color_luminance(background) ?: return true
    val text = color_luminance(ink) ?: return true
    return contrast_ratio(surface, text) >= MIN_TEXT_CONTRAST
}

internal fun text_contrast_css(): String =
    "#m [$INK_ATTRIBUTE=\"$INK_DARK\"]{color:$PAPER_INK!important}" +
        "#m [$INK_ATTRIBUTE=\"$INK_LIGHT\"]{color:$FORCED_DARK_INK!important}" +
        "#m a[$INK_LINK_ATTRIBUTE=\"$INK_DARK\"],#m a[$INK_LINK_ATTRIBUTE=\"$INK_DARK\"] *{color:$PAPER_LINK!important}" +
        "#m a[$INK_LINK_ATTRIBUTE=\"$INK_LIGHT\"],#m a[$INK_LINK_ATTRIBUTE=\"$INK_LIGHT\"] *{color:$LIGHT_INK_LINK!important}"

private sealed interface surface_reading {
    data object inherited : surface_reading
    data object unknown : surface_reading
    data class solid(val luminance: Double) : surface_reading
}

private fun read_surface(raw: String): surface_reading {
    val bare = IMPORTANT_FLAG.replace(raw, "").trim()
    if (bare.lowercase() in INHERITED_BACKGROUND_VALUES) return surface_reading.inherited
    if (declares_background_image("background:$bare")) return surface_reading.unknown
    if (is_translucent(bare)) return surface_reading.inherited
    color_luminance(bare)?.let { return surface_reading.solid(it) }
    val tokens = COLOR_TOKEN.findAll(bare).map { it.value }.filterNot(::is_translucent).mapNotNull(::color_luminance).toList()
    return if (tokens.isEmpty()) surface_reading.unknown else surface_reading.solid(tokens.average())
}

private fun read_ink(raw: String): Double? {
    val bare = IMPORTANT_FLAG.replace(raw, "").trim()
    if (is_translucent(bare)) return null
    return color_luminance(bare)
}

private fun inline_background_value(element: org.jsoup.nodes.Element): String? {
    val style = element.attr("style")
    val declared = if (style.isEmpty()) null else BACKGROUND_COLOR_DECLARATION.findAll(style).lastOrNull()?.groupValues?.get(1)
    return declared ?: element.attr("bgcolor").trim().ifEmpty { null }
}

private fun inline_ink_value(element: org.jsoup.nodes.Element): String? {
    val style = element.attr("style")
    val declared = if (style.isEmpty()) null else COLOR_DECLARATION.findAll(style).lastOrNull()?.groupValues?.get(1)
    if (declared != null) return declared
    return if (element.tagName() == "font") element.attr("color").trim().ifEmpty { null } else null
}

private class sheet_hints(
    val backgrounds: Map<org.jsoup.nodes.Element, String>,
    val inks: Map<org.jsoup.nodes.Element, String>,
    val images: Set<org.jsoup.nodes.Element>,
)

private fun collect_sheet_hints(doc: org.jsoup.nodes.Document, honors_backgrounds: Boolean): sheet_hints {
    val backgrounds = HashMap<org.jsoup.nodes.Element, String>()
    val inks = HashMap<org.jsoup.nodes.Element, String>()
    val images = HashSet<org.jsoup.nodes.Element>()
    var budget = MAX_CONTRAST_RULES
    var selector_budget = MAIL_SELECTOR_MAX_COUNT
    for (sheet in doc.select("style")) {
        for (rule in STYLE_RULE.findAll(sheet.data())) {
            if (budget-- <= 0) return sheet_hints(backgrounds, inks, images)
            val declarations = rule.groupValues[2]
            val image = honors_backgrounds && declares_background_image(declarations)
            val background = if (!honors_backgrounds || image) {
                null
            } else {
                BACKGROUND_COLOR_DECLARATION.findAll(declarations).lastOrNull()?.groupValues?.get(1)
            }
            val ink = COLOR_DECLARATION.findAll(declarations).lastOrNull()?.groupValues?.get(1)
            if (!image && background == null && ink == null) continue
            for (part in rule.groupValues[1].split(',')) {
                val selector = part.trim()
                if (!is_simple_mail_selector(selector)) continue
                if (selector_budget-- <= 0) return sheet_hints(backgrounds, inks, images)
                val matches = try {
                    doc.body().select(selector)
                } catch (_: org.jsoup.select.Selector.SelectorParseException) {
                    continue
                }
                for (element in matches) {
                    if (image) images.add(element)
                    if (background != null) backgrounds[element] = background
                    if (ink != null) inks[element] = ink
                }
            }
        }
    }
    return sheet_hints(backgrounds, inks, images)
}

private fun ink_for(surface: Double): String = if (surface > INK_SWITCH_LUMINANCE) INK_DARK else INK_LIGHT

private fun ink_luminance(ink: String): Double =
    color_luminance(if (ink == INK_DARK) PAPER_INK else FORCED_DARK_INK) ?: 0.0

private fun drop_important_ink(element: org.jsoup.nodes.Element) {
    val style = element.attr("style")
    if (style.isEmpty()) return
    val relaxed = COLOR_DECLARATION.replace(style) { match ->
        "color:" + IMPORTANT_FLAG.replace(match.groupValues[1], "").trim()
    }
    if (relaxed != style) element.attr("style", relaxed)
}

private class contrast_frame(val element: org.jsoup.nodes.Element, val surface: Double?, val ink: Double?)

internal fun enforce_text_contrast(
    body: String,
    page_background: String,
    page_ink: String,
    link_color: String,
    forced_dark: Boolean,
): String = try {
    val page_surface = color_luminance(page_background)
    val page_text = color_luminance(page_ink)
    val link_text = color_luminance(link_color)
    if (page_surface == null || page_text == null) {
        body
    } else {
        val doc = org.jsoup.Jsoup.parseBodyFragment(body)
        doc.outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
        val hints = collect_sheet_hints(doc, honors_backgrounds = !forced_dark)
        var changed = false
        val stack = ArrayDeque<contrast_frame>()
        for (child in doc.body().children().reversed()) stack.addLast(contrast_frame(child, page_surface, page_text))
        while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            val element = frame.element
            if (element.tagName() in CONTRAST_SKIPPED_TAGS || element.hasAttr(PAPER_ATTRIBUTE)) continue
            val honors_inline = !forced_dark || element.hasAttr(KEEP_BACKGROUND_ATTRIBUTE)
            val image = !forced_dark && (
                element.hasAttr(BACKGROUND_IMAGE_ATTRIBUTE) || has_image_background(element) || element in hints.images
                )
            val declared_surface = (if (honors_inline) inline_background_value(element) else null) ?: hints.backgrounds[element]
            val reading = when {
                image -> surface_reading.unknown
                declared_surface == null -> surface_reading.inherited
                else -> read_surface(declared_surface)
            }
            val surface = when (reading) {
                surface_reading.inherited -> frame.surface
                surface_reading.unknown -> null
                is surface_reading.solid -> reading.luminance
            }
            val inline_ink = inline_ink_value(element)
            val declared_ink = inline_ink ?: hints.inks[element]
            var text = declared_ink?.let(::read_ink) ?: frame.ink
            if (surface != null && text != null && contrast_ratio(surface, text) < MIN_TEXT_CONTRAST) {
                val ink = ink_for(surface)
                element.attr(INK_ATTRIBUTE, ink)
                if (inline_ink != null) drop_important_ink(element)
                text = ink_luminance(ink)
                changed = true
            } else if (element.tagName() == "a" && declared_ink == null && surface != null && link_text != null &&
                contrast_ratio(surface, link_text) < MIN_TEXT_CONTRAST
            ) {
                element.attr(INK_LINK_ATTRIBUTE, ink_for(surface))
                changed = true
            }
            for (child in element.children().reversed()) stack.addLast(contrast_frame(child, surface, text))
        }
        if (changed) doc.body().html() else body
    }
} catch (_: Throwable) {
    body
}
