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

import org.astermail.android.mail.Autolink
import org.astermail.android.mail.degraded_email_html
import org.astermail.android.mail.strip_script_like_blocks
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Cleaner
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

    private fun raw_output_settings(): org.jsoup.nodes.Document.OutputSettings =
        org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)

    private const val max_sanitizer_input_chars = 4_000_000

    fun sanitize(raw_html: String, options: SanitizeOptions = SanitizeOptions()): String {
        if (raw_html.isBlank()) return ""
        return try {
            sanitize_unsafe(raw_html, options)
        } catch (_: Throwable) {
            degraded_email_html(raw_html.take(max_sanitizer_input_chars))
        }
    }

    private fun bounded_input(raw_html: String): String =
        if (raw_html.length > max_sanitizer_input_chars) raw_html.take(max_sanitizer_input_chars) else raw_html

    private fun clean_body(pre: String, clean_tracking_links: Boolean): Document {
        val dirty = Jsoup.parseBodyFragment(extract_body_html(pre), "https://mail-content.invalid/")
        drop_blank_image_sources(dirty)
        val doc = Cleaner(safelist).clean(dirty).apply { outputSettings(raw_output_settings()) }
        scrub_attributes(doc, clean_tracking_links)
        remove_sourceless_images(doc)
        return doc
    }

    private fun sanitize_unsafe(raw_html: String, options: SanitizeOptions): String {
        val pre = strip_dangerous_blocks(bounded_input(raw_html))
        val head_styles = extract_head_styles(pre)
        val doc = clean_body(pre, options.clean_tracking_links)
        if (options.remove_tracking_pixels) remove_tracking_pixels(doc)
        scrub_style_blocks(doc, options)
        autolink_bare_urls(doc, options.clean_tracking_links)
        mark_email_buttons(doc)
        val sb = StringBuilder()
        for (css in head_styles) {
            val safe_css = sanitize_css_block(css, options)
            if (safe_css.isNotBlank()) sb.append("<style>").append(safe_css).append("</style>")
        }
        sb.append(doc.body().html())
        return sb.toString()
    }

    private fun drop_blank_image_sources(doc: Document) {
        for (img in doc.select("img[src]")) {
            if (img.attr("src").isBlank()) img.removeAttr("src")
        }
    }

    private fun remove_sourceless_images(doc: Document) {
        for (img in doc.select("img")) {
            if (img.attr("src").isBlank() && img.attr("srcset").isBlank()) img.remove()
        }
    }

    private fun remove_tracking_pixels(doc: Document) {
        for (img in doc.select("img")) {
            if (remote_image_source(img) != null && is_tracking_pixel(img)) img.remove()
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
        val image_count: Int = 0,
        val pixel_urls: List<String> = emptyList(),
        val image_urls: List<String> = emptyList(),
    ) {
        val total: Int get() = pixel_count + cleaned_link_count
    }

    private const val TRACKER_URL_LIST_CAP = 60

    internal fun remote_image_source(img: Element): String? {
        val src = img.attr("src").ifBlank { img.attr("srcset").trim().substringBefore(' ') }
        return src.trim().takeUnless { is_local_image_source(src) }
    }

    fun analyze_trackers(html: String?): TrackerReport {
        if (html.isNullOrBlank()) return TrackerReport()
        return try {
            val doc = clean_body(strip_dangerous_blocks(bounded_input(html)), clean_tracking_links = false)
            val domains = LinkedHashMap<String, Int>()
            var pixels = 0
            var images = 0
            val pixel_urls = mutableListOf<String>()
            val image_urls = mutableListOf<String>()
            for (img in doc.select("img")) {
                val src = remote_image_source(img) ?: continue
                if (!is_tracking_pixel(img)) {
                    images++
                    if (image_urls.size < TRACKER_URL_LIST_CAP) image_urls.add(src)
                    continue
                }
                pixels++
                if (pixel_urls.size < TRACKER_URL_LIST_CAP) pixel_urls.add(src)
                val host = url_host(src) ?: src
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
                image_count = images,
                pixel_urls = pixel_urls,
                image_urls = image_urls,
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

    private val url_ignorable_characters = Regex("[\\u0000-\\u0020\\u007F]")

    private fun is_local_image_source(src: String): Boolean {
        val compact = src.replace(url_ignorable_characters, "").lowercase()
        if (compact.isEmpty()) return true
        if (compact.startsWith("data:") || compact.startsWith("cid:")) return true
        return src.trim().startsWith(INLINE_IMAGE_URL_PREFIX)
    }

    internal fun replace_blocked_images(
        html: String,
        labels: BlockedImageLabels = BlockedImageLabels.ENGLISH,
    ): String {
        if (html.isBlank()) return html
        val doc = Jsoup.parseBodyFragment(html).apply { outputSettings(raw_output_settings()) }
        for (img in doc.select("img")) {
            val src = remote_image_source(img)
            if (src == null) {
                img.removeAttr("srcset")
                img.removeAttr("sizes")
                continue
            }
            BlockedImagePlaceholder.prepare(img, src, is_tracking_pixel(img), labels)
        }
        for (element in doc.select("[srcset]")) {
            element.removeAttr("srcset")
            element.removeAttr("sizes")
        }
        for (element in doc.select("[src]")) {
            if (element.normalName() == "img") continue
            if (!is_local_image_source(element.attr("src"))) element.removeAttr("src")
        }
        return doc.body().html()
    }

    private val remote_url_prefix = Regex(
        """^(?!\s*(?:data:|cid:|#))\s*\S""",
        RegexOption.IGNORE_CASE,
    )

    private val css_url_open = Regex("""url\(""", RegexOption.IGNORE_CASE)
    private val css_local_target = Regex("""^(?:data:|cid:|#)""", RegexOption.IGNORE_CASE)
    private val css_hex_escape = Regex("""\\([0-9a-fA-F]{1,6})(\r\n|[ \t\r\n\u000C])?""")
    private val css_char_escape = Regex("""\\([g-zG-Z\-])""")
    private val css_bare_import = Regex("""@import\b[^;]*;?""", RegexOption.IGNORE_CASE)
    private val css_any_image_set = Regex("""(?:-webkit-)?image-set\s*\([^)]*\)""", RegexOption.IGNORE_CASE)
    private val css_any_cross_fade = Regex("""(?:-webkit-)?cross-fade\s*\([^)]*\)""", RegexOption.IGNORE_CASE)

    private fun is_plain_css_letter(code: Int): Boolean =
        (code in 'a'.code..'z'.code) || (code in 'A'.code..'Z'.code) || code == '-'.code

    fun decode_css_escapes(css: String): String {
        if (!css.contains('\\')) return css
        val hex_decoded = css_hex_escape.replace(css) { m ->
            val code = m.groupValues[1].toIntOrNull(16)
            if (code != null && is_plain_css_letter(code)) code.toChar().toString() else m.value
        }
        return css_char_escape.replace(hex_decoded) { m -> m.groupValues[1] }
    }

    private data class CssUrlSpan(val start: Int, val end: Int, val target: String)

    private fun css_url_spans(css: String): List<CssUrlSpan> {
        val spans = mutableListOf<CssUrlSpan>()
        var from = 0
        while (from < css.length) {
            val open = css_url_open.find(css, from) ?: break
            var index = open.range.last + 1
            while (index < css.length && css[index].isWhitespace()) index++
            val quote = css.getOrNull(index)?.takeIf { it == '"' || it == '\'' }
            val target = StringBuilder()
            if (quote != null) {
                index++
                while (index < css.length && css[index] != quote) {
                    if (css[index] == '\\' && index + 1 < css.length) {
                        target.append(css[index + 1])
                        index += 2
                        continue
                    }
                    target.append(css[index])
                    index++
                }
                if (index < css.length) index++
                while (index < css.length && css[index] != ')') index++
            } else {
                while (index < css.length && css[index] != ')') {
                    target.append(css[index])
                    index++
                }
            }
            val end = if (index < css.length) index + 1 else css.length
            spans.add(CssUrlSpan(open.range.first, end, target.toString().trim()))
            from = end
        }
        return spans
    }

    fun has_remote_css_url(css: String): Boolean {
        val decoded = decode_css_escapes(strip_css_comments(css))
        if (css_bare_import.containsMatchIn(decoded)) return true
        if (css_any_image_set.containsMatchIn(decoded)) return true
        if (css_any_cross_fade.containsMatchIn(decoded)) return true
        return css_url_spans(decoded).any { !css_local_target.containsMatchIn(it.target) }
    }

    fun strip_remote_css_urls(css: String): String {
        val decoded = decode_css_escapes(strip_css_comments(css))
        val spans = css_url_spans(decoded)
        val out = StringBuilder(decoded.length)
        var cursor = 0
        for (span in spans) {
            out.append(decoded, cursor, span.start)
            if (css_local_target.containsMatchIn(span.target)) {
                out.append(decoded, span.start, span.end)
            } else {
                out.append("none")
            }
            cursor = span.end
        }
        out.append(decoded, cursor, decoded.length)
        return out.toString()
            .replace(css_any_image_set, "none")
            .replace(css_any_cross_fade, "none")
            .replace(css_bare_import, "")
    }

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
        val doc = Jsoup.parseBodyFragment(html).apply { outputSettings(raw_output_settings()) }
        for (el in doc.select("[background]")) {
            if (remote_url_prefix.containsMatchIn(el.attr("background").trim())) {
                el.removeAttr("background")
                apply_bg_placeholder(el)
            }
        }
        for (el in doc.select("[style]")) {
            val style = el.attr("style")
            if (has_remote_css_url(style)) {
                el.attr("style", strip_remote_css_urls(style))
                apply_bg_placeholder(el)
            }
        }
        for (st in doc.select("style")) {
            val css = st.data()
            if (has_remote_css_url(css)) st.html(strip_remote_css_urls(css))
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
        val skip_ancestors = setOf("a", "style", "script", "textarea", "code", "pre", "button")
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
            val segments = Autolink.split(tn.wholeText)
            if (segments.none { it.href != null }) continue
            val nodes = mutableListOf<Node>()
            for (segment in segments) {
                val href = segment.href
                if (href == null) {
                    nodes.add(TextNode(segment.text))
                    continue
                }
                val a = Element("a")
                val is_web = href.startsWith("http://") || href.startsWith("https://")
                a.attr("href", if (clean_tracking_links && is_web) strip_tracking_params(href) else href)
                a.attr("target", "_blank")
                a.attr("rel", "noopener noreferrer nofollow")
                a.text(segment.text)
                nodes.add(a)
            }
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
        val head_re = head_block_regex
        val style_re = style_block_regex
        for (head in head_re.findAll(html)) {
            for (m in style_re.findAll(head.value)) {
                result.add(m.groupValues[1])
            }
        }
        return result
    }

    private fun extract_body_html(html: String): String {
        val open = body_open_regex.find(html) ?: return html
        if (!is_document_preamble(html.substring(0, open.range.first))) return html
        val close = body_close_regex.findAll(html).lastOrNull()
            ?: return html.substring(open.range.last + 1)
        if (close.range.first < open.range.last) return html
        return html.substring(open.range.last + 1, close.range.first)
    }

    private fun is_document_preamble(prefix: String): Boolean {
        val stripped = prefix
            .replace(html_comment_regex, "")
            .replace(processing_instruction_regex, "")
            .replace(doctype_regex, "")
            .replace(html_open_regex, "")
            .replace(head_block_regex, "")
            .replace(style_or_title_block_regex, "")
            .replace(document_shell_tag_regex, "")
        return stripped.isBlank()
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
            .addAttributes(
                "img", "src", "alt", "width", "height", "loading", "srcset",
                "sizes", "border", "hspace", "vspace",
            )
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

    private fun strip_mso_conditionals(html: String): String {
        var out = html
        out = out.replace(mso_conditional_regex, "")
        out = out.replace(mso_downlevel_open_regex, "")
        out = out.replace(mso_downlevel_close_regex, "")
        out = out.replace(mso_downlevel_empty_regex, "")
        out = out.replace(mso_endif_regex, "")
        return out
    }

    fun neutralize_amp_markup(html: String): String {
        if (!html.contains("amp", ignoreCase = true)) return html
        var result = html
        while (true) {
            val next = result.replace(amp_boilerplate_style_regex, "")
            if (next == result) break
            result = next
        }
        return result
            .replace(amp_img_open_regex, "<img")
            .replace(amp_img_close_regex, "")
    }

    fun repair_comment_markup(html: String): String {
        return neutralize_unterminated_comments(strip_mso_conditionals(neutralize_amp_markup(html)))
    }

    private fun strip_dangerous_blocks(html: String): String {
        var out = neutralize_amp_markup(html)
        out = strip_mso_conditionals(out)
        out = strip_script_like_blocks(out)
        out = out.replace(embed_tag_regex, "")
        out = out.replace(base_tag_regex, "")
        out = out.replace(meta_refresh_regex, "")
        out = out.replace(link_preload_regex, "")
        out = out.replace(form_tag_regex, "")
        return neutralize_unterminated_comments(out)
    }

    private val comment_end_regex = Regex("--!?>")

    private val meta_refresh_regex =
        Regex("<meta\\b[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*/?>", RegexOption.IGNORE_CASE)

    private val link_preload_regex =
        Regex("<link\\b[^>]*rel\\s*=\\s*[\"']?(?:import|prefetch|preload)[\"']?[^>]*/?>", RegexOption.IGNORE_CASE)

    private val head_block_regex =Regex("<head\\b[\\s>][\\s\\S]*?</head\\s*>", RegexOption.IGNORE_CASE)
    private val style_block_regex = Regex("<style\\b[^>]*>([\\s\\S]*?)</style\\s*>", RegexOption.IGNORE_CASE)
    private val body_open_regex = Regex("<body\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val body_close_regex = Regex("</body\\s*>", RegexOption.IGNORE_CASE)
    private val html_comment_regex = Regex("<!--[\\s\\S]*?-->")
    private val processing_instruction_regex = Regex("<\\?[\\s\\S]*?\\?>")
    private val doctype_regex = Regex("<!doctype\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val html_open_regex = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val style_or_title_block_regex = Regex("<(style|title)\\b[^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE)
    private val document_shell_tag_regex = Regex("</?(head|meta|link|title|base|style)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val mso_conditional_regex = Regex("<!--\\[if\\s[^\\]!]*?mso[^\\]]*?\\]>[\\s\\S]*?<!\\[endif\\]\\s*--\\s*>", RegexOption.IGNORE_CASE)
    private val mso_downlevel_open_regex = Regex("<!--\\[if\\s!mso\\]><!-->\\s*", RegexOption.IGNORE_CASE)
    private val mso_downlevel_close_regex = Regex("\\s*<!--<!\\[endif\\]\\s*--\\s*>", RegexOption.IGNORE_CASE)
    private val mso_downlevel_empty_regex = Regex("<!--\\[if\\s!mso\\]>\\s*<!--\\s*--\\s*>", RegexOption.IGNORE_CASE)
    private val mso_endif_regex = Regex("<!--\\s*<!\\[endif\\]\\s*--\\s*>", RegexOption.IGNORE_CASE)
    private val amp_boilerplate_style_regex = Regex(
        "<style\\b[^>]*?\\samp(?:4email|4ads)?-boilerplate\\b[^>]*>[\\s\\S]*?</style\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val amp_img_open_regex = Regex("<amp-img\\b", RegexOption.IGNORE_CASE)
    private val amp_img_close_regex = Regex("</amp-img\\s*>", RegexOption.IGNORE_CASE)
    private val embed_tag_regex = Regex("<embed\\b[^>]*/?>", RegexOption.IGNORE_CASE)
    private val base_tag_regex = Regex("<base\\b[^>]*/?>", RegexOption.IGNORE_CASE)
    private val form_tag_regex = Regex("<form\\b[^>]*>|</form\\s*>", RegexOption.IGNORE_CASE)
    private val javascript_uri_regex = Regex("^\\s*javascript\\s*:", RegexOption.IGNORE_CASE)
    private val data_html_uri_regex = Regex("^\\s*data\\s*:\\s*text/html", RegexOption.IGNORE_CASE)
    private val vbscript_uri_regex = Regex("^\\s*vbscript\\s*:", RegexOption.IGNORE_CASE)
    private val data_uri_regex = Regex("^\\s*data\\s*:", RegexOption.IGNORE_CASE)
    private val css_expression_regex = Regex("expression\\s*\\(", RegexOption.IGNORE_CASE)
    private val css_javascript_regex = Regex("javascript\\s*:", RegexOption.IGNORE_CASE)
    private val css_vbscript_regex = Regex("vbscript\\s*:", RegexOption.IGNORE_CASE)
    private val css_import_regex = Regex("@import\\b[^;]*;?", RegexOption.IGNORE_CASE)
    private val css_behavior_regex = Regex("behavior\\s*:[^;]*;?", RegexOption.IGNORE_CASE)
    private val css_moz_binding_regex = Regex("-moz-binding\\s*:[^;]*;?", RegexOption.IGNORE_CASE)
    private val css_fixed_position_regex = Regex("position\\s*:\\s*(fixed|sticky)", RegexOption.IGNORE_CASE)
    private val css_charset_regex = Regex("@charset\\b[^;]*;?", RegexOption.IGNORE_CASE)
    private val css_namespace_regex = Regex("@namespace\\b[^;]*;?", RegexOption.IGNORE_CASE)
    private val css_document_regex = Regex("@document\\b[^;]*;?", RegexOption.IGNORE_CASE)
    private val css_moz_document_regex = Regex("-moz-document[^;{]*\\{[^}]*\\}", RegexOption.IGNORE_CASE)
    private val css_image_set_regex = Regex("image-set\\s*\\([^)]*\\)", RegexOption.IGNORE_CASE)
    private val css_webkit_image_set_regex = Regex("-webkit-image-set\\s*\\([^)]*\\)", RegexOption.IGNORE_CASE)
    private val css_cross_fade_regex = Regex("cross-fade\\s*\\([^)]*\\)", RegexOption.IGNORE_CASE)
    private val css_closing_tag_regex = Regex("</(style|script)", RegexOption.IGNORE_CASE)
    private const val media_keyword = "@media"
    private const val max_css_sanitize_passes = 8
    private val media_prelude_terminators = charArrayOf('{', ';')
    private val dark_scheme_regex = Regex("prefers-color-scheme\\s*:\\s*dark", RegexOption.IGNORE_CASE)
    private val media_not_regex = Regex("(^|[\\s(])not\\b", RegexOption.IGNORE_CASE)

    private fun neutralize_unterminated_comments(html: String): String {
        if (!html.contains("<!--")) return html

        val out = StringBuilder()
        var cursor = 0

        while (true) {
            val open = html.indexOf("<!--", cursor)

            if (open < 0) break

            val body_start = open + 4
            val abrupt = when {
                html.startsWith("->", body_start) -> 2
                html.startsWith(">", body_start) -> 1
                else -> 0
            }

            if (abrupt > 0) {
                out.append(html, cursor, open)
                cursor = body_start + abrupt
                continue
            }

            val end = comment_end_regex.find(html, body_start)

            if (end == null) {
                out.append(html, cursor, open)
                cursor = body_start
                if (html.regionMatches(body_start, "[if", 0, 3, ignoreCase = true)) {
                    val tail = html.indexOf('>', body_start)

                    if (tail >= 0) cursor = tail + 1
                }
                break
            }

            out.append(html, cursor, end.range.last + 1)
            cursor = end.range.last + 1
        }

        out.append(html, cursor, html.length)

        return out.toString()
    }

    private val safe_image_data_uri = Regex(
        "^\\s*data:image/(?:jpeg|jpg|png|gif|webp|avif|bmp|tiff|heic|heif|x-icon|vnd\\.microsoft\\.icon)[;,]",
        RegexOption.IGNORE_CASE,
    )

    private fun is_safe_image_data_uri(value: String): Boolean = safe_image_data_uri.containsMatchIn(value)

    private fun scrub_attributes(doc: Document, clean_tracking_links: Boolean = true) {
        val js_uri = javascript_uri_regex
        val data_html_uri = data_html_uri_regex
        val vbscript_uri = vbscript_uri_regex
        val data_uri = data_uri_regex
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
                    } else if (data_uri.containsMatchIn(v) && !is_safe_image_data_uri(v)) {
                        to_remove.add(attr.key)
                    }
                }
                if (key_lower == "style") {
                    val cleaned = sanitize_style_value(attr.value)
                    if (cleaned != attr.value) el.attr(attr.key, cleaned)
                }
            }
            for (k in to_remove) el.removeAttr(k)
            if (el.tagName().equals("img", ignoreCase = true)) reserve_image_box(el)
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

    private const val max_button_label_chars = 48

    private val button_background = Regex(
        "background(-color)?\\s*:\\s*(?!\\s*(transparent|none|inherit|initial))[^;]+",
        RegexOption.IGNORE_CASE,
    )

    fun is_email_button(anchor: Element): Boolean {
        val label = anchor.text().trim()
        if (label.isEmpty() || label.length > max_button_label_chars) return false
        if (anchor.selectFirst("img") != null) return false
        val own_style = anchor.attr("style")
        if (button_background.containsMatchIn(own_style)) return true
        val cell = anchor.parent() ?: return false
        if (!cell.normalName().equals("td", ignoreCase = true)) return false
        if (cell.text().trim() != label) return false
        return cell.hasAttr("bgcolor") || button_background.containsMatchIn(cell.attr("style"))
    }

    private fun mark_email_buttons(doc: Document) {
        for (anchor in doc.select("a")) {
            if (is_email_button(anchor)) anchor.addClass("aster-email-button")
        }
    }

    private val plain_dimension = Regex("^\\d{1,5}$")

    private fun reserve_image_box(img: Element) {
        if (img.hasAttr("loading")) img.attr("loading", "eager")
        val width = img.attr("width").trim()
        val height = img.attr("height").trim()
        if (!plain_dimension.matches(width) || !plain_dimension.matches(height)) return
        if (width == "0" || height == "0") return
        val style = img.attr("style")
        if (style.contains("aspect-ratio", ignoreCase = true)) return
        val ratio = "aspect-ratio:$width/$height"
        img.attr("style", if (style.isBlank()) ratio else style.trimEnd().trimEnd(';') + ";" + ratio)
    }

    private fun scrub_style_blocks(doc: Document, options: SanitizeOptions = SanitizeOptions()) {
        for (el in doc.select("style")) {
            el.html(sanitize_css_block(el.data(), options))
        }
    }

    private fun sanitize_until_stable(css: String, pass: (String) -> String): String {
        var current = css
        repeat(max_css_sanitize_passes) {
            val next = pass(current)
            if (next == current) return next
            current = next
        }
        return ""
    }

    private fun sanitize_style_value(css: String): String =
        sanitize_until_stable(css) { sanitize_style_value_pass(it) }

    private fun sanitize_style_value_pass(css: String): String {
        var out = decode_css_escapes(strip_css_comments(css))
        out = out.replace(css_any_image_set, "none")
        out = out.replace(css_any_cross_fade, "none")
        out = out.replace("<", "")
        out = out.replace(css_expression_regex, "blocked(")
        out = out.replace(css_javascript_regex, "blocked:")
        out = out.replace(css_vbscript_regex, "blocked:")
        out = out.replace(css_import_regex, "")
        out = out.replace(css_behavior_regex, "")
        out = out.replace(css_moz_binding_regex, "")
        out = out.replace(css_fixed_position_regex, "position: relative")
        return out
    }

    private val font_face_block = Regex("@font-face\\s*\\{[^}]*\\}", RegexOption.IGNORE_CASE)

    private fun sanitize_css_block(css: String, options: SanitizeOptions = SanitizeOptions()): String =
        sanitize_until_stable(css) { sanitize_css_block_pass(it, options) }

    private fun sanitize_css_block_pass(css: String, options: SanitizeOptions): String {
        var out = decode_css_escapes(strip_css_comments(css))
        if (options.block_remote_css) {
            out = out.replace(css_import_regex, "")
        }
        if (options.block_remote_fonts) {
            out = font_face_block.replace(out) { m ->
                if (css_url_spans(m.value).any { !css_local_target.containsMatchIn(it.target) }) "" else m.value
            }
        }
        out = out.replace(css_charset_regex, "")
        out = out.replace(css_namespace_regex, "")
        out = out.replace(css_document_regex, "")
        out = out.replace(css_moz_document_regex, "")
        out = out.replace(css_expression_regex, "blocked(")
        out = out.replace(css_javascript_regex, "blocked:")
        out = out.replace(css_vbscript_regex, "blocked:")
        out = out.replace(css_behavior_regex, "")
        out = out.replace(css_moz_binding_regex, "")
        out = out.replace(css_image_set_regex, "none")
        out = out.replace(css_webkit_image_set_regex, "none")
        out = out.replace(css_cross_fade_regex, "none")
        out = strip_dark_mode_media(out)
        out = out.replace(css_fixed_position_regex, "position: relative")
        out = out.replace(css_closing_tag_regex, """<\\/$1""")
        return out
    }

    private fun strip_dark_mode_media(css: String): String {
        val out = StringBuilder()
        var cursor = 0
        var search = 0
        while (true) {
            val at = css.indexOf(media_keyword, search, ignoreCase = true)
            if (at < 0) break
            val prelude_start = at + media_keyword.length
            val prelude_end = css.indexOfAny(media_prelude_terminators, prelude_start)
            if (prelude_end < 0) break
            val body_start = prelude_end + 1
            search = body_start
            val next = css[prelude_start]
            if (css[prelude_end] == ';' || next.isLetterOrDigit() || next == '_') continue
            val queries = split_media_queries(css.substring(prelude_start, prelude_end))
            val kept = queries.filterNot { requires_dark_scheme(it) }
            if (kept.size == queries.size) continue
            out.append(css, cursor, at)
            if (kept.isEmpty()) {
                cursor = css_block_end(css, body_start)
                search = cursor
            } else {
                out.append("@media ").append(kept.joinToString(", ") { it.trim() }).append(" {")
                cursor = body_start
            }
        }
        out.append(css, cursor, css.length)
        return out.toString()
    }

    private fun requires_dark_scheme(query: String): Boolean =
        dark_scheme_regex.containsMatchIn(query) && !media_not_regex.containsMatchIn(query)

    private fun split_media_queries(prelude: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in prelude.indices) {
            when (prelude[i]) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) {
                    parts.add(prelude.substring(start, i))
                    start = i + 1
                }
            }
        }
        parts.add(prelude.substring(start))
        return parts
    }

    private fun css_block_end(css: String, body_start: Int): Int {
        var depth = 1
        var quote: Char? = null
        var i = body_start
        while (i < css.length) {
            val c = css[i]
            if (quote != null) {
                if (c == '\\') i++ else if (c == quote) quote = null
            } else {
                when (c) {
                    '"', '\'' -> quote = c
                    '{' -> depth++
                    '}' -> if (--depth == 0) return i + 1
                }
            }
            i++
        }
        return css.length
    }

    fun rewrite_img_through_proxy(html: String, proxy_base: String, allow_external: Boolean): String {
        if (html.isBlank()) return html
        val doc = Jsoup.parseBodyFragment(html).apply { outputSettings(raw_output_settings()) }
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
