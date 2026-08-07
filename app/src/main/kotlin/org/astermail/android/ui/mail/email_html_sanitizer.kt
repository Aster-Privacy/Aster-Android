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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

object EmailHtmlSanitizer {

    data class SanitizeOptions(
        val clean_tracking_links: Boolean = true,
        val remove_tracking_pixels: Boolean = true,
        val block_remote_fonts: Boolean = true,
        val block_remote_css: Boolean = true,
    )

    private val safelist: Safelist by lazy { build_safelist() }

    private val tracking_params = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid",
        "msclkid", "twclid", "li_fat_id", "mc_cid", "mc_eid", "oly_anon_id",
        "oly_enc_id", "_openstat", "vero_id", "wickedid", "yclid", "rb_clickid",
        "s_cid", "ml_subscriber", "ml_subscriber_hash", "igshid", "ref_src",
        "ref_url", "trk", "trkcampaign", "trkinfo", "sc_campaign", "sc_channel",
        "sc_content", "sc_medium", "sc_outcome", "sc_geo", "sc_country",
    )

    private val tracking_pixel_url_patterns = listOf(
        Regex("/track", RegexOption.IGNORE_CASE),
        Regex("/open/", RegexOption.IGNORE_CASE),
        Regex("/pixel", RegexOption.IGNORE_CASE),
        Regex("/beacon", RegexOption.IGNORE_CASE),
        Regex("/wf/open", RegexOption.IGNORE_CASE),
        Regex("/o\\.gif", RegexOption.IGNORE_CASE),
        Regex("/t\\.gif", RegexOption.IGNORE_CASE),
        Regex("/e\\.gif", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])mailchimp\\.com.*/track", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])list-manage\\.com.*/track", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])sendgrid\\.net.*/wf/", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])amazonses\\.com(?:/|$)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])doubleclick\\.net(?:/|$)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])mailgun\\.org.*/o/", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])sparkpostmail(?:/|$)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[./])returnpath\\.net(?:/|$)", RegexOption.IGNORE_CASE),
        Regex("emltrk\\.", RegexOption.IGNORE_CASE),
        Regex("bsn\\.sendgrid", RegexOption.IGNORE_CASE),
    )

    private val tracking_pixel_style_patterns = listOf(
        Regex("width\\s*[:=]\\s*[\"']?[01](?!\\d)", RegexOption.IGNORE_CASE),
        Regex("height\\s*[:=]\\s*[\"']?[01](?!\\d)", RegexOption.IGNORE_CASE),
    )

    fun sanitize(raw_html: String, options: SanitizeOptions = SanitizeOptions()): String {
        if (raw_html.isBlank()) return ""
        val pre = strip_dangerous_blocks(raw_html)
        val head_styles = extract_head_styles(pre)
        val body_only = extract_body_html(pre)
        val cleaned_body = Jsoup.clean(body_only, "https://mail-content.invalid/", safelist)
        val doc = Jsoup.parseBodyFragment(cleaned_body)
        scrub_attributes(doc, options.clean_tracking_links)
        if (options.remove_tracking_pixels) remove_tracking_pixels(doc)
        scrub_style_blocks(doc, options)
        autolink_bare_urls(doc, options.clean_tracking_links)
        val sb = StringBuilder()
        for (css in head_styles) {
            val safe_css = sanitize_css_block(css, options)
            if (safe_css.isNotBlank()) sb.append("<style>").append(safe_css).append("</style>")
        }
        sb.append(doc.body().html())
        return sb.toString()
    }

    private fun remove_tracking_pixels(doc: Document) {
        for (img in doc.select("img[src]")) {
            val lower = img.attr("src").trim().lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) continue
            if (is_tracking_pixel(img)) img.remove()
        }
    }

    fun strip_tracking_params(url: String): String {
        val q_idx = url.indexOf('?')
        if (q_idx == -1) return url
        val hash_idx = url.indexOf('#', q_idx)
        val query = if (hash_idx == -1) url.substring(q_idx + 1) else url.substring(q_idx + 1, hash_idx)
        if (query.isEmpty()) return url
        val fragment = if (hash_idx == -1) "" else url.substring(hash_idx)
        val params = query.split('&')
        val kept = params.filter { it.substringBefore('=').lowercase() !in tracking_params }
        if (kept.size == params.size) return url
        val base = url.substring(0, q_idx)
        return if (kept.isEmpty()) base + fragment else base + "?" + kept.joinToString("&") + fragment
    }

    fun removed_tracking_params(url: String): List<String> {
        val q_idx = url.indexOf('?')
        if (q_idx == -1) return emptyList()
        val hash_idx = url.indexOf('#', q_idx)
        val query = if (hash_idx == -1) url.substring(q_idx + 1) else url.substring(q_idx + 1, hash_idx)
        if (query.isEmpty()) return emptyList()
        return query.split('&')
            .map { it.substringBefore('=') }
            .filter { it.lowercase() in tracking_params }
    }

    data class TrackerReport(
        val pixel_domains: List<Pair<String, Int>> = emptyList(),
        val param_counts: List<Pair<String, Int>> = emptyList(),
        val pixel_count: Int = 0,
        val cleaned_link_count: Int = 0,
    ) {
        val total: Int get() = pixel_count + cleaned_link_count
    }

    private const val TRACKER_SCAN_MAX_CHARS = 2 * 1024 * 1024

    fun analyze_trackers(html: String?): TrackerReport {
        if (html.isNullOrBlank()) return TrackerReport()
        if (html.length > TRACKER_SCAN_MAX_CHARS) return TrackerReport()
        return try {
            val doc = Jsoup.parseBodyFragment(html)
            val domains = LinkedHashMap<String, Int>()
            var pixels = 0
            for (img in doc.select("img[src]")) {
                val src = img.attr("src").trim()
                val lower = src.lowercase()
                if (!lower.startsWith("http://") && !lower.startsWith("https://")) continue
                if (!is_tracking_pixel(img)) continue
                pixels++
                val host = url_host(src) ?: continue
                domains[host] = (domains[host] ?: 0) + 1
            }
            val params = LinkedHashMap<String, Int>()
            var cleaned_links = 0
            for (a in doc.select("a[href]")) {
                val href = a.attr("href").trim()
                val lower = href.lowercase()
                if (!lower.startsWith("http://") && !lower.startsWith("https://")) continue
                val removed = removed_tracking_params(href)
                if (removed.isEmpty()) continue
                cleaned_links++
                for (p in removed.distinct()) params[p] = (params[p] ?: 0) + 1
            }
            TrackerReport(
                pixel_domains = domains.entries.map { it.key to it.value },
                param_counts = params.entries.sortedByDescending { it.value }.map { it.key to it.value },
                pixel_count = pixels,
                cleaned_link_count = cleaned_links,
            )
        } catch (_: Throwable) {
            TrackerReport()
        }
    }

    fun url_host(url: String): String? {
        val scheme_idx = url.indexOf("//")
        if (scheme_idx == -1) return null
        var authority = url.substring(scheme_idx + 2)
        val end = authority.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (end >= 0) authority = authority.substring(0, end)
        val at_idx = authority.lastIndexOf('@')
        if (at_idx >= 0) authority = authority.substring(at_idx + 1)
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close > 0) return authority.substring(0, close + 1).lowercase()
        }
        val host = authority.substringBefore(':').lowercase()
        return host.takeIf { it.isNotEmpty() }
    }

    fun is_tracking_pixel(img: Element): Boolean {
        val width = img.attr("width").ifEmpty { null }
        val height = img.attr("height").ifEmpty { null }
        val style = img.attr("style")
        val src = img.attr("src")
        val alt = img.attr("alt").ifEmpty { null }
        val css_class = img.attr("class").ifEmpty { null }
        val tiny = setOf("0", "1")
        if (width in tiny && height in tiny) return true
        if ((width in tiny || height in tiny) && alt == null) return true
        if (tracking_pixel_style_patterns.all { it.containsMatchIn(style) }) return true
        if (tracking_pixel_style_patterns.any { it.containsMatchIn(style) } && alt == null) return true
        if (src.isNotEmpty() && tracking_pixel_url_patterns.any { it.containsMatchIn(src) }) return true
        if (src.isNotEmpty() && width == null && height == null && style.isEmpty() && alt == null && css_class == null) return true
        return false
    }

    fun replace_blocked_images(html: String, placeholder_text: String): String {
        if (html.isBlank()) return html
        val doc = Jsoup.parseBodyFragment(html)
        for (img in doc.select("img[src]")) {
            val src = img.attr("src")
            val lower = src.trim().lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) continue
            if (lower.startsWith(INLINE_IMAGE_URL_PREFIX)) continue
            if (is_tracking_pixel(img)) {
                img.remove()
                continue
            }
            val span = Element("span")
            span.attr("class", "blocked-image")
            span.attr("data-original-src", src)
            val w = img.attr("width")
            val h = img.attr("height")
            val s = img.attr("style")
            val alt = img.attr("alt")
            if (w.isNotEmpty()) span.attr("data-width", w)
            if (h.isNotEmpty()) span.attr("data-height", h)
            if (s.isNotEmpty()) span.attr("data-style", s)
            if (alt.isNotEmpty()) span.attr("data-alt", alt)
            span.text(alt.ifEmpty { placeholder_text })
            img.replaceWith(span)
        }
        return doc.body().html()
    }

    private val remote_url_prefix = Regex("^(?:https?:)?//", RegexOption.IGNORE_CASE)

    private val css_remote_url = Regex(
        """url\(\s*["']?(?:https?:)?//[^"')\s]+["']?\s*\)""",
        RegexOption.IGNORE_CASE,
    )

    fun strip_css_comments(css: String): String {
        if (!css.contains("/*")) return css
        val out = StringBuilder(css.length)
        var index = 0
        var quote: Char? = null
        while (index < css.length) {
            val ch = css[index]
            if (quote != null) {
                out.append(ch)
                if (ch == '\\' && index + 1 < css.length) {
                    out.append(css[index + 1])
                    index += 2
                    continue
                }
                if (ch == quote) quote = null
                index++
                continue
            }
            if (ch == '"' || ch == '\'') {
                quote = ch
                out.append(ch)
                index++
                continue
            }
            if (ch == '/' && index + 1 < css.length && css[index + 1] == '*') {
                val end = css.indexOf("*/", index + 2)
                index = if (end < 0) css.length else end + 2
                continue
            }
            out.append(ch)
            index++
        }
        return out.toString()
    }

    private val solid_bg_color = Regex(
        """background(?:-color)?\s*:\s*[^;]*(#[0-9a-f]{3,8}|rgb|hsl|\b(?:black|white|red|green|blue|gray|grey|silver|navy|teal|maroon|purple|orange|yellow)\b)""",
        RegexOption.IGNORE_CASE,
    )

    fun neutralize_blocked_backgrounds(html: String): String {
        if (html.isBlank()) return html
        val doc = Jsoup.parseBodyFragment(html)
        for (el in doc.select("[background]")) {
            if (remote_url_prefix.containsMatchIn(el.attr("background").trim())) {
                el.removeAttr("background")
                apply_bg_placeholder(el)
            }
        }
        for (el in doc.select("[style]")) {
            val style = strip_css_comments(el.attr("style"))
            if (css_remote_url.containsMatchIn(style)) {
                el.attr("style", css_remote_url.replace(style, "none"))
                apply_bg_placeholder(el)
            }
        }
        for (st in doc.select("style")) {
            val css = strip_css_comments(st.data())
            if (css_remote_url.containsMatchIn(css)) st.html(css_remote_url.replace(css, "none"))
        }
        return doc.body().html()
    }

    private fun apply_bg_placeholder(el: Element) {
        if (el.attr("bgcolor").isNotBlank()) return
        val style = el.attr("style")
        if (solid_bg_color.containsMatchIn(style)) return
        val sep = if (style.isBlank() || style.trimEnd().endsWith(";")) "" else ";"
        el.attr("style", style + sep + "background-color:#6b7280")
    }

    private fun autolink_bare_urls(doc: Document, clean_tracking_links: Boolean = true) {
        val url_re = Regex("""https?://[^\s<>"'{}|\\^`\[\]]+""")
        val skip_ancestors = setOf("a", "style", "script")
        val text_nodes = mutableListOf<TextNode>()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    if (node is TextNode) text_nodes.add(node)
                }

                override fun tail(node: Node, depth: Int) {}
            },
            doc.body(),
        )
        for (tn in text_nodes) {
            var ancestor = tn.parent()
            var skip = false
            while (ancestor is Element) {
                if (ancestor.tagName().lowercase() in skip_ancestors) {
                    skip = true
                    break
                }
                ancestor = ancestor.parent()
            }
            if (skip) continue
            val text = tn.wholeText
            if (!url_re.containsMatchIn(text)) continue
            val nodes = mutableListOf<Node>()
            var last = 0
            for (m in url_re.findAll(text)) {
                if (m.range.first > last) nodes.add(TextNode(text.substring(last, m.range.first)))
                val a = Element("a")
                a.attr("href", if (clean_tracking_links) strip_tracking_params(m.value) else m.value)
                a.attr("target", "_blank")
                a.attr("rel", "noopener noreferrer nofollow")
                a.text(m.value)
                nodes.add(a)
                last = m.range.last + 1
            }
            if (last < text.length) nodes.add(TextNode(text.substring(last)))
            var ref: Node = tn
            for (n in nodes) {
                ref.after(n)
                ref = n
            }
            tn.remove()
        }
    }

    private fun extract_head_styles(html: String): List<String> {
        val result = mutableListOf<String>()
        val head_match = Regex("<head\\b[\\s>][\\s\\S]*?</head\\s*>", RegexOption.IGNORE_CASE).find(html) ?: return result
        val style_re = Regex("<style\\b[^>]*>([\\s\\S]*?)</style\\s*>", RegexOption.IGNORE_CASE)
        for (m in style_re.findAll(head_match.value)) {
            result.add(m.groupValues[1])
        }
        return result
    }

    private fun extract_body_html(html: String): String {
        val body_match = Regex("<body\\b[^>]*>([\\s\\S]*?)</body\\s*>", RegexOption.IGNORE_CASE).find(html)
        if (body_match != null) return body_match.groupValues[1]
        return html
    }

    private fun build_safelist(): Safelist {
        return Safelist.relaxed()
            .addTags(
                "table", "thead", "tbody", "tfoot", "tr", "td", "th",
                "caption", "colgroup", "col", "div", "span", "section",
                "article", "header", "footer", "main", "nav", "aside",
                "details", "summary", "figure", "figcaption", "blockquote",
                "pre", "code", "kbd", "samp", "var", "mark", "small", "sub",
                "sup", "u", "s", "strike", "del", "ins", "abbr", "address",
                "cite", "dfn", "time", "br", "hr", "wbr", "center", "font",
                "style",
            )
            .addAttributes(":all", "style", "class", "id", "dir", "lang", "title", "align")
            .addAttributes("a", "target", "rel", "name")
            .addAttributes("img", "src", "alt", "width", "height", "loading", "srcset")
            .addAttributes(
                "table", "border", "cellpadding", "cellspacing", "bgcolor",
                "background", "width", "height", "align",
            )
            .addAttributes("td", "colspan", "rowspan", "bgcolor", "background", "valign", "align", "width", "height")
            .addAttributes("th", "colspan", "rowspan", "bgcolor", "background", "valign", "align", "width", "height")
            .addAttributes("tr", "bgcolor", "background", "valign", "align")
            .addAttributes("font", "color", "face", "size")
            .addAttributes("ol", "start", "reversed", "type")
            .addAttributes("ul", "type")
            .addAttributes("li", "value")
            .addAttributes("col", "span", "width")
            .addProtocols("a", "href", "http", "https", "mailto", "tel", "sms", "cid", "aster")
            .addProtocols("img", "src", "http", "https", "data", "cid")
            .addProtocols("blockquote", "cite", "http", "https")
            .preserveRelativeLinks(false)
    }

    private fun strip_dangerous_blocks(html: String): String {
        var out = html
        out = out.replace(Regex("<!--\\[if\\s[^\\]!]*?mso[^\\]]*?\\]>[\\s\\S]*?<!\\[endif\\]\\s*--\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<!--\\[if\\s!mso\\]><!-->\\s*", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("\\s*<!--<!\\[endif\\]\\s*--\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<!--\\[if\\s!mso\\]>\\s*<!--\\s*--\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<!--\\s*<!\\[endif\\]\\s*--\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<script\\b[^>]*>[\\s\\S]*?</script\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<iframe\\b[^>]*>[\\s\\S]*?</iframe\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<object\\b[^>]*>[\\s\\S]*?</object\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<embed\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<applet\\b[^>]*>[\\s\\S]*?</applet\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<base\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<meta\\b[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<link\\b[^>]*rel\\s*=\\s*[\"']?(?:import|prefetch|preload)[\"']?[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<form\\b[^>]*>|</form\\s*>", RegexOption.IGNORE_CASE), "")
        return out
    }

    private fun scrub_attributes(doc: Document, clean_tracking_links: Boolean = true) {
        val js_uri = Regex("^\\s*javascript\\s*:", RegexOption.IGNORE_CASE)
        val data_html_uri = Regex("^\\s*data\\s*:\\s*text/html", RegexOption.IGNORE_CASE)
        val vbscript_uri = Regex("^\\s*vbscript\\s*:", RegexOption.IGNORE_CASE)
        for (el in doc.allElements) {
            val to_remove = mutableListOf<String>()
            for (attr in el.attributes()) {
                val key_lower = attr.key.lowercase()
                if (key_lower.startsWith("on")) {
                    to_remove.add(attr.key); continue
                }
                if (key_lower == "srcdoc" || key_lower == "formaction" || key_lower == "ping") {
                    to_remove.add(attr.key); continue
                }
                if (key_lower == "href" || key_lower == "src" || key_lower == "action" || key_lower == "background") {
                    val v = attr.value
                    if (js_uri.containsMatchIn(v) || data_html_uri.containsMatchIn(v) || vbscript_uri.containsMatchIn(v)) {
                        to_remove.add(attr.key)
                    }
                }
                if (key_lower == "style") {
                    val cleaned = sanitize_style_value(attr.value)
                    if (cleaned != attr.value) el.attr(attr.key, cleaned)
                }
            }
            for (k in to_remove) el.removeAttr(k)
            if (el.tagName().equals("a", ignoreCase = true)) {
                el.attr("target", "_blank")
                el.attr("rel", "noopener noreferrer nofollow")
                val href = el.attr("href")
                val lower_href = href.trim().lowercase()
                if (clean_tracking_links && (lower_href.startsWith("http://") || lower_href.startsWith("https://"))) {
                    val cleaned_href = strip_tracking_params(href)
                    if (cleaned_href != href) el.attr("href", cleaned_href)
                }
            }
        }
    }

    private fun scrub_style_blocks(doc: Document, options: SanitizeOptions = SanitizeOptions()) {
        for (el in doc.select("style")) {
            el.html(sanitize_css_block(el.data(), options))
        }
    }

    private fun sanitize_style_value(css: String): String {
        var out = strip_css_comments(css)
        out = out.replace("<", "")
        out = out.replace(Regex("expression\\s*\\(", RegexOption.IGNORE_CASE), "blocked(")
        out = out.replace(Regex("javascript\\s*:", RegexOption.IGNORE_CASE), "blocked:")
        out = out.replace(Regex("vbscript\\s*:", RegexOption.IGNORE_CASE), "blocked:")
        out = out.replace(Regex("@import\\b[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("behavior\\s*:[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("-moz-binding\\s*:[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("position\\s*:\\s*(fixed|sticky)", RegexOption.IGNORE_CASE), "position: relative")
        return out
    }

    private val font_face_block = Regex("@font-face\\s*\\{[^}]*\\}", RegexOption.IGNORE_CASE)

    private fun sanitize_css_block(css: String, options: SanitizeOptions = SanitizeOptions()): String {
        var out = strip_css_comments(css)
        if (options.block_remote_css) {
            out = out.replace(Regex("@import\\b[^;]*;?", RegexOption.IGNORE_CASE), "")
        }
        if (options.block_remote_fonts) {
            out = font_face_block.replace(out) { m ->
                if (css_remote_url.containsMatchIn(m.value)) "" else m.value
            }
        }
        out = out.replace(Regex("@charset\\b[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("@namespace\\b[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("@document\\b[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("-moz-document[^;{]*\\{[^}]*\\}", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("expression\\s*\\(", RegexOption.IGNORE_CASE), "blocked(")
        out = out.replace(Regex("javascript\\s*:", RegexOption.IGNORE_CASE), "blocked:")
        out = out.replace(Regex("vbscript\\s*:", RegexOption.IGNORE_CASE), "blocked:")
        out = out.replace(Regex("behavior\\s*:[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("-moz-binding\\s*:[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("image-set\\s*\\([^)]*\\)", RegexOption.IGNORE_CASE), "none")
        out = out.replace(Regex("-webkit-image-set\\s*\\([^)]*\\)", RegexOption.IGNORE_CASE), "none")
        out = out.replace(Regex("cross-fade\\s*\\([^)]*\\)", RegexOption.IGNORE_CASE), "none")
        out = strip_dark_mode_media(out)
        out = out.replace(Regex("position\\s*:\\s*(fixed|sticky)", RegexOption.IGNORE_CASE), "position: relative")
        out = out.replace(Regex("</(style|script)", RegexOption.IGNORE_CASE), """<\\/$1""")
        return out
    }

    private fun strip_dark_mode_media(css: String): String {
        var result = css
        val pattern = Regex("@media\\s*\\([^)]*prefers-color-scheme\\s*:\\s*dark[^)]*\\)\\s*\\{", RegexOption.IGNORE_CASE)
        var match = pattern.find(result)
        while (match != null) {
            var depth = 1
            var i = match.range.last + 1
            while (i < result.length && depth > 0) {
                when (result[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            result = result.substring(0, match.range.first) + result.substring(i)
            match = pattern.find(result, match.range.first)
        }
        return result
    }

    fun rewrite_img_through_proxy(html: String, proxy_base: String, allow_external: Boolean): String {
        if (html.isBlank()) return html
        val doc = Jsoup.parseBodyFragment(html)
        for (img in doc.select("img[src]")) {
            val src = img.attr("src")
            if (src.startsWith("cid:", ignoreCase = true)) continue
            if (src.startsWith("data:", ignoreCase = true)) continue
            if (!allow_external) {
                img.attr("data-blocked-src", src)
                img.removeAttr("src")
                continue
            }
            if (src.startsWith(proxy_base)) continue
            val encoded = java.net.URLEncoder.encode(src, "UTF-8")
            img.attr("src", "$proxy_base?url=$encoded")
        }
        return doc.body().html()
    }
}
