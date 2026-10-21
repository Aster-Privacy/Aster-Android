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

package org.astermail.android.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class draft_quote_test {
    private val reply_quote =
        "<br><br><div class=\"aster_quote gmail_quote\">" +
            "<div class=\"aster_quote_attr gmail_attr\"><div><b>From:</b> a@b.org</div></div>" +
            "<blockquote class=\"gmail_quote\">Original body</blockquote></div>"

    @Test
    fun plain_draft_has_no_quote() {
        assertNull(split_draft_quote("<div>Hello</div>"))
    }

    @Test
    fun reply_quote_leaves_only_typed_text_in_body() {
        val split = split_draft_quote("<div>Undo me</div>$reply_quote")!!
        assertEquals("<div>Undo me</div>", split.body_html)
        assertFalse(split.is_forward)
        assertTrue(split.quote_block.startsWith("<br><br><div class=\"aster_quote gmail_quote\">"))
        assertTrue(split.quote_block.contains("Original body"))
    }

    @Test
    fun preview_drops_classes_that_hide_quotes() {
        val split = split_draft_quote("<div>Hi</div>$reply_quote")!!
        assertFalse(split.preview_html.contains("gmail_quote"))
        assertFalse(split.preview_html.contains("aster_quote"))
        assertTrue(split.preview_html.contains("Original body"))
    }

    @Test
    fun signature_below_quote_stays_in_body() {
        val split = split_draft_quote(
            "<div>Hi</div>$reply_quote<br><br><div class=\"aster_signature\">Sig</div>",
        )!!
        assertTrue(split.body_html.contains("Sig"))
        assertFalse(split.body_html.contains("Original body"))
        assertFalse(split.quote_block.contains("Sig"))
    }

    @Test
    fun forward_block_is_detected() {
        val block = wrap_forward_block(
            build_forward_inner_html("<div class=\"aster_quote_attr gmail_attr\">Fwd</div>", "Body"),
        )
        val split = split_draft_quote("Note$block")!!
        assertTrue(split.is_forward)
        assertEquals("Note", split.body_html)
    }

    @Test
    fun round_trip_through_draft_save_keeps_one_quote() {
        val first = split_draft_quote("<div>Hi</div>$reply_quote")!!
        val saved = draft_html_with_forward(first.body_html, true, first.quote_block)
        val second = split_draft_quote(saved)!!
        assertEquals(first.body_html, second.body_html)
        assertEquals(1, Regex("Original body").findAll(saved).count())
    }
}
