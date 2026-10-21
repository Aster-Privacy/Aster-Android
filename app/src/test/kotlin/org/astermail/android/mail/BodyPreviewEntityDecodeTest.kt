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
import org.junit.Test

class BodyPreviewEntityDecodeTest {

    @Test
    fun `double encoded nbsp never shows as a literal entity`() {
        assertEquals(
            "Aster meeting in Inbox",
            clean_body_preview("", "<p>Aster meeting in Inbox&amp;nbsp;&amp;nbsp;</p>"),
        )
    }

    @Test
    fun `double encoded ampersand decodes to one ampersand`() {
        assertEquals("Tom & Jerry", strip_body_html("Tom &amp;amp; Jerry"))
    }

    @Test
    fun `double encoded numeric entity decodes`() {
        assertEquals("© 2026", strip_body_html("&amp;#169; 2026"))
    }

    @Test
    fun `preview decoding stops after a bounded number of passes`() {
        assertEquals("&amp;lt;", decode_html_entities_for_preview("&amp;amp;amp;amp;lt;"))
    }

    @Test
    fun `send path decoding stays single pass`() {
        assertEquals("&nbsp;", decode_html_entities("&amp;nbsp;"))
    }

    @Test
    fun `preheader keeps words separated by a non breaking space`() {
        val html = "<html><body><span style=\"display:none\">Sale&nbsp;ends&nbsp;tonight</span><p>Body</p></body></html>"
        assertEquals("Sale ends tonight", extract_preheader_text(html))
    }
}
