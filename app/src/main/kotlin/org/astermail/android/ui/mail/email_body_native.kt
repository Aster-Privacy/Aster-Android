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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal const val FAILED_IMAGE_LABEL_ATTRIBUTE = "data-aster-failed-label"
internal const val ZOOM_LINK_SCHEME = "asterimg:"
internal const val QUOTE_TOGGLE_LABEL = "•••"

private val INLINE_TAGS = setOf(
    "a", "abbr", "b", "bdi", "bdo", "big", "cite", "code", "em", "font", "i", "kbd",
    "label", "mark", "nobr", "q", "s", "samp", "small", "span", "strike", "strong",
    "sub", "sup", "time", "tt", "u", "var", "wbr",
)

private val NON_CONTENT_TAGS = setOf("script", "style", "template")

private val LINKIFY_SKIP_TAGS = setOf("a", "script", "style", "textarea", "code", "pre", "button")

private val LONG_TOKEN_SKIP_TAGS = setOf("textarea", "code", "pre", "kbd", "samp")

private val LONG_TOKEN = Regex("""\S{30,}""")

private val MEDIA_TAGS = setOf("img", "video", "picture")

internal const val WRAP_BUTTON_LABEL_ATTRIBUTE = "data-aster-wrap-label"

private const val WRAP_BUTTON_LABEL_WIDTH = 296f

private const val DEFAULT_FONT_PX = 16f

private const val LABEL_CHARACTER_EM = 0.62f

private val INLINE_FONT_SIZE =
    Regex("""(?:^|;)\s*font-size\s*:\s*(\d+(?:\.\d+)?)\s*(px|pt|em|rem|%)""", RegexOption.IGNORE_CASE)

private val BLANK_SPACER_TAGS = setOf("div", "p", "span")

private const val SPACER_CONTENT = "img, hr, table, video, audio, iframe, object"

private val QUICK_LINK_HINT = Regex("://|www\\.|@", RegexOption.IGNORE_CASE)

private val LINK_CANDIDATE = Regex(
    "(?<![\\w@/.-])(?:(https?://|www\\.)[^\\s<>\"'`]+|([A-Za-z0-9._%+-]+@[A-Za-z0-9]" +
        "(?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}))",
    RegexOption.IGNORE_CASE,
)

private val TRAILING_PUNCTUATION = setOf('.', ',', ':', ';', '!', '?', '\'', '"', '*', '_', '~')

private val BRACKET_OPENERS = mapOf(')' to '(', ']' to '[', '}' to '{')

private val HTML_ENTITY_TAIL = Regex("&#?[A-Za-z0-9]+;$")

private val WATERMARK_MARKER = Regex("(^|[\\s> ])(Secured by Aster Mail)", RegexOption.IGNORE_CASE)

private val QUOTE_SIGNATURE = Regex(
    "wrote\\s*:|-{3,}\\s*(?:Original|Forwarded)|Forwarded message|^\\s*>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)

private val WHITESPACE_RUN = Regex("\\s+")

private val INLINE_HEIGHT = Regex("(^|;)\\s*height\\s*:\\s*([0-9.]+)px", RegexOption.IGNORE_CASE)

private val INLINE_NEGATIVE_MARGIN = Regex(
    "(^|;)\\s*margin-(left|right)\\s*:\\s*-[0-9.]+(?:px|em|rem|%)",
    RegexOption.IGNORE_CASE,
)

private val INLINE_COLOR = Regex("(^|;)\\s*color\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)

private val INLINE_BACKGROUND = Regex("(^|;)\\s*background(?:-color|-image)?\\s*:", RegexOption.IGNORE_CASE)

private val HEX_COLOR = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

private val RGB_COLOR = Regex("^rgba?\\(([^)]+)\\)$", RegexOption.IGNORE_CASE)

internal const val MIN_FIT_CONTENT_WIDTH = 600

internal const val MAX_FIT_CONTENT_WIDTH = 1600

private val STYLE_PIXEL_WIDTH =
    Regex("""(?<![a-z-])width\s*:\s*(\d{3,4})(?:\.\d+)?px""", RegexOption.IGNORE_CASE)

private val PIXEL_WIDTH_ATTRIBUTE = Regex("""^(\d{3,4})(?:px)?$""", RegexOption.IGNORE_CASE)

private val UNSIZED_FIT_TAGS = setOf("img", "video", "iframe", "hr", "canvas")

private val STYLE_FLUID_MAX_WIDTH =
    Regex("""(?<![a-z-])max-width\s*:\s*(?:\d{1,3}(?:\.\d+)?%|\d{1,3}(?:\.\d+)?vw)""", RegexOption.IGNORE_CASE)

internal fun declared_content_width(body: String): Int? = try {
    val doc = Jsoup.parseBodyFragment(body).apply { outputSettings(raw_body_output_settings()) }
    var widest = 0
    for (element in doc.body().select("*")) {
        if (element.tagName().lowercase() in UNSIZED_FIT_TAGS) continue
        val style = element.attr("style")
        val from_style = if (STYLE_FLUID_MAX_WIDTH.containsMatchIn(style)) {
            0
        } else {
            STYLE_PIXEL_WIDTH.find(style)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        val from_attribute =
            PIXEL_WIDTH_ATTRIBUTE.find(element.attr("width").trim())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val declared = maxOf(from_style, from_attribute)
        if (declared > widest) widest = declared
    }
    if (widest < MIN_FIT_CONTENT_WIDTH) null else minOf(widest, MAX_FIT_CONTENT_WIDTH)
} catch (_: Throwable) {
    null
}

private val TRANSLATION_SKIP_TAGS =
    setOf("script", "style", "noscript", "textarea", "pre", "code", "kbd", "samp", "var", "tt")

private val TRANSLATION_SKIP_CLASSES =
    setOf("aster-quoted-content", "aster-quote-toggle", "aster-forwarded-collapse", "notranslate")

private const val MIN_TRANSLATABLE_LENGTH = 2

private val NON_LINGUISTIC = Regex("^[\\s\\d\\p{Punct}]*$")

private val BACKGROUND_COLOR_VALUE =
    Regex("(^|;)\\s*background(?:-color)?\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)

private val SAFE_COLOR_VALUE =
    Regex("^(?:#[0-9a-fA-F]{3,8}|[a-zA-Z]{3,20}|rgba?\\([0-9,.%\\s]+\\))$")

private val ZOOMABLE_IMAGE_SOURCE = Regex("^(?:https?:|data:image/|cid:)", RegexOption.IGNORE_CASE)

internal fun raw_body_output_settings(): Document.OutputSettings =
    Document.OutputSettings().prettyPrint(false)

internal fun prepare_email_body(
    body: String,
    forwarded_label: String,
    image_failed_label: String,
    is_newsletter: Boolean,
    simple_dark: Boolean,
): String = try {
    val doc = Jsoup.parseBodyFragment(body).apply { outputSettings(raw_body_output_settings()) }
    val root = doc.body()
    linkify_text_nodes(root)
    if (!is_newsletter) mark_long_tokens(root)
    mark_wide_button_labels(root)
    relax_fixed_heights(root)
    if (is_newsletter) pad_loose_blocks(root)
    if (simple_dark) repair_dark_text_contrast(root)
    prune_empty_signature_blocks(root)
    reveal_fully_hidden_content(root)
    collapse_quoted_content(root, forwarded_label)
    if (simple_dark) BlockedImagePlaceholder.repaint(root, BlockedImagePlaceholder.DARK)
    wrap_images_for_zoom(root)
    label_images_for_failure(root, image_failed_label)
    trim_leading_blank_nodes(root)
    trim_trailing_blank_nodes(root)
    root.html()
} catch (_: Throwable) {
    "<div class=\"aster-quoted-content aster-quoted-solo\">$body</div>"
}

private const val RENDERABLE_SCAN_LIMIT = 32768

private val PIXEL_DIMENSION = Regex("^[01](?:px)?$")

private val INVISIBLE_CHARACTERS = Regex("[\u200B-\u200D\uFEFF]")

private fun is_tracking_pixel(image: Element): Boolean =
    PIXEL_DIMENSION.matches(image.attr("width").trim()) || PIXEL_DIMENSION.matches(image.attr("height").trim())

internal fun html_has_renderable_content(html: String): Boolean = try {
    if (html.length > RENDERABLE_SCAN_LIMIT) {
        true
    } else {
        val root = Jsoup.parseBodyFragment(html).body()
        root.select("script, style, template, head, title, noscript").remove()
        root.text().replace(INVISIBLE_CHARACTERS, "").isNotBlank() ||
            root.selectFirst("svg, video, canvas, picture, hr") != null ||
            root.select("img").any { !is_tracking_pixel(it) }
    }
} catch (_: Throwable) {
    true
}

internal fun renderable_html_part(body_html: String?, body: String): String? {
    if (body_html.isNullOrBlank()) return null
    if (body.isBlank() || body == body_html) return body_html
    return if (html_has_renderable_content(body_html)) body_html else null
}

private val HIDING_DECLARATION = Regex(
    """(?<![a-z-])(?:display\s*:\s*none|visibility\s*:\s*(?:hidden|collapse)|""" +
        """opacity\s*:\s*0(?:\.0+)?(?![.\d])|""" +
        """(?:font-size|max-height)\s*:\s*0(?:\.0+)?(?:px|pt|em|rem|%)?(?![.\d]))""" +
        """\s*(?:!important)?\s*;?""",
    RegexOption.IGNORE_CASE,
)

private val SUBTREE_HIDING_DECLARATION = Regex(
    """(?<![a-z-])(?:display\s*:\s*none|visibility\s*:\s*(?:hidden|collapse)|""" +
        """opacity\s*:\s*0(?:\.0+)?(?![.\d])|""" +
        """max-height\s*:\s*0(?:\.0+)?(?:px|pt|em|rem|%)?(?![.\d]))""",
    RegexOption.IGNORE_CASE,
)

private val ZERO_FONT_SIZE =
    Regex("""(?<![a-z-])font-size\s*:\s*0(?:\.0+)?(?:px|pt|em|rem|%)?(?![.\d])""", RegexOption.IGNORE_CASE)

private val FONT_SIZE_DECLARATION = Regex("""(?<![a-z-])font-size\s*:""", RegexOption.IGNORE_CASE)

private val CSS_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

private val CSS_RULE = Regex("""([^{}@]+)\{([^{}]*)\}""")

private const val STYLE_REVEAL =
    "display:revert !important;visibility:visible !important;opacity:1 !important;" +
        "max-height:none !important;font-size:inherit !important"

private fun strip_nested_blocks(css: String): String {
    val out = StringBuilder()
    var depth = 0
    var at_rule = false
    var i = 0
    while (i < css.length) {
        val c = css[i]
        when {
            c == '@' && depth == 0 -> {
                at_rule = true
            }
            c == '{' -> {
                depth++
                if (!at_rule && depth == 1) out.append(c)
            }
            c == '}' -> {
                if (!at_rule && depth == 1) out.append(c)
                depth = (depth - 1).coerceAtLeast(0)
                if (depth == 0) at_rule = false
            }
            c == ';' && at_rule && depth == 0 -> at_rule = false
            !at_rule -> out.append(c)
        }
        i++
    }
    return out.toString()
}

private fun stylesheet_hidden_elements(root: Element): List<Element> {
    val css = root.select("style").joinToString(" ") { it.data() }
    if (css.isBlank()) return emptyList()
    val flat = strip_nested_blocks(CSS_COMMENT.replace(css, ""))
    return CSS_RULE.findAll(flat)
        .filter { HIDING_DECLARATION.containsMatchIn(it.groupValues[2]) }
        .flatMap { rule ->
            rule.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.asSequence()
        }
        .flatMap { selector -> runCatching { root.select(selector).toList() }.getOrDefault(emptyList()).asSequence() }
        .filter { it.tagName().lowercase() != "style" }
        .distinct()
        .toList()
}

private fun is_hidden(node: Node, sheet_hidden: List<Element>, is_text: Boolean): Boolean {
    var sized = false
    var current: Node? = node
    while (current != null) {
        if (current is Element) {
            if (sheet_hidden.any { it === current }) return true
            val style = current.attr("style")
            if (SUBTREE_HIDING_DECLARATION.containsMatchIn(style)) return true
            if (ZERO_FONT_SIZE.containsMatchIn(style)) {
                if (is_text && !sized) return true
            } else if (FONT_SIZE_DECLARATION.containsMatchIn(style)) {
                sized = true
            }
        }
        current = current.parentNode()
    }
    return false
}

private fun reveal_fully_hidden_content(root: Element) {
    val inline_hidden = root.select("[style]").filter { HIDING_DECLARATION.containsMatchIn(it.attr("style")) }
    val sheet_hidden = stylesheet_hidden_elements(root)
    val hidden = (inline_hidden + sheet_hidden).distinct()
    if (hidden.isEmpty()) return
    val texts = collect_text_nodes(root).filter { it.wholeText.replace(INVISIBLE_CHARACTERS, "").isNotBlank() }
    if (texts.isEmpty()) return
    if (texts.any { !is_hidden(it, sheet_hidden, is_text = true) }) return
    val visible_media = root.select("img, video, picture, svg, canvas")
        .any { !is_hidden(it, sheet_hidden, is_text = false) && !(it.tagName().lowercase() == "img" && is_tracking_pixel(it)) }
    if (visible_media) return
    for (element in inline_hidden) {
        val style = HIDING_DECLARATION.replace(element.attr("style"), "").trim()
        if (style.isEmpty()) element.removeAttr("style") else element.attr("style", style)
    }
    for (element in sheet_hidden) {
        val style = element.attr("style").trim().trimEnd(';')
        element.attr("style", if (style.isEmpty()) STYLE_REVEAL else "$style;$STYLE_REVEAL")
    }
}

internal data class detected_link(val start: Int, val end: Int, val text: String, val href: String)

private fun count_char(value: String, needle: Char): Int = value.count { it == needle }

private fun trim_url_tail(raw: String): String {
    var url = raw
    while (url.isNotEmpty()) {
        val last = url[url.length - 1]
        if (last == ';') {
            val entity = HTML_ENTITY_TAIL.find(url)
            url = if (entity != null) url.substring(0, entity.range.first) else url.dropLast(1)
            continue
        }
        if (last in TRAILING_PUNCTUATION) {
            url = url.dropLast(1)
            continue
        }
        val opener = BRACKET_OPENERS[last]
        if (opener != null && count_char(url, last) > count_char(url, opener)) {
            url = url.dropLast(1)
            continue
        }
        break
    }
    return url
}

private fun has_host(url: String, offset: Int): Boolean {
    if (offset >= url.length) return false
    val host = url.substring(offset).split('/', '?', '#')[0]
    if (host.isEmpty()) return false
    if (host.first() == '.' || host.last() == '.') return false
    if (host.contains("..")) return false
    return host.any { it.isLetterOrDigit() }
}

private fun trim_email_tail(address: String): String {
    val at = address.lastIndexOf('@')
    if (at < 0) return address
    val domain = address.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    if (dot < 0) return address
    var tld = domain.substring(dot + 1)
    val seam = Regex("[a-z][A-Z]").find(tld)?.range?.first ?: -1
    if (seam >= 0) tld = tld.substring(0, seam + 1)
    if (tld.length > 24) tld = tld.substring(0, 24)
    return address.substring(0, at + 1) + domain.substring(0, dot + 1) + tld
}

internal fun find_links(value: String): List<detected_link> {
    if (value.isEmpty() || !QUICK_LINK_HINT.containsMatchIn(value)) return emptyList()
    val out = mutableListOf<detected_link>()
    var cursor = 0
    while (cursor <= value.length) {
        val match = LINK_CANDIDATE.find(value, cursor) ?: break
        val start = match.range.first
        val prefix = match.groupValues[1]
        if (prefix.isNotEmpty()) {
            val url = trim_url_tail(match.value)
            val is_www = prefix.equals("www.", ignoreCase = true)
            if (url.length > prefix.length && has_host(url, prefix.length) &&
                (!is_www || url.substring(prefix.length).contains('.'))
            ) {
                out.add(
                    detected_link(
                        start = start,
                        end = start + url.length,
                        text = url,
                        href = if (is_www) "http://$url" else url,
                    ),
                )
            }
            cursor = start + maxOf(url.length, 1)
            continue
        }
        val address = trim_email_tail(match.groupValues[2].ifEmpty { match.value })
        out.add(
            detected_link(
                start = start,
                end = start + address.length,
                text = address,
                href = "mailto:$address",
            ),
        )
        cursor = start + maxOf(address.length, 1)
    }
    return out
}

private fun collect_text_nodes(root: Element): List<TextNode> {
    val out = mutableListOf<TextNode>()
    fun walk(node: Node) {
        for (child in node.childNodes().toList()) {
            when {
                child is TextNode -> out.add(child)
                child is Element && child.tagName().lowercase() in NON_CONTENT_TAGS -> Unit
                else -> walk(child)
            }
        }
    }
    walk(root)
    return out
}

private fun linkify_skipped(node: Node, root: Element): Boolean = inside_tags(node, root, LINKIFY_SKIP_TAGS)

private fun inside_tags(node: Node, root: Element, tags: Set<String>): Boolean {
    var parent = node.parentNode()
    while (parent != null && parent !== root) {
        if (parent is Element && parent.tagName().lowercase() in tags) return true
        parent = parent.parentNode()
    }
    return false
}

private fun linkify_text_nodes(root: Element) {
    val targets = collect_text_nodes(root).filter {
        QUICK_LINK_HINT.containsMatchIn(it.wholeText) && !linkify_skipped(it, root)
    }
    for (node in targets) {
        val source = node.wholeText
        val links = find_links(source)
        if (links.isEmpty()) continue
        val parent = node.parentNode() as? Element ?: continue
        val index = node.siblingIndex()
        val replacements = mutableListOf<Node>()
        var last = 0
        for (link in links) {
            if (link.start > last) replacements.add(TextNode(source.substring(last, link.start)))
            val anchor = Element("a")
            anchor.attr("href", link.href)
            anchor.appendChild(TextNode(link.text))
            replacements.add(anchor)
            last = link.end
        }
        if (last < source.length) replacements.add(TextNode(source.substring(last)))
        node.remove()
        parent.insertChildren(index, replacements)
    }
}

private fun mark_long_tokens(root: Element) {
    val targets = collect_text_nodes(root).filter {
        LONG_TOKEN.containsMatchIn(it.wholeText) && !inside_tags(it, root, LONG_TOKEN_SKIP_TAGS)
    }
    for (node in targets) {
        val source = node.wholeText
        val parent = node.parentNode() as? Element ?: continue
        val index = node.siblingIndex()
        val replacements = mutableListOf<Node>()
        var last = 0
        for (token in LONG_TOKEN.findAll(source)) {
            if (token.range.first > last) replacements.add(TextNode(source.substring(last, token.range.first)))
            val span = Element("span")
            span.attr("data-aster-long-token", "")
            span.appendChild(TextNode(token.value))
            replacements.add(span)
            last = token.range.last + 1
        }
        if (last < source.length) replacements.add(TextNode(source.substring(last)))
        node.remove()
        parent.insertChildren(index, replacements)
    }
}

private fun inline_font_px(element: Element?): Float {
    if (element == null) return DEFAULT_FONT_PX
    val match = INLINE_FONT_SIZE.findAll(element.attr("style")).lastOrNull()
        ?: return inline_font_px(element.parent())
    val value = match.groupValues[1].toFloatOrNull() ?: return inline_font_px(element.parent())
    return when (match.groupValues[2].lowercase()) {
        "px" -> value
        "pt" -> value * 4f / 3f
        "rem" -> value * DEFAULT_FONT_PX
        "em" -> value * inline_font_px(element.parent())
        else -> value / 100f * inline_font_px(element.parent())
    }
}

private fun estimated_label_width(anchor: Element): Float =
    anchor.text().trim().length * inline_font_px(anchor) * LABEL_CHARACTER_EM

private fun mark_wide_button_labels(root: Element) {
    for (anchor in root.select("a.aster-email-button")) {
        if (estimated_label_width(anchor) > WRAP_BUTTON_LABEL_WIDTH) anchor.attr(WRAP_BUTTON_LABEL_ATTRIBUTE, "")
    }
}

private fun relax_fixed_heights(root: Element) {
    for (element in root.select("td,th,tr,div,p,span,a,table")) {
        val style = element.attr("style")
        if (style.isEmpty()) continue
        val match = INLINE_HEIGHT.find(style) ?: continue
        if (element.text().isBlank()) continue
        val pixels = match.groupValues[2].toFloatOrNull() ?: continue
        if (pixels <= 0f) continue
        element.attr(
            "style",
            style.replaceRange(
                match.range,
                "${match.groupValues[1]}height:auto;min-height:${Math.round(pixels)}px",
            ),
        )
    }
}

private fun pad_loose_blocks(root: Element) {
    for (element in root.children()) {
        val style = element.attr("style")
        if (style.isEmpty()) continue
        if (!INLINE_NEGATIVE_MARGIN.containsMatchIn(style)) continue
        if (element.tagName().equals("table", ignoreCase = true)) continue
        if (element.selectFirst("table") != null) continue
        element.attr(
            "style",
            INLINE_NEGATIVE_MARGIN.replace(style) { "${it.groupValues[1]}margin-${it.groupValues[2]}:0" },
        )
    }
}

internal fun relative_luminance(color: String): Double? {
    val value = color.trim()
    val channels = when {
        HEX_COLOR.matches(value) -> {
            val hex = value.substring(1)
            val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
            listOf(
                full.substring(0, 2).toInt(16),
                full.substring(2, 4).toInt(16),
                full.substring(4, 6).toInt(16),
            )
        }
        RGB_COLOR.matches(value) -> {
            val parts = RGB_COLOR.find(value)!!.groupValues[1].split(',').map { it.trim() }
            if (parts.size < 3) return null
            val alpha = if (parts.size > 3) parts[3].toDoubleOrNull() ?: 1.0 else 1.0
            if (alpha < 0.55) return null
            listOf(
                parts[0].toDoubleOrNull()?.toInt() ?: return null,
                parts[1].toDoubleOrNull()?.toInt() ?: return null,
                parts[2].toDoubleOrNull()?.toInt() ?: return null,
            )
        }
        else -> return null
    }
    fun channel(raw: Int): Double {
        val scaled = raw.coerceIn(0, 255) / 255.0
        return if (scaled <= 0.03928) scaled / 12.92 else Math.pow((scaled + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(channels[0]) + 0.7152 * channel(channels[1]) + 0.0722 * channel(channels[2])
}

private fun declares_background(element: Element): Boolean {
    var node: Element? = element
    while (node != null) {
        if (INLINE_BACKGROUND.containsMatchIn(node.attr("style"))) return true
        if (node.hasAttr("bgcolor") || node.hasAttr("background")) return true
        node = node.parent()
    }
    return false
}

private fun contains_background(element: Element): Boolean {
    if (element.selectFirst("[bgcolor],[background]") != null) return true
    return element.select("[style]").any { it !== element && INLINE_BACKGROUND.containsMatchIn(it.attr("style")) }
}

private fun repair_dark_text_contrast(root: Element) {
    for (element in root.select("[style]")) {
        val style = element.attr("style")
        val match = INLINE_COLOR.find(style) ?: continue
        if (!element.hasText()) continue
        if (declares_background(element)) continue
        if (contains_background(element)) continue
        val luminance = relative_luminance(match.groupValues[2]) ?: continue
        if (luminance >= 0.35) continue
        element.attr("style", style.replaceRange(match.range, "${match.groupValues[1]}color:#e8e8e8"))
    }
}

private fun prune_empty_signature_blocks(root: Element) {
    for (block in root.select(".protonmail_signature_block-empty")) block.remove()
    for (block in root.select(".protonmail_signature_block")) {
        if (block.text().isBlank() && block.selectFirst("img") == null) block.remove()
    }
}

private fun wrap_images_for_zoom(root: Element) {
    for (image in root.select("img")) {
        if (image.attr("data-blocked") == "true") continue
        if (image.hasClass("blocked-image")) continue
        if (image.closest("a") != null) continue
        val source = image.attr("src")
        if (source.isEmpty() || !ZOOMABLE_IMAGE_SOURCE.containsMatchIn(source)) continue
        val parent = image.parent() ?: continue
        val anchor = Element("a")
        anchor.attr("href", ZOOM_LINK_SCHEME + java.net.URLEncoder.encode(source, "UTF-8"))
        anchor.attr("class", "aster-image-zoom")
        parent.insertChildren(image.siblingIndex(), listOf(anchor))
        image.remove()
        anchor.appendChild(image)
    }
}

private fun label_images_for_failure(root: Element, image_failed_label: String) {
    if (image_failed_label.isEmpty()) return
    for (image in root.select("img")) {
        if (image.attr("data-blocked") == "true") continue
        if (image.hasClass("blocked-image")) continue
        image.attr(FAILED_IMAGE_LABEL_ATTRIBUTE, image_failed_label)
    }
}

private fun is_blank_spacer(node: Node?): Boolean {
    if (node == null) return false
    if (node is TextNode) return node.wholeText.isBlank()
    if (node !is Element) return false
    val tag = node.tagName().lowercase()
    if (tag == "br") return true
    if (tag !in BLANK_SPACER_TAGS) return false
    if (node.text().isNotBlank()) return false
    return node.selectFirst(SPACER_CONTENT) == null
}

private fun trim_leading_blank_nodes(root: Element) {
    var first: Node? = root.childNodes().firstOrNull()
    while (first != null && is_blank_spacer(first)) {
        val removed = first
        first = removed.nextSibling()
        removed.remove()
    }
}

private fun trim_trailing_blank_nodes(root: Element) {
    var container: Element = root
    while (true) {
        var last: Node? = container.childNodes().lastOrNull()
        while (last != null && is_blank_spacer(last)) {
            val removed = last
            last = removed.previousSibling()
            removed.remove()
        }
        val element = last as? Element
        if (element != null && element.tagName().lowercase() in setOf("div", "p") &&
            element.selectFirst("details.aster-quoted-wrapper, details.aster-forwarded-collapse") == null &&
            !element.attr("class").contains("quote", ignoreCase = true) &&
            !element.attr("class").contains("cite", ignoreCase = true)
        ) {
            container = element
            continue
        }
        break
    }
}

private fun trim_blank_nodes_before(target: Node) {
    var previous = target.previousSibling()
    while (previous != null && is_blank_spacer(previous)) {
        val removed = previous
        previous = removed.previousSibling()
        removed.remove()
    }
    val element = previous as? Element ?: return
    if (element.tagName().lowercase() !in setOf("div", "p")) return
    if (element.selectFirst("details.aster-quoted-wrapper, details.aster-forwarded-collapse") != null) return
    trim_trailing_blank_nodes(element)
}

private fun quote_details(forwarded: Boolean, label: String): Element {
    val details = Element("details")
    details.attr("class", if (forwarded) "aster-forwarded-collapse" else "aster-quoted-wrapper")
    val summary = Element("summary")
    summary.attr("class", if (forwarded) "aster-forwarded-summary" else "aster-quote-toggle")
    summary.appendChild(TextNode(label))
    details.appendChild(summary)
    val content = Element("div")
    content.attr("class", if (forwarded) "aster-forwarded-content" else "aster-quoted-content")
    details.appendChild(content)
    return details
}

private fun quote_content(details: Element): Element = details.child(1)

private fun already_collapsed(root: Element): Boolean =
    root.selectFirst("details.aster-quoted-wrapper, details.aster-forwarded-collapse") != null

private fun hoist_trailing_signature(details: Element) {
    val parent = details.parent() ?: return
    val moved = mutableListOf<Node>()
    val pending = mutableListOf<Node>()
    var node = details.nextSibling()
    while (node != null) {
        val next = node.nextSibling()
        if (node is Element && (node.hasClass("aster_signature") || node.attr("data-aster-signature") == "1")) {
            moved.addAll(pending)
            pending.clear()
            moved.add(node)
        } else if (is_blank_spacer(node)) {
            pending.add(node)
        } else {
            break
        }
        node = next
    }
    if (moved.isEmpty()) return
    val index = details.siblingIndex()
    for (item in moved) item.remove()
    parent.insertChildren(index, moved)
}

private fun reveal_element(target: Element) {
    val parent = target.parent() ?: return
    val wrapper = Element("div")
    wrapper.attr("class", "aster-quoted-content aster-quoted-solo")
    parent.insertChildren(target.siblingIndex(), listOf(wrapper))
    target.remove()
    wrapper.appendChild(target)
}

private fun collapse_element(target: Element) {
    val parent = target.parent() ?: return
    val details = quote_details(forwarded = false, label = QUOTE_TOGGLE_LABEL)
    parent.insertChildren(target.siblingIndex(), listOf(details))
    target.remove()
    quote_content(details).appendChild(target)
    hoist_trailing_signature(details)
    trim_blank_nodes_before(details)
}

private fun is_within(node: Node, owners: List<Node>): Boolean {
    var current: Node? = node
    while (current != null) {
        if (owners.any { it === current }) return true
        current = current.parentNode()
    }
    return false
}

private val QUOTE_ROOT_SELECTOR =
    "div.aster_quote, div.gmail_quote, blockquote.gmail_quote, blockquote.aster_quote, " +
        "blockquote.protonmail_quote, div.yahoo_quoted, blockquote.yahoo_quoted"

private fun quote_is_whole_body(root: Element): Boolean {
    val quote = root.selectFirst(QUOTE_ROOT_SELECTOR) ?: return false
    if (root.text().length - quote.text().length > 4) return false
    return root.select("img").size <= quote.select("img").size
}

private val HIDDEN_QUOTE_SELECTOR =
    ".aster_quote, .gmail_quote, .protonmail_quote, .yahoo_quoted, .moz-cite-prefix"

private fun reveal_orphaned_quotes(root: Element) {
    for (element in root.select(HIDDEN_QUOTE_SELECTOR)) {
        if (element.closest(".aster-quoted-content, .aster-forwarded-content") == null) reveal_element(element)
    }
}

private fun collapse_quoted_content(root: Element, forwarded_label: String) {
    collapse_first_quote(root, forwarded_label)
    reveal_orphaned_quotes(root)
}

private fun collapse_first_quote(root: Element, forwarded_label: String) {
    collapse_proton_forward(root, forwarded_label)
    if (!already_collapsed(root) && quote_is_whole_body(root)) {
        root.selectFirst(QUOTE_ROOT_SELECTOR)?.let { reveal_element(it) }
        return
    }
    if (!already_collapsed(root)) {
        root.selectFirst("div.aster_quote, div.gmail_quote")?.let { collapse_element(it) }
    }
    if (!already_collapsed(root)) collapse_by_text_marker(root)
    if (!already_collapsed(root)) {
        root.selectFirst(
            "blockquote.gmail_quote, blockquote.aster_quote, blockquote.protonmail_quote, " +
                "div.yahoo_quoted, blockquote.yahoo_quoted",
        )?.let { collapse_element(it) }
    }
}

private fun collapse_proton_forward(root: Element, forwarded_label: String) {
    if (already_collapsed(root)) return
    val proton = root.select("div.protonmail_quote")
        .firstOrNull { it.closest("div.aster_quote, div.gmail_quote") == null } ?: return
    val meta = mutableListOf<Node>()
    var previous = proton.previousSibling()
    while (previous != null) {
        val element = previous as? Element
        val is_signature = element != null && element.hasClass("protonmail_signature_block")
        val has_media = element != null &&
            (element.tagName().lowercase() in MEDIA_TAGS || element.selectFirst("img, video, picture") != null)
        val is_empty = when (previous) {
            is TextNode -> previous.wholeText.isBlank()
            is Element -> previous.text().isBlank()
            else -> true
        }
        if (is_signature || (is_empty && !has_media)) {
            meta.add(0, previous)
            previous = previous.previousSibling()
        } else {
            break
        }
    }
    meta.add(proton)

    val outside_text = collect_text_nodes(root)
        .any { it.wholeText.isNotBlank() && !is_within(it, meta) }
    val outside_media = root.select("img, video, picture").any { !is_within(it, meta) }
    if (!outside_text && !outside_media) return

    val details = quote_details(forwarded = true, label = forwarded_label)
    val content = quote_content(details)
    root.appendChild(details)
    for (node in meta) {
        node.remove()
        content.appendChild(node)
    }
    trim_blank_nodes_before(details)
}

private fun block_ancestor(node: Node, root: Element): Node {
    var parent = node.parentNode()
    while (parent != null && parent !== root && parent is Element &&
        parent.tagName().lowercase() in INLINE_TAGS
    ) {
        parent = parent.parentNode()
    }
    return parent ?: root
}

private fun collapse_by_text_marker(root: Element) {
    collapse_by_watermark(root)
    if (!already_collapsed(root)) collapse_by_quote_lines(root)
}

private fun collapse_by_watermark(root: Element) {
    val runs = mutableListOf<Pair<TextNode, Int>>()
    val joined = StringBuilder()
    var previous_block: Node? = null
    var pending_break = false

    fun walk(node: Node) {
        for (child in node.childNodes().toList()) {
            when {
                child is Element && child.tagName().lowercase() in NON_CONTENT_TAGS -> Unit
                child is Element && child.tagName().lowercase() == "br" -> pending_break = true
                child is TextNode -> {
                    val block = block_ancestor(child, root)
                    if (previous_block != null && block !== previous_block) pending_break = true
                    if (pending_break) {
                        joined.append('\n')
                        pending_break = false
                    }
                    runs.add(child to joined.length)
                    joined.append(child.wholeText)
                    previous_block = block
                }
                else -> walk(child)
            }
        }
    }
    walk(root)

    val text = joined.toString()
    val watermark = WATERMARK_MARKER.find(text) ?: return
    val hit = watermark.range.first + watermark.groupValues[1].length

    var marker: TextNode? = null
    for ((node, start) in runs) {
        val length = node.wholeText.length
        if (hit < start) {
            marker = node
            break
        }
        if (hit < start + length) {
            val offset = hit - start
            marker = if (offset > 0) node.splitText(offset) else node
            break
        }
    }
    val anchor = marker ?: return

    val to_collect = mutableListOf<Node>()
    var cursor: Node? = anchor
    while (cursor != null) {
        val next = cursor.nextSibling()
        to_collect.add(cursor)
        cursor = next
    }
    var ancestor = anchor.parentNode()
    while (ancestor != null && ancestor !== root) {
        var sibling = ancestor.nextSibling()
        while (sibling != null) {
            val next = sibling.nextSibling()
            to_collect.add(sibling)
            sibling = next
        }
        ancestor = ancestor.parentNode()
    }
    if (to_collect.isEmpty()) return

    val collected_text = to_collect.joinToString("") {
        when (it) {
            is TextNode -> it.wholeText
            is Element -> it.text()
            else -> ""
        }
    }
    val kept = text.substring(0, hit).replace(WHITESPACE_RUN, " ").trim()
    if (kept.isEmpty() || !QUOTE_SIGNATURE.containsMatchIn(collected_text)) return

    val details = quote_details(forwarded = false, label = QUOTE_TOGGLE_LABEL)
    val content = quote_content(details)
    root.appendChild(details)
    for (node in to_collect) {
        node.remove()
        content.appendChild(node)
    }
    trim_blank_nodes_before(details)
}

private val LINE_BLOCK_TAGS = setOf(
    "address", "article", "aside", "blockquote", "body", "center", "dd", "details", "div", "dl", "dt",
    "fieldset", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li",
    "main", "nav", "ol", "p", "pre", "section", "summary", "table", "tbody", "td", "tfoot", "th",
    "thead", "tr", "ul",
)

private val SKIPPED_LINE_TAGS = setOf("style", "script", "template", "noscript", "title")

private val INLINE_LINE_TAGS = setOf("a", "b", "i", "u", "em", "strong", "font", "small", "code", "span")

private val CELL_TAGS = setOf("td", "th")

private class QuoteLine(val first: TextNode, var last: TextNode, val block: Node) {
    val raw = StringBuilder()
    var text = ""
}

private fun nearest_line_block(node: Node, root: Element): Node {
    var current = node.parentNode()
    while (current != null && current !== root) {
        if (current is Element && current.tagName().lowercase() in LINE_BLOCK_TAGS) return current
        current = current.parentNode()
    }
    return root
}

private fun collect_quote_lines(root: Element): List<QuoteLine> {
    val lines = mutableListOf<QuoteLine>()
    var current: QuoteLine? = null

    fun flush() {
        val line = current ?: return
        line.text = normalize_quote_line(line.raw.toString())
        lines.add(line)
        current = null
    }

    fun walk(node: Node) {
        for (child in node.childNodes()) {
            if (child is Element) {
                val tag = child.tagName().lowercase()
                if (tag in SKIPPED_LINE_TAGS) continue
                if (tag == "br" || tag in LINE_BLOCK_TAGS) flush()
                walk(child)
            } else if (child is TextNode) {
                val value = child.wholeText
                val block = nearest_line_block(child, root)
                if (current?.block.let { it != null && it !== block }) flush()
                val line = current
                if (line == null) {
                    if (value.isBlank()) continue
                    current = QuoteLine(child, child, block).also { it.raw.append(value) }
                } else {
                    line.last = child
                    line.raw.append(value)
                }
            }
        }
    }
    walk(root)
    flush()
    return lines
}

private fun node_has_media(node: Node): Boolean =
    node is Element && (node.tagName().lowercase() in MEDIA_TAGS || node.selectFirst("img, video, picture") != null)

private fun node_text(node: Node): String = when (node) {
    is TextNode -> node.wholeText
    is Element -> node.text()
    else -> ""
}

private fun is_meaningful(node: Node): Boolean = node_text(node).isNotBlank() || node_has_media(node)

private fun is_separator_node(node: Node): Boolean {
    if (node !is Element) return false
    if (node.tagName().equals("hr", ignoreCase = true)) return true
    return is_separator_text(normalize_quote_line(node.text()))
}

private fun previous_meaningful_sibling(node: Node): Node? {
    var previous = node.previousSibling()
    while (previous != null && !is_meaningful(previous) && !is_separator_node(previous)) {
        previous = previous.previousSibling()
    }
    return previous
}

private fun line_top(node: Node): Node {
    var top = node
    while (true) {
        val parent = top.parentNode() as? Element ?: break
        if (parent.tagName().lowercase() !in INLINE_LINE_TAGS || top.previousSibling() != null) break
        top = parent
    }
    return top
}

private fun line_top_end(node: Node): Node {
    var top = node
    while (true) {
        val parent = top.parentNode() as? Element ?: break
        if (parent.tagName().lowercase() !in INLINE_LINE_TAGS || top.nextSibling() != null) break
        top = parent
    }
    return top
}

private fun quote_start_node(root: Element, first: Node): Node {
    var node = line_top(first)
    while (true) {
        val parent = node.parentNode() ?: break
        if (parent === root) break
        if (parent is Element && parent.tagName().lowercase() in CELL_TAGS) break
        if (previous_meaningful_sibling(node) != null) break
        node = parent
    }
    val previous = previous_meaningful_sibling(node)
    return if (previous != null && is_separator_node(previous)) previous else node
}

private fun nodes_in_order(root: Element): List<Node> {
    val out = mutableListOf<Node>()
    fun walk(node: Node) {
        for (child in node.childNodes()) {
            out.add(child)
            walk(child)
        }
    }
    walk(root)
    return out
}

private fun has_media_before_node(root: Element, node: Node): Boolean {
    for (current in nodes_in_order(root)) {
        if (is_within(current, listOf(node))) return false
        if (current is Element && current.tagName().lowercase() in MEDIA_TAGS) return true
    }
    return false
}

private fun has_content_after_node(root: Element, node: Node): Boolean {
    var past = false
    for (current in nodes_in_order(root)) {
        if (!past) {
            if (current === node) past = true
            continue
        }
        if (is_within(current, listOf(node))) continue
        if (current is TextNode && current.wholeText.isNotBlank()) return true
        if (current is Element && current.tagName().lowercase() in MEDIA_TAGS) return true
    }
    return false
}

private fun shallow_copy(element: Element): Element = element.shallowClone().also { it.removeAttr("id") }

private fun following_siblings(node: Node): List<Node> {
    val out = mutableListOf<Node>()
    var sibling = node.nextSibling()
    while (sibling != null) {
        out.add(sibling)
        sibling = sibling.nextSibling()
    }
    return out
}

private fun split_before(node: Node, scope: Node): Node {
    var current = node
    while (true) {
        val parent = current.parentNode() ?: break
        if (parent === scope) break
        if (current.previousSibling() != null && parent.parentNode() != null && parent is Element) {
            val clone = shallow_copy(parent)
            val moving = listOf(current) + following_siblings(current)
            parent.after(clone)
            for (item in moving) clone.appendChild(item)
            current = clone
        } else {
            current = parent
        }
    }
    return current
}

private fun split_after(node: Node, scope: Node): Node {
    var current = node
    while (true) {
        val parent = current.parentNode() ?: break
        if (parent === scope) break
        if (current.nextSibling() != null && parent.parentNode() != null && parent is Element) {
            val clone = shallow_copy(parent)
            val moving = following_siblings(current)
            parent.after(clone)
            for (item in moving) clone.appendChild(item)
        }
        current = parent
    }
    return current
}

private fun is_edge_break(node: Node): Boolean =
    (node is Element && node.tagName().equals("br", ignoreCase = true)) ||
        (node is TextNode && node.wholeText.isBlank())

private fun trim_edge_breaks(node: Node?, at_end: Boolean) {
    var element: Node? = node
    var depth = 0
    while (element != null && depth < 4) {
        if (element !is Element) return
        var edge: Node? = if (at_end) element.lastChild() else element.firstChild()
        while (edge != null && is_edge_break(edge)) {
            val next = if (at_end) edge.previousSibling() else edge.nextSibling()
            edge.remove()
            edge = next
        }
        element = edge
        depth++
    }
}

private fun wrap_in_quote_details(nodes: List<Node>, insert_before: Node?, parent: Element): Element {
    val details = quote_details(forwarded = false, label = QUOTE_TOGGLE_LABEL)
    if (insert_before != null) insert_before.before(details) else parent.appendChild(details)
    val content = quote_content(details)
    for (node in nodes) content.appendChild(node)
    return details
}

private val LINE_FEED = Regex("\r?\n")

private fun break_preformatted_lines(root: Element) {
    for (pre in root.select("pre")) {
        val targets = mutableListOf<TextNode>()
        fun gather(node: Node) {
            for (child in node.childNodes()) {
                if (child is TextNode && child.wholeText.contains('\n')) targets.add(child)
                else if (child is Element) gather(child)
            }
        }
        gather(pre)
        for (node in targets) {
            val parts = node.wholeText.split(LINE_FEED)
            var anchor: Node = node
            parts.forEachIndexed { index, part ->
                if (index > 0) {
                    val br = Element("br")
                    anchor.after(br)
                    anchor = br
                }
                if (part.isNotEmpty()) {
                    val text = TextNode(part)
                    anchor.after(text)
                    anchor = text
                }
            }
            node.remove()
        }
    }
}

private fun collapse_by_quote_lines(root: Element) {
    break_preformatted_lines(root)
    val lines = collect_quote_lines(root)
    val texts = lines.map { it.text }
    val found = find_quote_start(texts) ?: find_trailing_quote(texts) ?: return
    val quote_end = resolve_quote_end(texts, found.body_from) ?: return

    val start_node = quote_start_node(root, lines[found.start].first)
    val end_node: Node? = if (quote_end.to_end) null else lines[quote_end.end].last
    val start_element = start_node as? Element ?: start_node.parentNode() as? Element
    val scope: Element = start_element?.closest("td, th") ?: root
    if (scope.childNodeSize() == 0) return

    val content_before = has_reply_text_before(texts, found.start) || has_media_before_node(root, start_node)

    if (!content_before) {
        if (end_node != null) {
            if (!has_content_after_node(root, end_node)) return
        } else {
            val to_collapse = mutableListOf(start_node)
            var sibling = start_node.nextSibling()
            while (sibling != null) {
                val tag = (sibling as? Element)?.tagName()?.lowercase()
                if (tag != "blockquote" && node_text(sibling).isNotBlank()) break
                to_collapse.add(sibling)
                sibling = sibling.nextSibling()
            }
            val outside = lines.any { !is_boilerplate_text(it.text) && !is_within(it.first, to_collapse) } ||
                root.select("img, video, picture").any { !is_within(it, to_collapse) }
            if (!outside) return
            val parent = start_node.parentNode() as? Element ?: return
            val details = wrap_in_quote_details(to_collapse.toList(), start_node, parent)
            hoist_trailing_signature(details)
            trim_blank_nodes_before(details)
            return
        }
    }

    if (end_node != null && !is_within(end_node, listOf(scope))) return

    val first_top = split_before(start_node, scope)
    val last_top = if (end_node != null) {
        split_after(line_top_end(end_node), scope)
    } else {
        scope.childNode(scope.childNodeSize() - 1)
    }
    if (first_top.parentNode() !== scope || last_top.parentNode() !== scope) return
    if (first_top.siblingIndex() > last_top.siblingIndex()) return

    val moving = mutableListOf<Node>()
    var cursor: Node? = first_top
    while (cursor != null) {
        moving.add(cursor)
        if (cursor === last_top) break
        cursor = cursor.nextSibling()
    }
    val anchor = last_top.nextSibling()
    val details = wrap_in_quote_details(moving, anchor, scope)
    hoist_trailing_signature(details)
    trim_edge_breaks(details.previousSibling(), true)
    trim_edge_breaks(details.nextSibling(), false)
    trim_blank_nodes_before(details)
}

private fun translation_skipped(node: Node, root: Element): Boolean {
    var parent = node.parentNode()
    while (parent != null && parent !== root) {
        if (parent is Element) {
            if (parent.tagName().lowercase() in TRANSLATION_SKIP_TAGS) return true
            if (parent.attr("translate").equals("no", ignoreCase = true)) return true
            val classes = parent.className().lowercase()
            if (TRANSLATION_SKIP_CLASSES.any { classes.contains(it) }) return true
        }
        parent = parent.parentNode()
    }
    return false
}

private fun is_translatable_text(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.length < MIN_TRANSLATABLE_LENGTH) return false
    return !NON_LINGUISTIC.matches(trimmed)
}

private fun translatable_text_nodes(root: Element): List<TextNode> =
    collect_text_nodes(root).filter { is_translatable_text(it.wholeText) && !translation_skipped(it, root) }

internal fun extract_translatable_segments(body: String): List<String> = try {
    val doc = Jsoup.parseBodyFragment(body).apply { outputSettings(raw_body_output_settings()) }
    translatable_text_nodes(doc.body()).map { it.wholeText }
} catch (_: Throwable) {
    emptyList()
}

internal fun apply_translated_segments(body: String, translated: List<String>): String = try {
    val doc = Jsoup.parseBodyFragment(body).apply { outputSettings(raw_body_output_settings()) }
    val nodes = translatable_text_nodes(doc.body())
    if (nodes.size != translated.size) {
        body
    } else {
        nodes.forEachIndexed { index, node -> node.text(translated[index]) }
        doc.body().html()
    }
} catch (_: Throwable) {
    body
}

internal fun detect_body_background(body: String): String? = try {
    val doc = Jsoup.parseBodyFragment(body).apply { outputSettings(raw_body_output_settings()) }
    val first = doc.body().children().firstOrNull()
    val declared = first?.attr("bgcolor")?.trim().orEmpty().ifBlank {
        first?.attr("style")?.let { style -> BACKGROUND_COLOR_VALUE.find(style)?.groupValues?.get(2)?.trim() }.orEmpty()
    }
    declared.takeIf { it.isNotBlank() && SAFE_COLOR_VALUE.matches(it) && !it.equals("transparent", ignoreCase = true) }
} catch (_: Throwable) {
    null
}

internal fun background_reads_light(color: String?): Boolean {
    val value = color?.trim() ?: return false
    if (value.equals("white", ignoreCase = true)) return true
    val luminance = relative_luminance(value) ?: return false
    return luminance > 0.55
}
