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
}
