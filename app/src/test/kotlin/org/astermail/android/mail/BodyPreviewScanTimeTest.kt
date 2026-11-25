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

package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyPreviewScanTimeTest {

    @Test(timeout = 2_000)
    fun a_long_run_of_open_brackets_finishes_quickly() {
        val text = "<".repeat(100_000)
        assertEquals(text, strip_body_html(text))
        assertEquals(text, html_to_plain_text(text))
        assertEquals(text, thread_card_preview(text, ""))
        assertEquals("<".repeat(PREVIEW_MAX_LENGTH), clean_body_preview("", text))
    }

    @Test(timeout = 2_000)
    fun unclosed_quote_attribution_tags_finish_quickly() {
        val html = "<div class=\"gmail_attr\" ".repeat(1_200)
        assertFalse(has_quote_attribution(html))
        assertEquals(html, strip_quote_attribution(html))
        assertEquals(html.trim().substring(0, PREVIEW_MAX_LENGTH), clean_body_preview("", html))
        thread_card_preview(html, "")
    }

    @Test(timeout = 2_000)
    fun closed_quote_attribution_tags_finish_quickly() {
        val html = "<div class=\"gmail_attr\">".repeat(1_200)
        assertTrue(has_quote_attribution(html))
        assertEquals("", strip_quote_attribution(html))
        clean_body_preview("", html)
    }

    @Test(timeout = 2_000)
    fun unclosed_quote_block_tags_finish_quickly() {
        val runs = listOf(
            "<blockquote class=\"gmail_quote\" ".repeat(1_000),
            "<div ".repeat(20_000) + ">",
            "<div class='".repeat(10_000) + ">",
            "<div class=\"x\" ".repeat(10_000) + ">",
            "<div class=\"" + "gmail_quote ".repeat(10_000),
        )
        for (html in runs) {
            assertEquals(html, strip_quoted_html(html))
            assertFalse(has_quote_attribution(html))
        }
    }

    @Test(timeout = 2_000)
    fun unclosed_html_tags_finish_quickly() {
        assertFalse(looks_like_html_body("<body ".repeat(20_000)))
        assertFalse(looks_like_html_body("<p ".repeat(30_000) + ">"))
        assertFalse(looks_like_html_body("< ".repeat(50_000) + ">"))
        assertFalse(looks_like_html_body("</ ".repeat(30_000) + ">"))
    }

    @Test(timeout = 2_000)
    fun unclosed_style_blocks_in_a_preheader_scan_finish_quickly() {
        assertEquals("", extract_preheader_text("<style>".repeat(9_000)))
        assertEquals("", extract_preheader_text("<style ".repeat(9_000)))
        assertEquals("", extract_preheader_text("<style>" + "a".repeat(60_000) + "</style>"))
        assertEquals("", extract_preheader_text("<style>" + "a{".repeat(30_000) + "</style>"))
    }

    @Test(timeout = 2_000)
    fun deeply_nested_markup_in_a_preheader_scan_finishes_quickly() {
        assertEquals("", extract_preheader_text("<div>".repeat(12_000) + "x"))
    }

    @Test(timeout = 2_000)
    fun deeply_nested_leading_css_finishes_quickly() {
        val text = "@media{".repeat(20_000) + "}".repeat(20_000) + "Hello"
        assertEquals(text, strip_leading_css(text))
    }

    @Test
    fun tags_are_still_stripped_from_previews() {
        assertEquals("a <>b", strip_body_html("<p>a</p><>b"))
        assertEquals("ac 1 < 2", html_to_plain_text("a<b>c</b> 1 < 2"))
        assertEquals("Hello there", strip_body_html("<div class=\"a<b\">Hello</div>  <span>there</span>"))
    }

    @Test
    fun html_bodies_are_still_detected() {
        assertTrue(looks_like_html_body("<p>Hi</p>"))
        assertTrue(looks_like_html_body("<HTML><BODY>x"))
        assertTrue(looks_like_html_body("<P class=x>Hi</P>"))
        assertTrue(looks_like_html_body("line<br>next"))
        assertTrue(looks_like_html_body("< h2 >Title</h2>"))
        assertFalse(looks_like_html_body("<b>bold"))
        assertFalse(looks_like_html_body("<body"))
        assertFalse(looks_like_html_body("a < b and c > d"))
        assertFalse(looks_like_html_body("<bold>x</bold>"))
    }

    @Test
    fun quoted_blocks_are_still_removed() {
        assertEquals("<p>Hi</p>", strip_quoted_html("<p>Hi</p><div class=\"gmail_quote\">old</div>"))
        assertEquals(
            "<p>Hi</p>",
            strip_quoted_html("<p>Hi</p>< BLOCKQUOTE type=\"cite\" class='Moz-Cite-Prefix x'>old"),
        )
        assertEquals("<p>Hi</p>", strip_quoted_html("<p>Hi</p><div dir=\"ltr\" class = \"aster_quote\">old</div>"))
        for (html in listOf(
            "<divx class=\"gmail_quote\">a",
            "<div class=gmail_quote>a",
            "<p class=\"gmail_quote\">a",
            "<div title=\"a>b\" class=\"gmail_quote\">a",
        )) {
            assertEquals(html, strip_quoted_html(html))
        }
    }

    @Test
    fun quote_attributions_are_still_removed() {
        val html = "<p>Reply</p><div class=\"gmail_attr\">On Mon wrote:<div>x</div></div><blockquote>old</blockquote>"
        assertTrue(has_quote_attribution(html))
        assertEquals("<p>Reply</p><blockquote>old</blockquote>", strip_quote_attribution(html))
        assertEquals("<p>Reply</p>", strip_quote_attribution("<p>Reply</p><div class=\"gmail_attr\">On Mon wrote:"))
        assertEquals("a b", strip_quote_attribution("a<DIV CLASS='aster_quote_attr'>x< / div > b"))
        assertFalse(has_quote_attribution("<div class=\"gmail_quote\">x</div>"))
    }

    @Test
    fun hidden_preheaders_are_still_found() {
        val html = "<html><head><STYLE type=\"text/css\">.a{color:red}.pre,.x{display:none}</STYLE></head>" +
            "<body><div class=\"pre\">Your order shipped</div><p>Body</p></body></html>"
        assertEquals("Your order shipped", extract_preheader_text(html))
    }

    @Test
    fun leading_css_groups_are_still_stripped() {
        assertEquals("Hello", strip_leading_css("@media screen{p{color:red}}Hello"))
    }

    @Test(timeout = 2_000)
    fun runs_of_open_brackets_before_a_far_close_finish_quickly() {
        val text = "<".repeat(40_000) + "x".repeat(5_000) + ">"
        assertEquals(text, replace_short_tags(text, " ", 4000))
        assertEquals("<".repeat(35_999) + " ", replace_short_tags("<".repeat(40_000) + ">", " ", 4000))
    }

    @Test
    fun short_tags_are_still_replaced() {
        assertEquals(" Hi  there ", replace_short_tags("<p>Hi<br/> there</p>", " ", 4000))
        assertEquals(" ", replace_short_tags("<>", " ", 4000))
        assertEquals("a<" + "b".repeat(10) + "> c", replace_short_tags("a<" + "b".repeat(10) + "> c", " ", 5))
        assertEquals("a  c", replace_short_tags("a<<b> c", " ", 5))
        assertEquals("a < b", replace_short_tags("a < b", " ", 5))
    }
}
