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

class HtmlBodyDetectionTest {

    @Test
    fun detects_an_html_body() {
        assertTrue(looks_like_html_body("<div dir=\"auto\">Thanks<br>Sam</div>"))
        assertTrue(looks_like_html_body("hello <b>there</b>"))
    }

    @Test
    fun leaves_plain_text_alone() {
        assertFalse(looks_like_html_body("Thanks, I have found it :-)"))
        assertFalse(looks_like_html_body("2 < 3 and 5 > 4"))
        assertFalse(looks_like_html_body("mail me at <sam@astermail.org>"))
    }

    @Test
    fun converts_html_to_readable_plain_text() {
        val html = "<div>Thanks, I have found it<br><br>Secured by Aster Mail</div>" +
            "<div>On Wed, Aug 19, 2026, Aster Team &lt;hello@astermail.org&gt; wrote:</div>"
        val text = html_to_plain_text(html)

        assertEquals(
            "Thanks, I have found it\n\nSecured by Aster Mail\n" +
                "On Wed, Aug 19, 2026, Aster Team <hello@astermail.org> wrote:",
            text,
        )
    }

    @Test
    fun drops_style_and_script_blocks() {
        val html = "<style>p{color:red}</style><script>alert(1)</script><p>Body text</p>"

        assertEquals("Body text", html_to_plain_text(html))
    }

    @Test
    fun decodes_numeric_entities() {
        assertEquals("café & cream", decode_html_entities("caf&#233; &amp; cream"))
    }
}
