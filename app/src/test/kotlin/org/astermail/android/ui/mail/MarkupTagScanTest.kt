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

class MarkupTagScanTest {

    private val img_start = Regex("""<img\s""", RegexOption.IGNORE_CASE)

    private val samples = listOf(
        """<p>a</p><img src="cid:a"><IMG
alt="b" src='cid:b'/>tail""",
        """<img src="x"><imgx src="y"><img>""",
        """<img src="cid:a" <img src="cid:b">""",
        """no tags here""",
        """<img src="cid:open""",
    )

    @Test
    fun the_scanner_finds_the_same_image_tags_as_the_old_pattern() {
        val old = Regex("""<img\s[^>]*>""", RegexOption.IGNORE_CASE)
        for (html in samples) {
            val found = markup_tags(html, img_start).map { html.substring(it.first, it.last + 1) }.toList()
            assertEquals(old.findAll(html).map { it.value }.toList(), found)
            assertEquals(old.replace(html) { "[${it.value}]" }, replace_markup_tags(html, img_start) { "[$it]" })
        }
    }

    @Test
    fun the_scanner_finds_the_same_stylesheets_as_the_old_pattern() {
        val old = Regex("""<link\b[^>]*rel\s*=\s*["']stylesheet["'][^>]*>""", RegexOption.IGNORE_CASE)
        val html = """<link rel="icon" href="https://a.example/i.png"><LINK href="https://a.example/s.css" REL='stylesheet'>""" +
            """<link <link rel="stylesheet" href="https://b.example/t.css"><linked rel="stylesheet"><link rel="stylesheet" """
        assertEquals(old.findAll(html).map { it.value }.toList(), stylesheet_link_tags(html))
        assertEquals(2, stylesheet_link_tags(html).size)
    }

    @Test(timeout = 10_000)
    fun unterminated_image_tags_resolve_in_linear_time() {
        val html = "cid:" + "<img src=\"cid:a\" ".repeat(200_000)
        val out = resolve_inline_cids(html, mapOf("a" to "data:image/png;base64,AAAB"))
        assertFalse(out.contains("cid:a"))
        assertTrue(out.endsWith("data:image/png;base64,AAAB\" "))
    }

    @Test(timeout = 10_000)
    fun unterminated_link_tags_are_counted_in_linear_time() {
        val html = "<link rel=\"stylesheet\" ".repeat(200_000)
        val counts = count_external_content(html, EmailHtmlSanitizer.TrackerReport())
        assertEquals(0, counts.css_count)
    }

    @Test(timeout = 10_000)
    fun long_font_urls_are_capped() {
        val html = "@font-face{src:url(https://" + "a".repeat(1_000_000)
        val counts = count_external_content(html, EmailHtmlSanitizer.TrackerReport())
        assertEquals(1, counts.font_count)
        assertTrue(counts.items.single().url.length <= "https://".length + 2048)
    }

    @Test
    fun a_long_content_id_is_left_alone() {
        val reference = "x".repeat(2_000)
        val html = """<img src="cid:$reference">"""
        assertEquals(html, resolve_inline_cids(html, mapOf(reference to "data:image/png;base64,AAAB")))
    }
}
