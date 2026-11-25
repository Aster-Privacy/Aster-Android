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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class outgoing_markers_test {
    @Test
    fun removes_signature_id_from_signature_div() {
        val out = strip_outgoing_markers(signature_div_html("sig-123", "Bob"))
        assertFalse(out.contains("data-aster-signature-id"))
        assertFalse(out.contains("sig-123"))
        assertEquals(
            "<div data-aster-signature=\"1\" dir=\"auto\" style=\"unicode-bidi:plaintext\">Bob</div>",
            out,
        )
    }

    @Test
    fun removes_single_quoted_and_unquoted_values() {
        assertEquals("<div>a</div>", strip_outgoing_markers("<div data-aster-signature-id='x1'>a</div>"))
        assertEquals("<div>a</div>", strip_outgoing_markers("<div DATA-ASTER-SIGNATURE-ID=x1>a</div>"))
    }

    @Test
    fun keeps_text_that_mentions_the_attribute() {
        val html = "<p>data-aster-signature-id=\"abc\" is text</p>"
        assertEquals(html, strip_outgoing_markers(html))
    }

    @Test
    fun leaves_html_without_the_attribute_unchanged() {
        val html = "<div class=\"a\">x &lt; y</div><br>"
        assertEquals(html, strip_outgoing_markers(html))
    }

    @Test
    fun handles_quoted_angle_brackets_inside_a_tag() {
        val html = "<div title=\"a > b\" data-aster-signature-id=\"s\">x</div>"
        assertEquals("<div title=\"a > b\">x</div>", strip_outgoing_markers(html))
    }

    @Test
    fun assembled_body_has_no_signature_id_after_stripping() {
        val body = assemble_body_with_signature(
            body = "Hi",
            is_rich = false,
            plain_signature = "",
            html_signature = "<b>Bob</b>",
            signature_id = "sig-9",
            quote_html = "",
            place_below = false,
        )
        assertTrue(body.contains("sig-9"))
        val out = strip_outgoing_markers(body)
        assertFalse(out.contains("sig-9"))
        assertTrue(out.contains("data-aster-signature=\"1\""))
        assertTrue(out.contains("<b>Bob</b>"))
    }
}
