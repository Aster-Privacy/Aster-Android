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

package org.astermail.android.ui.compose

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class quoted_original_test {
    private val labels = reply_quote_labels(from = "From:", date = "Date:", subject = "Subject:")

    private val crafted = "<p>hi</p></blockquote></div><p id=\"after\">appended</p>" +
        "<form action=\"https://example.com/x\"><input name=\"q\"></form>" +
        "<script>alert(1)</script>" +
        "<img src=\"https://example.com/p.gif\" width=\"1\" height=\"1\">"

    private fun top_level_blocks(html: String): Int {
        val body = Jsoup.parseBodyFragment(html).body()
        return body.children().count { it.tagName() != "br" }
    }

    @Test
    fun reply_header_escapes_values() {
        val html = build_reply_quote_html(labels, "<a@example.com>", "Oct 1", "<b>x</b>", "<p>y</p>")
        assertTrue(html.contains("&lt;a@example.com&gt;"))
        assertTrue(html.contains("&lt;b&gt;x&lt;/b&gt;"))
        assertTrue(html.contains("<p>y</p></blockquote>"))
    }

    @Test
    fun reply_quote_keeps_content_inside_the_blockquote() {
        val quote = wrap_forward_block(
            build_reply_quote_html(labels, "a@example.com", "Oct 1", "Hi", sanitize_quoted_original(crafted)),
        )
        assertEquals(1, top_level_blocks(quote))
        val doc = Jsoup.parseBodyFragment(quote)
        val appended = doc.select("p:contains(appended)").first()
        assertNotNull(appended)
        assertTrue(appended!!.parents().any { it.tagName() == "blockquote" })
        assertTrue(doc.select("form, script, input").isEmpty())
    }

    @Test
    fun forward_body_keeps_content_inside_the_wrapper() {
        val inner = build_forward_inner_html("<div>h</div>", sanitize_quoted_original(crafted))
        val block = wrap_forward_block(inner)
        assertEquals(1, top_level_blocks(block))
        val doc = Jsoup.parseBodyFragment(block)
        val appended = doc.select("p:contains(appended)").first()
        assertNotNull(appended)
        assertTrue(appended!!.parents().any { it.hasClass("aster_forward_body") })
        assertTrue(doc.select("form, script, input").isEmpty())
    }

    @Test
    fun sanitized_quote_drops_tracking_pixels() {
        val out = sanitize_quoted_original(crafted)
        assertFalse(out.contains("p.gif"))
        assertTrue(out.contains("hi"))
    }
}
