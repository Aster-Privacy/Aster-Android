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

class CssCommentStrippingTest {

    @Test
    fun removes_comments_between_url_and_argument() {
        val stripped = EmailHtmlSanitizer.strip_css_comments(
            "background:url/*c*/(\"https://evil.example/p.png\")",
        )
        assertEquals("background:url(\"https://evil.example/p.png\")", stripped)
    }

    @Test
    fun removes_comments_inside_url_argument() {
        val stripped = EmailHtmlSanitizer.strip_css_comments(
            "background:url(/*c*/\"https://evil.example/p.png\")",
        )
        assertEquals("background:url(\"https://evil.example/p.png\")", stripped)
    }

    @Test
    fun keeps_comment_markers_inside_quoted_strings() {
        val css = "a:after{content:\"/*not a comment*/\"}"
        assertEquals(css, EmailHtmlSanitizer.strip_css_comments(css))
    }

    @Test
    fun tolerates_unterminated_comment() {
        assertEquals("a{", EmailHtmlSanitizer.strip_css_comments("a{/*never closed"))
    }

    @Test
    fun comment_obfuscated_background_is_neutralized() {
        val html =
            "<div style=\"background:url/*c*/(&quot;https://evil.example/p.png&quot;)\">hi</div>"
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertFalse(out.contains("evil.example"))
    }

    @Test
    fun comment_obfuscated_style_block_is_neutralized() {
        val html =
            "<style>body{background:url(/*c*/'https://evil.example/p.png')}</style><p>hi</p>"
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertFalse(out.contains("evil.example"))
    }

    @Test
    fun inline_cid_and_fragment_urls_survive() {
        val html = "<div style=\"background:url(cid:abc123)\">hi</div>"
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertTrue(out.contains("cid:abc123"))
    }

    @Test
    fun comment_obfuscated_javascript_url_is_blocked_in_style_blocks() {
        val out = EmailHtmlSanitizer.sanitize(
            "<style>a{background:url(x);behavior:url(x)}b{color:java/*x*/script:alert(1)}</style><p>hi</p>",
        )
        assertFalse(out.contains("javascript:"))
    }
}
