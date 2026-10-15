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

class forward_block_test {
    private val labels = forward_header_labels(
        forwarded_message = "Forwarded message",
        from = "From:",
        date = "Date:",
        subject = "Subject:",
        to = "To:",
    )

    @Test
    fun header_lists_every_field() {
        val html = build_forward_header_html(
            labels,
            forward_header_values("a@example.com", "Oct 1, 2026", "Hello", listOf("b@example.com", " c@example.com ")),
        )
        assertTrue(html.contains("---------- Forwarded message ---------"))
        assertTrue(html.contains("<b>From:</b> a@example.com"))
        assertTrue(html.contains("<b>Date:</b> Oct 1, 2026"))
        assertTrue(html.contains("<b>Subject:</b> Hello"))
        assertTrue(html.contains("<b>To:</b> b@example.com, c@example.com"))
    }

    @Test
    fun header_skips_empty_fields_and_escapes() {
        val html = build_forward_header_html(
            labels,
            forward_header_values("<x>@example.com", "", "", listOf(" ")),
        )
        assertTrue(html.contains("&lt;x&gt;@example.com"))
        assertFalse(html.contains("Date:"))
        assertFalse(html.contains("Subject:"))
        assertFalse(html.contains("To:"))
    }

    @Test
    fun inner_html_keeps_original_body() {
        val inner = build_forward_inner_html("<div>h</div>", "<p>body</p>")
        assertEquals("<div>h</div><div class=\"aster_forward_body\"><p>body</p></div>", inner)
        assertTrue(wrap_forward_block(inner).endsWith(inner + "</div>"))
    }

    @Test
    fun draft_with_forward_converts_plain_body() {
        assertEquals("a &lt;b&gt;<br>c<q>", draft_html_with_forward("a <b>\nc", false, "<q>"))
        assertEquals("<p>x</p><q>", draft_html_with_forward("<p>x</p>", true, "<q>"))
        assertEquals("plain", draft_html_with_forward("plain", false, ""))
    }
}
