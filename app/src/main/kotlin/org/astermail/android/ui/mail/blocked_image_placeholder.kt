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

package org.astermail.android.ui.mail

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal data class BlockedImageLabels(val image: String, val tracking_pixel: String) {
    companion object {
        val ENGLISH = BlockedImageLabels(image = "Image blocked", tracking_pixel = "Tracking pixel blocked")
    }
}

internal data class BlockedImagePaint(
    val background: String,
    val border: String,
    val text: String,
    val radius: Double,
)

internal object BlockedImagePlaceholder {

    const val CLASS_NAME = "blocked-image"

    val LIGHT = BlockedImagePaint(background = "#f5f5f5", border = "#e8e8e8", text = "#5c616d", radius = 10.0)

    val DARK = BlockedImagePaint(background = "#0a0a0a", border = "#333333", text = "#909090", radius = 10.0)

    private const val FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"

    private const val LABEL_FONT_SIZE = 12.0

    private const val FALLBACK_WIDTH = 120.0

    private const val FALLBACK_HEIGHT = 24.0

    private const val REFERENCE_WIDTH = 360.0

    private const val MAX_DIMENSION = 10_000.0

    private const val WIDTH_ATTRIBUTE = "data-placeholder-width"

    private const val HEIGHT_ATTRIBUTE = "data-placeholder-height"

    private const val LABEL_ATTRIBUTE = "data-placeholder-label"

    private const val PHOTO_PATH =
        "m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z"

    private val PIXEL_LENGTH = Regex("""^(\d+(?:\.\d+)?)(?:px)?$""", RegexOption.IGNORE_CASE)

    private val PERCENT_LENGTH = Regex("""^(\d+(?:\.\d+)?)%$""")

    private val ASPECT_RATIO = Regex("""^(?:auto\s+)?(\d+(?:\.\d+)?)(?:\s*/\s*(\d+(?:\.\d+)?))?$""")

    private val IMPORTANT = Regex("""\s*!\s*important\s*$""", RegexOption.IGNORE_CASE)

    const val TRACKING_MARKER_ATTRIBUTE = "data-tracking-pixel-marker"

    private const val TRACKING_SLOT_ATTRIBUTE = "data-tracking-pixel-slot"

    const val TRACKING_SLOT_SELECTOR = "span[$TRACKING_SLOT_ATTRIBUTE]"

    const val TRACKING_MARKER_SELECTOR = "span[$TRACKING_MARKER_ATTRIBUTE]"

    const val TRACKING_MARKERS_SHOWN_ATTRIBUTE = "data-tracking-markers-shown"

    private val QUOTE_CLASSES = setOf("aster_quote", "gmail_quote", "protonmail_quote", "yahoo_quoted", "moz-cite-prefix")

    private val QUOTE_WRAPPER_CLASSES = setOf("aster-quoted-content", "aster-forwarded-content")

    private val ZERO_LENGTH = Regex("""^0*\.?0+(?:px|pt|em|rem|%)?$""", RegexOption.IGNORE_CASE)

    private data class Size(val width: Double, val height: Double)

    fun tracking_slot(): Element = Element("span").attr(TRACKING_SLOT_ATTRIBUTE, "true")

    fun tracking_marker(label: String): Element = Element("span")
        .attr(TRACKING_MARKER_ATTRIBUTE, "true")
        .attr("role", "img")
        .attr("aria-label", label)
        .attr("title", label)

    fun is_hidden(img: Element): Boolean {
        if (is_zero(img.attr("width")) || is_zero(img.attr("height"))) return true
        val style = style_declarations(img.attr("style"))
        if (style["display"]?.lowercase() == "none") return true
        if (style["visibility"]?.lowercase() in setOf("hidden", "collapse")) return true
        if (style["opacity"]?.toDoubleOrNull()?.let { it <= 0.0 } == true) return true
        return listOf("width", "height", "max-width", "max-height").any { is_zero(style[it]) }
    }

    private fun is_zero(value: String?): Boolean = ZERO_LENGTH.matches(value?.trim().orEmpty())

    fun tracking_marker_css(): String {
        val marker = TRACKING_MARKER_SELECTOR
        val shown = "html[$TRACKING_MARKERS_SHOWN_ATTRIBUTE] $marker"
        val dot = format(TrackingPixelDot.SIZE_PX)
        val offset = format(TrackingPixelDot.SIZE_PX / 2)
        return "$marker{display:none!important}" +
            "$shown{position:relative!important;display:inline-block!important;width:0!important;height:0!important;" +
            "margin:0!important;padding:0!important;border:0!important;overflow:visible!important;" +
            "vertical-align:middle!important;line-height:0!important;font-size:0!important;background:none!important;" +
            "animation:none!important;transition:none!important}" +
            "$shown::before{content:''!important;position:absolute!important;left:0!important;top:-${offset}px!important;" +
            "width:${dot}px!important;height:${dot}px!important;border-radius:50%!important;" +
            "background:${TrackingPixelDot.FILL}!important;box-shadow:${TrackingPixelDot.box_shadow()}!important;" +
            "animation:none!important;transition:none!important}" +
            "@media print{$marker,$shown{display:none!important}}"
    }

    fun has_tracking_markers(document: String): Boolean = document.contains("$TRACKING_MARKER_ATTRIBUTE=\"")

    fun with_tracking_markers_shown(document: String): String =
        if (has_tracking_markers(document)) {
            document.replaceFirst("<html", "<html $TRACKING_MARKERS_SHOWN_ATTRIBUTE")
        } else {
            document
        }

    fun count_drawn_tracking_markers(document: String): Int {
        if (!has_tracking_markers(document)) return 0
        return Jsoup.parse(document).select(TRACKING_MARKER_SELECTOR).count { marker ->
            marker.parents().none { hides_descendant(it, marker) }
        }
    }

    private fun hides_descendant(element: Element, descendant: Element): Boolean {
        if (element.hasAttr("hidden")) return true
        val style = style_declarations(element.attr("style"))
        if (style["display"]?.lowercase() == "none") return true
        if (style["visibility"]?.lowercase() in setOf("hidden", "collapse")) return true
        if (element.normalName() == "details" && !element.hasAttr("open")) {
            val summary = element.children().firstOrNull { it.normalName() == "summary" }
            return summary == null || descendant.parents().none { it === summary }
        }
        if (element.classNames().any { it in QUOTE_CLASSES }) {
            return element.parents().none { parent -> parent.classNames().any { it in QUOTE_WRAPPER_CLASSES } }
        }
        return false
    }

    fun prepare(img: Element, original_src: String, tracking: Boolean, labels: BlockedImageLabels) {
        val size = placeholder_size(img, tracking)
        val alt = if (img.hasAttr("alt")) img.attr("alt") else null
        if (alt == null || alt.isNotBlank()) {
            val label = if (tracking) labels.tracking_pixel else labels.image
            val value = if (!alt.isNullOrBlank() && !tracking) "$label: ${alt.trim()}" else label
            img.attr("title", value)
            img.attr("aria-label", value)
        }
        img.removeAttr("srcset")
        img.removeAttr("sizes")
        img.attr("data-original-src", original_src)
        img.attr("data-blocked", "true")
        img.attr("data-tracking-pixel", if (tracking) "true" else "false")
        img.attr(WIDTH_ATTRIBUTE, format(size.width))
        img.attr(HEIGHT_ATTRIBUTE, format(size.height))
        if (tracking) img.removeAttr(LABEL_ATTRIBUTE) else img.attr(LABEL_ATTRIBUTE, labels.image)
        img.addClass(CLASS_NAME)
        img.attr("src", placeholder_source(size, LIGHT, if (tracking) null else labels.image))
    }

    fun repaint(root: Element, paint: BlockedImagePaint) {
        for (img in root.select("img.$CLASS_NAME[data-blocked=true][$WIDTH_ATTRIBUTE]")) {
            val width = img.attr(WIDTH_ATTRIBUTE).toDoubleOrNull() ?: continue
            val height = img.attr(HEIGHT_ATTRIBUTE).toDoubleOrNull() ?: continue
            if (!width.isFinite() || !height.isFinite()) continue
            val size = Size(width.coerceIn(1.0, MAX_DIMENSION), height.coerceIn(1.0, MAX_DIMENSION))
            val label = if (img.hasAttr(LABEL_ATTRIBUTE)) img.attr(LABEL_ATTRIBUTE) else null
            img.attr("src", placeholder_source(size, paint, label))
        }
    }

    private fun style_declarations(style: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (declaration in EmailHtmlSanitizer.strip_css_comments(style).split(';')) {
            val colon = declaration.indexOf(':')
            if (colon <= 0) continue
            val name = declaration.substring(0, colon).trim().lowercase()
            out[name] = declaration.substring(colon + 1).replace(IMPORTANT, "").trim()
        }
        return out
    }

    private fun pixels(value: String?): Double? =
        PIXEL_LENGTH.matchEntire(value?.trim().orEmpty())?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }

    private fun percent_of_reference(value: String?): Double? =
        PERCENT_LENGTH.matchEntire(value?.trim().orEmpty())?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { REFERENCE_WIDTH * it.coerceAtMost(100.0) / 100.0 }

    private fun placeholder_size(img: Element, tracking: Boolean): Size {
        val style = style_declarations(img.attr("style"))
        val width = pixels(style["width"]) ?: pixels(img.attr("width"))
            ?: percent_of_reference(style["width"]) ?: percent_of_reference(img.attr("width"))
        val height = pixels(style["height"]) ?: pixels(img.attr("height"))
        val ratio = style["aspect-ratio"]?.let { ASPECT_RATIO.matchEntire(it.trim()) }?.let { match ->
            val a = match.groupValues[1].toDoubleOrNull() ?: return@let null
            val b = match.groupValues[2].ifEmpty { "1" }.toDoubleOrNull() ?: return@let null
            (a / b).takeIf { it.isFinite() && it > 0.0 }
        }
        val size = when {
            width != null && height != null -> Size(width, height)
            ratio != null -> {
                val w = width ?: (height?.let { it * ratio } ?: 300.0)
                Size(w, height ?: (w / ratio))
            }
            tracking -> Size(width ?: 1.0, height ?: 1.0)
            else -> Size(width ?: FALLBACK_WIDTH, height ?: FALLBACK_HEIGHT)
        }
        return Size(size.width.coerceIn(1.0, MAX_DIMENSION), size.height.coerceIn(1.0, MAX_DIMENSION))
    }

    private fun viewport(size: Size): Size =
        if (size.width <= REFERENCE_WIDTH) {
            size
        } else {
            Size(REFERENCE_WIDTH, maxOf(size.height * REFERENCE_WIDTH / size.width, 1.0))
        }

    private fun label_width(label: String): Double {
        var width = 0.0
        var index = 0
        while (index < label.length) {
            val code_point = label.codePointAt(index)
            width += if (code_point >= 0x1100) LABEL_FONT_SIZE else LABEL_FONT_SIZE * 0.55
            index += Character.charCount(code_point)
        }
        return kotlin.math.ceil(width)
    }

    private fun placeholder_source(size: Size, paint: BlockedImagePaint, label: String?): String {
        val view = viewport(size)
        val w = maxOf(view.width, 1.0)
        val h = maxOf(view.height, 1.0)
        val icon_size = minOf(if (w < 100 || h < 36) 16.0 else 24.0, w - 4, h - 4)
        val stacked = h >= 64
        val text_width = if (label != null) label_width(label) else 0.0
        val show_label = label != null && label.isNotBlank() && h >= 20 && w >= 110 &&
            w >= (if (stacked) text_width else icon_size + 8 + text_width) + 8
        val group_width = if (show_label && !stacked) icon_size + 8 + text_width else icon_size
        val x = (w - group_width) / 2
        val y = if (stacked && show_label) (h - icon_size - 26) / 2 else (h - icon_size) / 2
        val svg = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(format(size.width))
            append("\" height=\"").append(format(size.height))
            append("\" viewBox=\"0 0 ").append(format(w)).append(' ').append(format(h))
            append("\" preserveAspectRatio=\"none\">")
            append("<rect x=\"0.5\" y=\"0.5\" width=\"").append(format(maxOf(w - 1, 0.0)))
            append("\" height=\"").append(format(maxOf(h - 1, 0.0)))
            append("\" rx=\"").append(format(minOf(paint.radius, w / 2, h / 2)))
            append("\" fill=\"").append(xml(paint.background))
            append("\" stroke=\"").append(xml(paint.border)).append("\"/>")
            if (icon_size > 0) {
                append("<svg x=\"").append(format(x)).append("\" y=\"").append(format(y))
                append("\" width=\"").append(format(icon_size)).append("\" height=\"").append(format(icon_size))
                append("\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"").append(xml(paint.text))
                append("\" stroke-width=\"1.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"")
                append(PHOTO_PATH).append("\"/></svg>")
            }
            if (show_label && label != null) {
                append("<text x=\"").append(format(if (stacked) w / 2 else x + icon_size + 8))
                append("\" y=\"").append(format(if (stacked) y + icon_size + 22 else h / 2 + 4))
                append("\" text-anchor=\"").append(if (stacked) "middle" else "start")
                append("\" font-family=\"").append(xml(FONT))
                append("\" font-size=\"").append(format(LABEL_FONT_SIZE))
                append("\" fill=\"").append(xml(paint.text)).append("\">")
                append(xml(label)).append("</text>")
            }
            append("</svg>")
        }
        return "data:image/svg+xml," + percent_encode(svg)
    }

    private fun format(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded) && kotlin.math.abs(rounded) < 1e15) {
            rounded.toLong().toString()
        } else {
            java.math.BigDecimal(rounded).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    }

    private fun xml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private const val UNRESERVED = "-_.!~*'()"

    private fun percent_encode(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 0x80 && (ch.isLetterOrDigit() || ch in UNRESERVED)) {
                out.append(ch)
            } else {
                out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
            }
        }
        return out.toString()
    }
}
