//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// AGPL-3.0-or-later
//

package org.astermail.android.ui.mail

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

object EmailHtmlSanitizer {

    private val safelist: Safelist by lazy { build_safelist() }

    fun sanitize(raw_html: String): String {
        if (raw_html.isBlank()) return ""
        val pre = strip_dangerous_blocks(raw_html)
        val head_styles = extract_head_styles(pre)
        val body_only = extract_body_html(pre)
        val cleaned_body = Jsoup.clean(body_only, "https://mail-content.invalid/", safelist)
        val doc = Jsoup.parseBodyFragment(cleaned_body)
        scrub_attributes(doc)
        scrub_style_blocks(doc)
        val sb = StringBuilder()
        for (css in head_styles) {
            val safe_css = sanitize_style_value(css)
            if (safe_css.isNotBlank()) sb.append("<style>").append(safe_css).append("</style>")
        }
        sb.append(doc.body().html())
        return sb.toString()
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
            .addAttributes("td", "colspan", "rowspan", "bgcolor", "valign", "align", "width", "height")
            .addAttributes("th", "colspan", "rowspan", "bgcolor", "valign", "align", "width", "height")
            .addAttributes("tr", "bgcolor", "valign", "align")
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
        out = out.replace(Regex("<!--\\[if[^\\]]*\\]>[\\s\\S]*?<!\\[endif\\]-->", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<script\\b[^>]*>[\\s\\S]*?</script\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<iframe\\b[^>]*>[\\s\\S]*?</iframe\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<object\\b[^>]*>[\\s\\S]*?</object\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<embed\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<applet\\b[^>]*>[\\s\\S]*?</applet\\s*>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<base\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<meta\\b[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<link\\b[^>]*rel\\s*=\\s*[\"']?(?:import|prefetch|preload)[\"']?[^>]*/?>", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("<form\\b[^>]*>[\\s\\S]*?</form\\s*>", RegexOption.IGNORE_CASE), "")
        return out
    }

    private fun scrub_attributes(doc: Document) {
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
                if (el.hasAttr("target")) {
                    el.attr("target", "_blank")
                    el.attr("rel", "noopener noreferrer nofollow")
                }
            }
        }
    }

    private fun scrub_style_blocks(doc: Document) {
        for (el in doc.select("style")) {
            el.html(sanitize_style_value(el.data()))
        }
    }

    private fun sanitize_style_value(css: String): String {
        var out = css
        out = out.replace(Regex("expression\\s*\\(", RegexOption.IGNORE_CASE), "blocked(")
        out = out.replace(Regex("javascript\\s*:", RegexOption.IGNORE_CASE), "blocked:")
        out = out.replace(Regex("vbscript\\s*:", RegexOption.IGNORE_CASE), "blocked:")
        out = out.replace(Regex("@import\\b[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("behavior\\s*:[^;]*;?", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("-moz-binding\\s*:[^;]*;?", RegexOption.IGNORE_CASE), "")
        return out
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
