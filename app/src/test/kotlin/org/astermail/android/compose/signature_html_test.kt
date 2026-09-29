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
package org.astermail.android.compose

import org.astermail.android.ui.compose.SIGNATURE_GAP_HTML
import org.astermail.android.ui.compose.assemble_body_with_signature
import org.astermail.android.ui.compose.draft_html_with_signature
import org.astermail.android.ui.compose.html_signature_with_separator
import org.astermail.android.ui.compose.plain_signature_with_separator
import org.astermail.android.ui.compose.seeded_body_with_signature
import org.astermail.android.ui.compose.signature_div_html
import org.junit.Assert.assertEquals
import org.junit.Test

class signature_html_test {
    private val signature = plain_signature_with_separator("Adam\nAster", true)
    private val signature_div =
        "<div data-aster-signature=\"1\" data-aster-signature-id=\"sig_1\">--<br>Adam<br>Aster</div>"
    private val quote = "<br><br><div class=\"aster_quote\">quoted</div>"

    private fun plain(
        body: String,
        quote_html: String = "",
        place_below: Boolean = false,
        plain_signature: String = signature,
        html_signature: String = "",
    ): String = assemble_body_with_signature(
        body = body,
        is_rich = false,
        plain_signature = plain_signature,
        html_signature = html_signature,
        signature_id = "sig_1",
        quote_html = quote_html,
        place_below = place_below,
    )

    private fun rich(body: String): String = assemble_body_with_signature(
        body = body,
        is_rich = true,
        plain_signature = signature,
        html_signature = "",
        signature_id = "sig_1",
        quote_html = "",
        place_below = false,
    )

    @Test
    fun `untouched new message matches the web caret line, blank line, and signature`() {
        val body = seeded_body_with_signature("", signature, "")
        assertEquals(
            "<div><br></div><div><br></div>" + signature_div,
            plain(body),
        )
    }

    @Test
    fun `typed text keeps exactly one blank line above the signature`() {
        assertEquals(
            "<div>Hello</div><div><br></div>" + signature_div,
            plain("Hello\n\n$signature"),
        )
    }

    @Test
    fun `each typed line becomes its own block`() {
        assertEquals(
            "<div>Hi</div><div>there</div><div><br></div>" + signature_div,
            plain("Hi\nthere\n\n$signature"),
        )
    }

    @Test
    fun `typed text is escaped`() {
        assertEquals(
            "<div>a &lt; b &amp; c</div><div><br></div>" + signature_div,
            plain("a < b & c\n\n$signature"),
        )
    }

    @Test
    fun `trailing blank lines after the signature are dropped`() {
        assertEquals(
            "<div>Hello</div><div><br></div>" + signature_div,
            plain("Hello\n\n$signature\n\n"),
        )
    }

    @Test
    fun `inline image tokens stay in place`() {
        assertEquals(
            "<div>[[ASTER_IMG_0]]</div><div><br></div>" + signature_div,
            plain("[[ASTER_IMG_0]]\n\n$signature"),
        )
    }

    @Test
    fun `body without a signature adds no blank lines`() {
        assertEquals("<div>Hello</div><div>World</div>", plain("Hello\nWorld", plain_signature = ""))
        assertEquals("", plain("", plain_signature = ""))
        assertEquals("<div>Hello</div>", plain("Hello\n\n", plain_signature = ""))
    }

    @Test
    fun `disabled separator leaves the signature without a delimiter`() {
        val bare = plain_signature_with_separator("Adam", false)
        assertEquals(
            "<div><br></div><div><br></div>" +
                "<div data-aster-signature=\"1\" data-aster-signature-id=\"sig_1\">Adam</div>",
            plain("\n\n$bare", plain_signature = bare),
        )
    }

    @Test
    fun `html signature gets the same spacing and a single separator`() {
        val html = html_signature_with_separator("<b>Adam</b>", true)
        val expected_div = "<div data-aster-signature=\"1\" data-aster-signature-id=\"sig_1\">--<br><b>Adam</b></div>"
        assertEquals(
            "<div><br></div><div><br></div>" + expected_div,
            plain("", plain_signature = "", html_signature = html),
        )
        val typed = plain("Hello", plain_signature = "", html_signature = html)
        assertEquals("<div>Hello</div><div><br></div>" + expected_div, typed)
        assertEquals(1, Regex("--<br>").findAll(typed).count())
    }

    @Test
    fun `reply keeps the signature above the quote`() {
        assertEquals(
            "<div>Hello</div><div><br></div>" + signature_div + quote,
            plain("Hello\n\n$signature", quote_html = quote),
        )
    }

    @Test
    fun `signature moved below the quote keeps one blank line`() {
        assertEquals(
            "<div>Hello</div>" + quote + SIGNATURE_GAP_HTML + signature_div,
            plain("Hello\n\n$signature", quote_html = quote, place_below = true),
        )
    }

    @Test
    fun `signature stays above the quote when nothing was typed`() {
        assertEquals(
            "<div><br></div><div><br></div>" + signature_div + quote,
            plain("\n\n$signature", quote_html = quote, place_below = true),
        )
    }

    @Test
    fun `formatted body wraps the lines above the signature in one block`() {
        assertEquals(
            "<div>Hello<br><br></div>" + signature_div,
            rich("Hello<br><br>--<br>Adam<br>Aster"),
        )
        assertEquals(
            "<div><b>Hi</b><br><br></div>" + signature_div,
            rich("<b>Hi</b><br><br>--<br>Adam<br>Aster<br><br>"),
        )
    }

    @Test
    fun `signature block omits a missing id`() {
        assertEquals("<div data-aster-signature=\"1\">x</div>", signature_div_html(null, "x"))
    }

    @Test
    fun `draft with an html signature keeps line breaks`() {
        assertEquals(
            "Hello<br>&lt;World&gt;<br><br><div class=\"aster_signature\">--<br><b>A</b></div>",
            draft_html_with_signature("Hello\n<World>", false, "--<br><b>A</b>"),
        )
        assertEquals("Hello\nWorld", draft_html_with_signature("Hello\nWorld", false, ""))
        assertEquals(
            "<b>Hi</b><br><br><div class=\"aster_signature\">--<br>A</div>",
            draft_html_with_signature("<b>Hi</b>", true, "--<br>A"),
        )
    }
}
