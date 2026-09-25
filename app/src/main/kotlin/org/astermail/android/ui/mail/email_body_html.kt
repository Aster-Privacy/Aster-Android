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

import org.astermail.android.mail.body_starts_with

private val WIDE_WIDTH_ATTRIBUTE_VALUE = Regex("""[4-9]\d{2,3}""")

internal const val BODY_SIDE_PADDING = 16

internal fun fit_wide_width_attributes(body: String): String {
    if (!body.contains("width", ignoreCase = true)) return body
    return try {
        val doc = org.jsoup.Jsoup.parseBodyFragment(body)
        doc.outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
        var changed = false
        for (element in doc.select("[width]")) {
            if (WIDE_WIDTH_ATTRIBUTE_VALUE.matches(element.attr("width").trim())) {
                element.attr("width", "100%")
                changed = true
            }
        }
        if (changed) doc.body().html() else body
    } catch (_: Throwable) {
        body
    }
}

internal fun build_email_html(
    body: String,
    is_dark: Boolean,
    fg_hex: String,
    link_hex: String,
    forwarded_label: String,
    image_failed_label: String,
    force_dark_emails: Boolean,
    forced_dark_canvas: Boolean = false,
    dyslexia_font: Boolean,
    translate_mode: String,
    email_font_id: String? = null,
    text_zoom: Int = 100,
    underline_links: Boolean = false,
    scrollable: Boolean = false,
): String {
    val chip_scale = maxOf(1f, text_zoom.coerceIn(50, 300) / 100f)
    fun scaled_px(value: Float): String = String.format(java.util.Locale.US, "%.1fpx", value * chip_scale)
    val is_html_body = body_starts_with(body, "<")
    val has_table = is_html_body && body.contains(Regex("<table", RegexOption.IGNORE_CASE))
    val has_newsletter_layout = has_table && (
        body.contains(Regex("style\\s*=\\s*[\"'][^\"']*width\\s*:\\s*[456789]\\d{2}px", RegexOption.IGNORE_CASE)) ||
        body.contains(Regex("<table[^>]*(?:width|bgcolor|background)\\s*=", RegexOption.IGNORE_CASE)) ||
        (body.split(Regex("<table\\b", RegexOption.IGNORE_CASE)).size - 1) > 2
    )
    val render_body = if (has_newsletter_layout) {
        body
            .replace(Regex("""(?<!\(\s{0,8})\bmin-width\s*:\s*([1-9]\d{2,3})px""", RegexOption.IGNORE_CASE), "min-width:$1px;min-width:min($1px,100%)")
            .replace(Regex("""(?<!\(\s{0,8})(?<![a-z-])width\s*:\s*[4-9]\d{2,3}px""", RegexOption.IGNORE_CASE), "width:100%")
            .let { fit_wide_width_attributes(it) }
    } else {
        body
    }
    val declares_light = body.contains(Regex("color-scheme\\s*:\\s*light\\s+only", RegexOption.IGNORE_CASE))
    val declares_light_bg = is_html_body && body.contains(
        Regex(
            "(?:background(?:-color)?\\s*:\\s*(?:#fff(?:fff)?|white|rgb\\(\\s*25[0-5])|bgcolor\\s*=\\s*[\"']?(?:#fff(?:fff)?|white))",
            RegexOption.IGNORE_CASE,
        ),
    )
    val seeded_background = detect_body_background(render_body)
    val designed_light =
        declares_light || declares_light_bg || has_newsletter_layout || background_reads_light(seeded_background)
    val white_page = is_dark && is_html_body && designed_light && !force_dark_emails
    val simple_dark = is_dark && is_html_body && !white_page
    val force_light = is_html_body && !simple_dark
    val chip_dark = is_dark && !white_page

    val sys_font = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"
    val web_font = org.astermail.android.design.email_web_font_for(email_font_id)
    val user_font = when {
        web_font != null -> "'${web_font.family}',${web_font.fallback}"
        else -> org.astermail.android.design.email_generic_font_stack(email_font_id) ?: sys_font
    }
    val body_font = if (dyslexia_font) sys_font else user_font
    val body_style = when {
        is_html_body && !has_newsletter_layout ->
            "background-color:transparent;color:${if (simple_dark) "#e5e5e5" else if (white_page) "#111827" else fg_hex};margin:0;padding:4px 16px 0 16px;font-family:$body_font;font-size:16px;line-height:1.6;word-wrap:break-word"
        is_html_body ->
            "background-color:transparent;margin:0;padding:4px 16px 0 16px"
        else ->
            "background-color:transparent;color:$fg_hex;margin:0;padding:4px 16px 6px 16px;font-family:$body_font;font-size:16px;line-height:1.55;word-wrap:break-word"
    }
    val user_font_css = if (!dyslexia_font && web_font != null) {
        web_font.faces.joinToString("") { face ->
            "@font-face{font-family:'${web_font.family}';font-style:normal;font-weight:${face.weight};font-display:swap;" +
                "src:url('$EMAIL_USER_FONT_PREFIX${web_font.id}__${face.slot}.ttf') format('truetype')}"
        } + "body,body *:not(code):not(pre):not(kbd):not(samp):not(font){font-family:$user_font!important}"
    } else if (!dyslexia_font && email_font_id == "system_mono") {
        "body,body *:not(code):not(pre):not(kbd):not(samp):not(font){font-family:$user_font!important}"
    } else {
        ""
    }

    val dark_css = when {
        simple_dark -> """
html{color-scheme:dark}
html,body{background-color:transparent!important;color:#e8e8e8!important}
"""
        white_page -> """
html,body{background-color:#ffffff!important}
"""
        else -> ""
    }

    val underline_css = if (underline_links) "a{text-decoration:underline!important}" else ""

    val dyslexia_css = if (dyslexia_font) {
        "@font-face{font-family:'AsterDyslexic';font-style:normal;font-weight:400;font-display:swap;src:url('$EMAIL_FONT_PATH') format('opentype')}" +
            "body,body *:not(code):not(pre):not(kbd):not(samp):not(font){font-family:'AsterDyslexic',$sys_font!important}"
    } else {
        ""
    }

    val table_css = if (has_newsletter_layout) {
        "#m{max-width:100%!important;overflow-x:hidden!important;box-sizing:border-box!important}#m table{max-width:100%!important;box-sizing:border-box!important}#m img{max-width:100%!important;height:auto!important}#m div,#m p,#m blockquote,#m section,#m article{box-sizing:border-box!important;max-width:100%!important}td,th{box-sizing:border-box!important;max-width:100%!important}#m,#m *{word-break:normal!important;overflow-wrap:break-word!important;word-wrap:break-word!important}#m a{overflow-wrap:anywhere!important}"
    } else {
        "table{max-width:100%!important;border-collapse:collapse;width:100%!important}td,th{overflow-wrap:break-word}"
    }
    val fit_content_width = declared_content_width(render_body)
    val viewport_meta = if (fit_content_width != null) {
        "<meta name=\"viewport\" content=\"width=${fit_content_width + BODY_SIDE_PADDING * 2},maximum-scale=5,user-scalable=yes\">"
    } else {
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes\">"
    }
    val color_scheme_meta = if (force_light) "<meta name=\"color-scheme\" content=\"light only\">" else ""

    val bq_border = if (simple_dark) "#4b5563" else "#dadce0"
    val bq_color = if (simple_dark) "#9ca3af" else "#5f6368"
    val bq_border2 = if (simple_dark) "#444" else "#dadce0"
    val bq_border3 = if (simple_dark) "#555" else "#c4c7cc"
    val detail_border = if (simple_dark) "#374151" else "#e5e7eb"
    val detail_color = if (simple_dark) "#9ca3af" else "#6b7280"

    val forced_dark_css = if (force_dark_emails) {
        forced_dark_mode_css(
            link_hex,
            "#4b5563",
            "#9ca3af",
            if (forced_dark_canvas) FORCED_DARK_CANVAS else "transparent",
        )
    } else {
        ""
    }
    val dark_ready_body = if (force_dark_emails) lighten_dark_email_text(render_body) else render_body

    val prepared_body = prepare_email_body(
        body = dark_ready_body,
        forwarded_label = forwarded_label,
        image_failed_label = image_failed_label,
        is_newsletter = has_newsletter_layout,
        simple_dark = simple_dark,
    )

    val csp_meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src https://app.astermail.org https://mail-content.invalid data:; style-src 'unsafe-inline'; font-src https://app.astermail.org https://mail-content.invalid data:; script-src 'none'; worker-src 'none'; connect-src 'none'; base-uri 'none'; form-action 'none'; frame-src 'none'; object-src 'none'\">"

    return """<!DOCTYPE html><html${if (has_newsletter_layout) " data-nl=\"1\"" else ""}${if (white_page) " data-white=\"1\"" else ""}${if (simple_dark) " data-dark=\"1\"" else ""}${if (force_dark_emails) " data-dark-force=\"1\"" else ""}${if (is_html_body && !simple_dark) " style=\"background-color:transparent\"" else ""}><head>
$csp_meta
<meta charset="utf-8">
$viewport_meta
$color_scheme_meta
<style>
html{height:auto!important;min-height:0!important;background-color:transparent;-webkit-text-size-adjust:100%;text-size-adjust:100%}
body{height:auto!important;min-height:0!important;margin:0;overflow-x:hidden;overflow-y:${if (scrollable) "auto" else "hidden"}}
*{box-sizing:border-box}
[height="100%"],[height='100%']{height:auto!important}
[style*="height:100%"],[style*="height: 100%"],[style*="height:100vh"],[style*="height: 100vh"],[style*="height:100dvh"],[style*="height: 100dvh"]{height:auto!important;min-height:0!important}
img{max-width:100%!important;height:auto!important}
img:not([data-blocked='true']):not(.blocked-image){cursor:zoom-in;-webkit-tap-highlight-color:rgba(128,128,128,0.22)}
a img{cursor:pointer}
a.aster-image-zoom{text-decoration:none;color:inherit;-webkit-tap-highlight-color:rgba(128,128,128,0.22)}
a.aster-image-zoom img{cursor:zoom-in}
a{color:$link_hex;text-decoration:underline;-webkit-tap-highlight-color:transparent}
pre,code{overflow-x:auto;max-width:100%}
#m img[data-aster-failed-label]::after{content:attr(data-aster-failed-label);display:inline-block;padding:4px 8px;border-radius:4px;font-size:12px;background-color:${if (simple_dark) "#1f1f1f" else "#f3f4f6"};color:#9ca3af;border:1px dashed ${if (simple_dark) "#374151" else "#e5e7eb"}}
.blocked-image{display:inline-block;padding:4px 8px;border-radius:4px;font-size:12px;background-color:${if (simple_dark) "#1f1f1f" else "#f3f4f6"};color:#9ca3af${if (simple_dark) "!important" else ""};border:1px dashed ${if (simple_dark) "#374151" else "#e5e7eb"}}
$table_css
a.aster-email-button,#m a.aster-email-button{white-space:nowrap!important;word-break:keep-all!important;overflow-wrap:normal!important;max-width:100%!important}
.aster_quote,.gmail_quote,.protonmail_quote,.yahoo_quoted,.moz-cite-prefix{display:none}
.aster-quoted-content .aster_quote,.aster-quoted-content .gmail_quote,.aster-quoted-content .protonmail_quote,.aster-quoted-content .yahoo_quoted,.aster-quoted-content .moz-cite-prefix,.aster-forwarded-content .aster_quote,.aster-forwarded-content .gmail_quote,.aster-forwarded-content .protonmail_quote{display:block;margin:0;padding:0}
blockquote{margin:8px 0;padding-left:12px;border-left:2px solid $bq_border;color:$bq_color}
details.aster-quoted-wrapper{margin-top:18px;margin-bottom:4px}
.aster-quote-toggle{display:inline-flex;align-items:center;justify-content:center;min-height:${scaled_px(28f)};min-width:${scaled_px(44f)};padding:0 ${scaled_px(16f)};margin:0;border-radius:${scaled_px(14f)};border:none;outline:none;background:${if (chip_dark) "rgba(255,255,255,0.12)" else "rgba(0,0,0,0.08)"};color:${if (chip_dark) "rgba(255,255,255,0.65)" else "rgba(0,0,0,0.55)"};cursor:pointer;font-family:inherit;font-size:0;letter-spacing:0;line-height:0;vertical-align:middle;user-select:none;list-style:none;-webkit-tap-highlight-color:transparent;transition:background 0.12s ease}
.aster-quote-toggle::-webkit-details-marker{display:none}
.aster-quote-toggle::before{content:'';display:block;flex:none;width:${scaled_px(4f)};height:${scaled_px(4f)};border-radius:50%;background:currentColor;box-shadow:${scaled_px(-6.4f)} 0 0 currentColor,${scaled_px(6.4f)} 0 0 currentColor}
.aster-quote-toggle::marker{content:''}
.aster-quote-toggle:active,details[open].aster-quoted-wrapper>.aster-quote-toggle{background:${if (chip_dark) "rgba(255,255,255,0.2)" else "rgba(0,0,0,0.16)"}}
.aster-quoted-content{margin-top:14px;padding-top:14px;border-top:1px solid $detail_border;color:$bq_color;font-family:inherit;font-size:1em;line-height:1.45}
.aster-quoted-content.aster-quoted-solo{margin-top:0;padding-top:0;border-top:none}
.aster-quoted-content .aster_quote_attr,.aster-quoted-content .gmail_attr{color:$bq_color;font-size:0.82em;margin-bottom:4px}
.aster-quoted-content blockquote{margin:0;padding:0 0 0 12px;border-left:2px solid $bq_border2;color:$bq_color}
.aster-quoted-content blockquote blockquote{border-left-color:$bq_border3}
details.aster-forwarded-collapse{margin-top:12px;border-top:1px solid $detail_border;padding-top:4px}
details.aster-forwarded-collapse>summary{cursor:pointer;color:$detail_color;font-family:inherit;font-size:0.9em;padding:6px 0;user-select:none;list-style:none}
details.aster-forwarded-collapse>summary::-webkit-details-marker{display:none}
details.aster-forwarded-collapse>summary::before{content:'\25B6';display:inline-block;font-size:0.62em;margin-right:6px;transition:transform 0.15s ease}
details[open].aster-forwarded-collapse>summary::before{transform:rotate(90deg)}
details.aster-forwarded-collapse>.aster-forwarded-content{padding-top:8px}
$user_font_css
$underline_css
$dyslexia_css
$dark_css
$forced_dark_css
</style>
</head><body style="$body_style"><div id="m"${if (seeded_background != null) " style=\"background-color:$seeded_background\"" else ""}>$prepared_body</div>
</body></html>"""
}
