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

package org.astermail.android.ui.mail

import org.astermail.android.mail.html_to_plain_text
import org.astermail.android.mail.strip_body_html
import org.astermail.android.ui.compose.assemble_body_with_signature
import org.astermail.android.ui.compose.wrap_forward_block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class reply_body_spacing_test {

    private val typed = "Hi Sam,\n\nThanks for the update on the order.\nI can pick it up on Friday.\n\nBest,\nAlex"

    private val quote = wrap_forward_block(
        "<div class=\"aster_quote_attr gmail_attr\" style=\"color:#555;font-size:13px\">" +
            "<div><b>From:</b> sam@example.com</div><div><b>Date:</b> Nov 17</div>" +
            "<div><b>Subject:</b> Order</div></div>" +
            "<blockquote class=\"gmail_quote\" style=\"margin:8px 0 0;padding-left:12px;border-left:2px solid #ccc\">" +
            "<div dir=\"ltr\">Hello Alex,<br><br>Your order is ready.<br>Sam</div></blockquote>",
    )

    private val expected = "Hi Sam,\n\nThanks for the update on the order.\nI can pick it up on Friday.\n\n" +
        "Best,\nAlex\n\nFrom: sam@example.com\nDate: Nov 17\nSubject: Order\n\n" +
        "Hello Alex,\n\nYour order is ready.\nSam"

    private fun sent_reply_html(): String = assemble_body_with_signature(
        body = typed,
        is_rich = false,
        plain_signature = "",
        html_signature = "",
        signature_id = null,
        quote_html = quote,
        place_below = false,
    )

    @Test
    fun the_stored_text_of_a_sent_reply_keeps_its_lines() {
        assertEquals(expected, html_to_plain_text(sent_reply_html()))
    }

    @Test
    fun a_sent_reply_shown_as_text_keeps_its_lines_and_quote_header() {
        val html = sent_reply_html()
        assertEquals(expected, select_display_body("", html))
    }

    @Test
    fun a_sent_reply_cached_with_flattened_text_is_shown_with_its_lines() {
        val html = sent_reply_html()
        val flattened = strip_body_html(html)
        assertEquals(expected, select_display_body(flattened, html))
    }

    @Test
    fun html_kept_in_the_text_field_is_shown_with_its_lines() {
        assertEquals(expected, select_display_body(sent_reply_html(), null))
    }

    @Test
    fun a_real_plain_text_part_is_left_as_sent() {
        val text = "Line one\nLine two"
        assertEquals(text, select_display_body(text, "<div>Line one</div><div>Line two</div>"))
    }

    @Test
    fun the_html_view_keeps_every_typed_line_as_its_own_block() {
        val prepared = prepare_email_body(sent_reply_html(), "Forwarded", "failed", false, false)
        assertTrue(prepared, prepared.contains("<div dir=\"auto\">Thanks for the update on the order.</div><div dir=\"auto\">I can pick it up on Friday.</div>"))
        assertTrue(prepared, prepared.contains("aster-quoted-content"))
    }
}
