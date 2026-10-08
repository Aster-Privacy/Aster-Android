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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailDarkContrastTest {

    private fun render(body: String, forced: Boolean, theme_dark: Boolean = true): String =
        build_email_html(
            body = body,
            is_dark = theme_dark,
            fg_hex = if (theme_dark) "#E8E8E8" else "#111827",
            link_hex = "#8ab4f8",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image unavailable",
            force_dark_emails = forced,
            dyslexia_font = false,
            translate_mode = "off",
        )

    private val inline_background = Regex("background(?:-color)?\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
    private val color_token = Regex("#[0-9a-fA-F]{3,8}\\b|rgba?\\([^)]*\\)|\\bwhite\\b", RegexOption.IGNORE_CASE)
    private val css_rule = Regex("([^{}]+)\\{([^{}]*)\\}")
    private val color_declaration = Regex("(?<![-a-z])color\\s*:\\s*([^;}\"']+)", RegexOption.IGNORE_CASE)

    private fun reads_light(value: String): Boolean =
        color_token.findAll(value).any { background_reads_light(it.value) || is_page_surface(it.value) }

    private fun declares_light_background(css: String): Boolean =
        inline_background.findAll(css).any { reads_light(it.groupValues[1]) }

    private fun light_surfaces(doc: Document, content: Element): Set<Element> {
        val found = LinkedHashSet<Element>()
        if (declares_light_background(content.attr("style"))) found.add(content)
        for (element in content.select("[bgcolor]")) if (reads_light(element.attr("bgcolor"))) found.add(element)
        for (element in content.select("[style]")) if (declares_light_background(element.attr("style"))) found.add(element)
        for (sheet in content.select("style")) {
            for (rule in css_rule.findAll(sheet.data())) {
                if (!declares_light_background(rule.groupValues[2])) continue
                for (part in rule.groupValues[1].split(',')) {
                    val selector = part.trim()
                    if (selector.isEmpty() || selector.startsWith("@")) continue
                    val matches = runCatching { doc.select(selector) }.getOrNull() ?: continue
                    found.addAll(matches.filter { it != doc.body() && it.tagName() != "html" })
                }
            }
        }
        return found
    }

    private fun dark_ink(content: Element): List<String> {
        val inks = mutableListOf<String>()
        for (element in content.select("[style]")) {
            color_declaration.findAll(element.attr("style")).forEach { inks.add(it.groupValues[1].removeSuffix("!important").trim()) }
        }
        for (sheet in content.select("style")) {
            color_declaration.findAll(sheet.data()).forEach { inks.add(it.groupValues[1].removeSuffix("!important").trim()) }
        }
        for (font in content.select("font[color]")) inks.add(font.attr("color"))
        return inks.filter { reads_too_dark_on_dark(it) }
    }

    private val important_background = Regex("background(?:-color)?\\s*:\\s*([^;}]*!\\s*important)", RegexOption.IGNORE_CASE)

    private fun important_light_backgrounds(content: Element): List<String> =
        content.select("style").flatMap { sheet ->
            css_rule.findAll(sheet.data())
                .filter { rule -> !declares_background_image(rule.groupValues[2]) }
                .filter { rule -> important_background.findAll(rule.groupValues[2]).any { reads_light(it.groupValues[1]) } }
                .map { it.value.trim() }
                .toList()
        }

    private val exclusion = Regex(":not\\(\\[([a-z-]+)(?:\\*=\"([^\"]*)\"(?: i)?)?\\]\\)")

    private fun forced_neutralizer(html: String): (Element) -> Boolean {
        val css = html.substringAfter("<style>").substringBefore("</style>")
        val line = css.lines().first { it.endsWith("{background-color:transparent!important;background-image:none!important}") }
        val parts = line.substringBefore("{background-color:transparent!important").split(",")
        val tags = parts.map { it.substringBefore(":not(").trim() }.toSet()
        val exclusions = exclusion.findAll(parts.first()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        return { element ->
            element.tagName() in tags && exclusions.none { (name, fragment) ->
                element.hasAttr(name) && (fragment.isEmpty() || element.attr(name).contains(fragment, ignoreCase = true))
            }
        }
    }

    private fun assert_consistent(label: String, html: String) {
        val doc = Jsoup.parse(html)
        val root = doc.selectFirst("html")!!
        val content = doc.getElementById("m")!!
        val surfaces = light_surfaces(doc, content)
        val inks = dark_ink(content)
        when {
            root.hasAttr("data-dark-force") -> {
                val neutralized = forced_neutralizer(html)
                val kept = surfaces.filter { !neutralized(it) }
                assertTrue("$label: light backgrounds kept under forced dark: ${kept.map { it.cssSelector() }}", kept.isEmpty())
                val outranking = important_light_backgrounds(content)
                assertTrue("$label: important light backgrounds outrank forced dark: $outranking", outranking.isEmpty())
                assertTrue("$label: dark text left under forced dark: $inks", inks.isEmpty())
            }
            root.hasAttr("data-dark") -> {
                assertTrue("$label: light backgrounds kept behind light text: ${surfaces.map { it.cssSelector() }}", surfaces.isEmpty())
                assertTrue("$label: dark text left on the dark page: $inks", inks.isEmpty())
            }
            else -> {
                assertFalse("$label: a light page must keep the authored text: $html", content.html().contains(FORCED_DARK_INK))
                assertFalse("$label: a light page must keep the authored text: $html", content.html().contains("color:#e8e8e8"))
            }
        }
    }

    private fun assert_consistent_in_dark(label: String, body: String) {
        assert_consistent("$label (forced)", render(body, forced = true))
        assert_consistent("$label (automatic)", render(body, forced = false))
    }

    @Test
    fun a_body_background_from_the_style_attribute_is_handled_with_its_text() {
        val raw = "<!doctype html><html><head><style>h1{color:#202020}</style></head>" +
            "<body style=\"background-color:#e7e7e7\"><h1>Weekly digest</h1>" +
            "<p style=\"color:#222222\">Here is what changed this week.</p><p>Plain line</p></body></html>"
        assert_consistent_in_dark("body style", EmailHtmlSanitizer.sanitize(raw))
    }

    @Test
    fun bgcolor_on_a_table_cell_is_handled_with_its_text() {
        assert_consistent_in_dark(
            "td bgcolor",
            "<table><tr><td bgcolor=\"#f2f2f2\" style=\"background-image:none\">" +
                "<p>Plain line</p><p style=\"color:#222222\">Dark line</p></td></tr></table>",
        )
    }

    @Test
    fun bgcolor_on_a_table_is_handled_with_its_text() {
        assert_consistent_in_dark(
            "table bgcolor",
            "<table bgcolor=\"#eeeeee\" style=\"background-image: none\"><tr><td>" +
                "<p>Plain line</p><p style=\"color:#000000\">Dark line</p></td></tr></table>",
        )
    }

    @Test
    fun a_background_from_a_style_rule_is_handled_with_its_text() {
        assert_consistent_in_dark(
            "style rule",
            "<style>.page{background-color:#ededed}.ink{color:#202020}</style>" +
                "<div class=\"page\" style=\"background-image:none\"><p class=\"ink\">Styled line</p><p>Plain line</p></div>",
        )
    }

    @Test
    fun a_light_container_nested_in_a_dark_page_is_handled_with_its_text() {
        assert_consistent_in_dark(
            "nested light box",
            "<div style=\"background-color:#1b1b1b\"><p style=\"color:#f5f5f5\">Dark intro</p>" +
                "<div style=\"background-color:#f4f4f4;background-image:none\"><p>Plain line</p>" +
                "<p style=\"color:#222222\">Dark line</p></div></div>",
        )
    }

    private val sectioned_newsletter =
        "<!doctype html><html><head><style>body,#outer{background-color:#e7e7e7}" +
            "h1{color:#202020}.copy{color:#222222}</style></head>" +
            "<body style=\"background-color:#e7e7e7\"><center><table id=\"outer\" width=\"100%\"><tr>" +
            "<td style=\"background:#e7e7e7 none no-repeat center/cover;background-color:#e7e7e7;background-image:none;padding:9px\">" +
            "<table width=\"600\"><tr><td><h1>Autumn workshops</h1>" +
            "<p class=\"copy\">Book a seat for the baking class.</p>" +
            "<p style=\"color:#000000\">Places are limited.</p>" +
            "<p><span style=\"background-color:#ff0000\"><span style=\"color:#FFFFFF\">Free entry</span></span></p>" +
            "</td></tr></table></td></tr></table></center></body></html>"

    @Test
    fun a_sectioned_newsletter_with_background_image_none_is_darkened_as_a_whole() {
        val html = render(EmailHtmlSanitizer.sanitize(sectioned_newsletter), forced = true)
        assert_consistent("sectioned newsletter", html)
        val doc = Jsoup.parse(html)
        val highlight = doc.select("#m span[style*=ff0000]").first()!!
        assertTrue(highlight.outerHtml(), highlight.hasAttr(KEEP_BACKGROUND_ATTRIBUTE))
    }

    @Test
    fun a_sectioned_newsletter_stays_light_without_forced_dark() {
        val html = render(EmailHtmlSanitizer.sanitize(sectioned_newsletter), forced = false)
        assertTrue(html.contains("data-white=\"1\""))
        assert_consistent("sectioned newsletter", html)
    }

    private val builder_newsletter =
        "<!doctype html><html><head><style>body,td{font-family:Arial,sans-serif}" +
            ".wrap-table{background-color:#e9eef4}.copy{color:#1f3b57}</style>" +
            "<style>@media only screen and (max-width:639px){#panel-1 .col-box{background-color:#fff !important}}" +
            "@media only screen and (min-width:640px){.col-box{max-width:600px !important}}" +
            "#panel-2 .col-box{background-color:transparent !important}</style></head>" +
            "<body bgcolor=\"#e9eef4\" style=\"margin:0;color:#1f3b57\">" +
            "<table class=\"wrap-table\" width=\"100%\" bgcolor=\"#e9eef4\"><tr><td>" +
            "<div id=\"panel-1\"><div class=\"col-box\" style=\"max-width:600px;background-color:#ffffff\">" +
            "<table width=\"600\" bgcolor=\"#ffffff\"><tr><td bgcolor=\"#ffffff\" style=\"background-color:#ffffff\">" +
            "<h1>Garden club news</h1><p>The plant swap is on Saturday.</p>" +
            "<p class=\"copy\">Bring cuttings or spare pots.</p></td></tr></table></div></div>" +
            "<div id=\"panel-2\"><div class=\"col-box\"><p class=\"copy\">You joined the garden club list.</p></div></div>" +
            "</td></tr></table></body></html>"

    private fun style_blocks(html: String): String =
        Jsoup.parse(html).getElementById("m")!!.select("style").joinToString("\n") { it.data() }

    @Test
    fun an_important_light_background_inside_a_media_query_is_cleared_under_forced_dark() {
        val html = render(EmailHtmlSanitizer.sanitize(builder_newsletter), forced = true)
        assert_consistent("builder newsletter", html)
        val css = style_blocks(html)
        assertTrue(css, css.contains("#panel-1 .col-box{background-color:transparent !important}"))
        assertTrue(css, css.contains(".copy{color:$FORCED_DARK_INK}"))
        assertTrue(css, css.contains("@media only screen and (max-width:639px)"))
    }

    @Test
    fun an_important_id_and_class_rule_cannot_outrank_forced_dark() {
        for (rule in listOf(
            "#hero .box{background-color:#ffffff !important}",
            "#hero .box{background:#FFF!important;padding:4px}",
            "div#hero > .box.card{background-color:rgb(255, 255, 255) !important}",
            "#hero .box{background-color:white !IMPORTANT}",
        )) {
            val body = "<style>$rule.ink{color:#1f3b57}</style><div id=\"hero\"><div class=\"box card\">" +
                "<p class=\"ink\">Styled line</p><p>Plain line</p></div></div>"
            val html = render(body, forced = true)
            assert_consistent(rule, html)
            val css = style_blocks(html)
            assertTrue(css, css.contains("transparent"))
            assertTrue(css, css.contains(".ink{color:$FORCED_DARK_INK}"))
        }
    }

    @Test
    fun body_and_cell_bgcolor_with_text_from_body_and_classes_stay_in_step() {
        val raw = "<!doctype html><html><head><style>.lead{color:#1f3b57}" +
            "@media (max-width:568px){#s .cell{background-color:#fff !important}}</style></head>" +
            "<body bgcolor=\"#e9eef4\" style=\"color:#1f3b57\"><table id=\"s\" width=\"100%\"><tr>" +
            "<td class=\"cell\" bgcolor=\"#ffffff\" style=\"background-color:#ffffff;color:#1f3b57\">" +
            "<p class=\"lead\">Release notes</p><p>Plain line</p></td></tr></table></body></html>"
        assert_consistent_in_dark("body and cell bgcolor", EmailHtmlSanitizer.sanitize(raw))
    }

    @Test
    fun without_forced_dark_a_builder_newsletter_stays_light_with_its_own_text() {
        val html = render(EmailHtmlSanitizer.sanitize(builder_newsletter), forced = false)
        assertTrue(html.contains("data-white=\"1\""))
        assert_consistent("builder newsletter", html)
        val css = style_blocks(html)
        assertTrue(css, css.contains("#panel-1 .col-box{background-color:#fff !important}"))
        assertTrue(css, css.contains(".copy{color:#1f3b57}"))
        val light = render(EmailHtmlSanitizer.sanitize(builder_newsletter), forced = forces_dark_emails(preference = true, theme_dark = false), theme_dark = false)
        assertFalse(light, light.contains("data-dark"))
        assertEquals(css, style_blocks(light))
    }

    @Test
    fun important_background_images_in_a_stylesheet_keep_their_colours_under_forced_dark() {
        val rule = "#hero .box{background:#ffffff url(https://mail-content.invalid/hero.png) no-repeat !important;color:#ffffff}"
        val html = render(
            "<style>$rule</style><div id=\"hero\"><div class=\"box\"><p>Hero</p></div></div>",
            forced = true,
        )
        val css = style_blocks(html)
        assertTrue(css, css.contains(rule))
        val box = Jsoup.parse(html).selectFirst("#m .box")!!
        assertTrue(box.outerHtml(), box.hasAttr(BACKGROUND_IMAGE_ATTRIBUTE))
    }

    @Test
    fun translucent_and_brand_important_backgrounds_are_left_alone_under_forced_dark() {
        val rule = "#a .tint{background-color:rgba(255,255,255,0.2) !important}#a .brand{background-color:#0b5394 !important}"
        val html = render("<style>$rule</style><div id=\"a\"><div class=\"tint\">One</div><div class=\"brand\">Two</div></div>", forced = true)
        assertTrue(style_blocks(html), style_blocks(html).contains(rule))
    }

    @Test
    fun the_light_theme_keeps_every_case_as_authored() {
        for (body in listOf(
            EmailHtmlSanitizer.sanitize(sectioned_newsletter),
            "<style>.page{background-color:#ededed}.ink{color:#202020}</style><div class=\"page\"><p class=\"ink\">Styled</p></div>",
            "<table><tr><td bgcolor=\"#f2f2f2\"><p style=\"color:#222222\">Dark line</p></td></tr></table>",
        )) {
            val html = render(body, forced = forces_dark_emails(preference = true, theme_dark = false), theme_dark = false)
            assertFalse(html, html.contains("data-dark"))
            assertFalse(html, html.contains("data-white"))
            assertFalse(html, html.contains(FORCED_DARK_INK))
            assertTrue(html, html.contains("#222222") || html.contains("#202020"))
        }
    }

    @Test
    fun real_background_images_keep_their_section_under_forced_dark() {
        for (style in listOf(
            "background-color:#ffffff;background-image:url(https://mail-content.invalid/hero.png)",
            "background-image:linear-gradient(#ffffff, #eeeeee)",
            "background-image: url('cid:hero@example.com') !important",
        )) {
            val html = render("<table><tr><td style=\"$style\"><p>Hero</p></td></tr></table>", forced = true)
            val doc = Jsoup.parse(html)
            val cell = doc.selectFirst("#m td")!!
            assertFalse(style, forced_neutralizer(html)(cell))
        }
    }

    @Test
    fun background_image_none_in_any_spelling_is_not_an_image() {
        for (value in listOf("none", "none !important", "NONE", "initial", "unset", "inherit")) {
            val html = render(
                "<table><tr><td style=\"background-color:#ffffff;background-image:$value\"><p style=\"color:#111111\">Body</p></td></tr></table>",
                forced = true,
            )
            val doc = Jsoup.parse(html)
            val cell = doc.selectFirst("#m td")!!
            assertTrue(value, forced_neutralizer(html)(cell))
            assertEquals(value, 0, dark_ink(doc.getElementById("m")!!).size)
        }
    }

    private val dispatch_email =
        "<!doctype html><html><head><style>.body,body,html{background:#fff\n!important;margin:0}" +
            "h1,h2{color:#4c12a0}.copy{color:#333333}</style></head>" +
            "<body class=\"body\"><table width=\"100%\" bgcolor=\"#f5f5f5\"><tr><td>" +
            "<table width=\"600\" style=\"background-color:#fff\"><tr><td>" +
            "<h1>Your items are on their way</h1>" +
            "<p class=\"copy\">Your items have been dispatched.</p>" +
            "<h2>Order information</h2><p style=\"color:#000\">Order number: 1234</p>" +
            "<a href=\"https://example.com/track\" style=\"background-color:#ffffff;border:1px solid #4c12a0;" +
            "border-radius:24px;color:#4c12a0;display:inline-block;padding:10px 20px\">Track your parcel</a>" +
            "</td></tr></table></td></tr></table></body></html>"

    @Test
    fun a_dispatch_email_with_a_white_stylesheet_page_and_button_stays_readable_in_dark() {
        val body = EmailHtmlSanitizer.sanitize(dispatch_email)
        assert_consistent_in_dark("dispatch email", body)
        val forced = Jsoup.parse(render(body, forced = true))
        val button = forced.select("#m a").first { it.text() == "Track your parcel" }
        assertFalse("button kept a white surface: ${button.attr("style")}", declares_light_background(button.attr("style")))
        assertFalse(forced.select("#m style").html().contains("background:#fff"))
        val automatic = Jsoup.parse(render(body, forced = false))
        assertEquals("1", automatic.selectFirst("html")!!.attr("data-white"))
        assertTrue(automatic.select("#m a").first { it.text() == "Track your parcel" }.attr("style").contains("#ffffff"))
    }

    @Test
    fun named_light_backgrounds_are_cleared_under_forced_dark() {
        val html = render(
            "<div style=\"background-color:whitesmoke\"><p style=\"color:#111\">Line</p>" +
                "<a href=\"https://example.com\" style=\"background:ivory;color:#222\">Open</a></div>",
            forced = true,
        )
        val content = Jsoup.parse(html).getElementById("m")!!
        assertFalse(content.html().contains("whitesmoke"))
        assertFalse(content.html().contains("ivory"))
        assert_consistent("named light backgrounds", html)
    }
}
