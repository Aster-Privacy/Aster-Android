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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlRewriteBreakoutTest {

    private val base = "https://app.astermail.org/api/images/v1/proxy?url="

    private fun rewritten(raw: String): String =
        proxy_external_urls(EmailHtmlSanitizer.sanitize(raw), base)

    private fun cell_of(html: String): org.jsoup.nodes.Element =
        org.jsoup.Jsoup.parseBodyFragment(html).selectFirst("td")!!

    @Test
    fun title_attribute_carrying_src_cannot_inject_attributes() {
        val payload = "src=//a.example/x style=position:fixed;top:0;left:0;width:100vw;height:100vh"
        val out = rewritten("""<table><tr><td title="$payload">hello</td></tr></table>""")
        val cell = cell_of(out)
        assertEquals(setOf("title"), cell.attributes().asList().map { it.key }.toSet())
        assertFalse(cell.hasAttr("style"))
        assertFalse(cell.hasAttr("src"))
        assertTrue(cell.attr("title").contains("position:fixed"))
    }

    @Test
    fun title_attribute_carrying_width_cannot_inject_attributes() {
        val out = rewritten("""<table><tr><td title="width=600 style=position:fixed">hello</td></tr></table>""")
        val fitted = fit_wide_width_attributes(out)
        val cell = cell_of(fitted)
        assertEquals(setOf("title"), cell.attributes().asList().map { it.key }.toSet())
        assertFalse(cell.hasAttr("width"))
        assertFalse(cell.hasAttr("style"))
    }

    @Test
    fun text_node_containing_src_is_not_rewritten() {
        val out = rewritten("""<p>write src=//a.example/x to proxy it</p>""")
        assertFalse(out.contains("proxy?url="))
        assertTrue(out.contains("src=//a.example/x"))
    }

    @Test
    fun genuine_image_source_is_still_proxied() {
        val out = rewritten("""<img src="https://cdn.example/hero.png" alt="hero">""")
        val img = org.jsoup.Jsoup.parseBodyFragment(out).selectFirst("img")!!
        assertEquals(base + java.net.URLEncoder.encode("https://cdn.example/hero.png", "UTF-8"), img.attr("src"))
    }

    @Test
    fun genuine_background_attribute_is_still_proxied() {
        val out = rewritten("""<table><tr><td background="https://cdn.example/b.png">x</td></tr></table>""")
        val cell = cell_of(out)
        assertEquals(base + java.net.URLEncoder.encode("https://cdn.example/b.png", "UTF-8"), cell.attr("background"))
    }

    @Test
    fun wide_table_width_attribute_becomes_full_width() {
        val out = fit_wide_width_attributes("""<table width="600"><tr><td width="300">x</td></tr></table>""")
        val doc = org.jsoup.Jsoup.parseBodyFragment(out)
        assertEquals("100%", doc.selectFirst("table")!!.attr("width"))
        assertEquals("300", doc.selectFirst("td")!!.attr("width"))
    }

    @Test
    fun wide_width_in_text_node_is_left_alone() {
        val out = fit_wide_width_attributes("""<p>the banner is width=600 wide</p>""")
        assertTrue(out.contains("width=600"))
        assertFalse(out.contains("100%"))
    }
}
