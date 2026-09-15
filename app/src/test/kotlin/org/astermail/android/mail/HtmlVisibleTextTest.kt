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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlVisibleTextTest {

    private val marketing_css = """
        @font-face { font-family: 'Euclid Circular A'; src: url('https://example.test/font.woff2'); }
        a { text-decoration: none; }
        #outlook a { padding: 0; }
        .ExternalClass { width: 100%; }
    """.trimIndent()

    private fun assert_no_css(text: String) {
        assertFalse(text, text.contains("text-decoration"))
        assertFalse(text, text.contains("@font-face"))
        assertFalse(text, text.contains("ExternalClass"))
        assertFalse(text, text.contains("#outlook"))
    }

    @Test
    fun `removes style blocks from plain text`() {
        val html = "<html><head><style type=\"text/css\">$marketing_css</style></head><body><p>readable body</p></body></html>"

        val text = html_to_plain_text(html)

        assert_no_css(text)
        assertTrue(text.contains("readable body"))
    }

    @Test
    fun `removes style blocks with spaced closing tags`() {
        val html = "<body><style>$marketing_css</style ><p>readable body</p></body>"

        assert_no_css(html_to_plain_text(html))
        assertTrue(html_to_plain_text(html).contains("readable body"))
    }

    @Test
    fun `removes an unterminated style block`() {
        val html = "<body><p>readable body</p><style type=\"text/css\">$marketing_css"

        val text = html_to_plain_text(html)

        assert_no_css(text)
        assertTrue(text.contains("readable body"))
    }

    @Test
    fun `keeps the body when head is never closed`() {
        val html = "<html><head><style>$marketing_css</style><body><p>readable body</p></body></html>"

        val text = html_to_plain_text(html)

        assert_no_css(text)
        assertTrue(text.contains("readable body"))
    }

    @Test
    fun `ignores a closing tag name inside an attribute value`() {
        val html = "<body><a href=\"https://example.test/?q=</style>\">readable body</a><style>$marketing_css</style></body>"

        val text = html_to_plain_text(html)

        assert_no_css(text)
        assertTrue(text.contains("readable body"))
    }

    @Test
    fun `strips script blocks`() {
        val html = "<body><script>window.tracker = 1;</script><p>readable body</p></body>"

        val text = html_to_plain_text(html)

        assertFalse(text.contains("window.tracker"))
        assertTrue(text.contains("readable body"))
    }

    @Test
    fun `degrades to escaped paragraphs without css`() {
        val html = "<html><head><style>$marketing_css</style></head><body><p>readable body</p></body></html>"

        val degraded = degraded_email_html(html)

        assert_no_css(degraded)
        assertTrue(degraded.contains("readable body"))
        assertFalse(degraded.contains("<style"))
    }

    @Test
    fun `degrades markup to escaped text rather than raw tags`() {
        val degraded = degraded_email_html("<body><p>readable &amp; body</p></body>")

        assertFalse(degraded.contains("<body>"))
        assertTrue(degraded.contains("readable &amp; body"))
    }

    @Test
    fun `handles deeply nested markup without overflowing`() {
        val depth = 20000
        val html = "<html><head><style>$marketing_css</style></head><body>" +
            "<div>".repeat(depth) + "readable body" + "</div>".repeat(depth) +
            "</body></html>"

        val text = html_to_plain_text(html)

        assert_no_css(text)
        assertTrue(text.contains("readable body"))
    }
}
