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

class SentReplyPreviewTest {

    private val quoted_original =
        "<div>Hey, all great questions. I am not in the office right now.</div>" +
            "<div class=\"protonmail_signature_block\">Sent from a mobile mail app.</div>"

    private val reply_body =
        "<div>Thanks for getting back to me, I will take a look.</div>" +
            "<br><div class=\"aster_quote gmail_quote\">" +
            "<div class=\"aster_quote_attr gmail_attr\"><div><b>From:</b> Lifetime Labs</div></div>" +
            "<blockquote class=\"gmail_quote\">" + quoted_original + "</blockquote></div>"

    @Test
    fun a_reply_preview_shows_only_my_own_text() {
        val preview = clean_body_preview("", reply_body)

        assertTrue(preview, preview.startsWith("Thanks for getting back to me"))
        assertFalse(preview, preview.contains("all great questions"))
        assertFalse(preview, preview.contains("From:"))
    }

    @Test
    fun a_forward_with_no_new_text_still_previews_the_quoted_body() {
        val forward_only =
            "<div class=\"aster_quote gmail_quote\"><blockquote class=\"gmail_quote\">" +
                quoted_original + "</blockquote></div>"

        val preview = clean_body_preview("", forward_only)

        assertTrue(preview, preview.contains("all great questions"))
    }

    @Test
    fun a_plain_message_preview_is_unchanged() {
        assertEquals(
            "Just a plain note with no quoting at all.",
            clean_body_preview("", "<div>Just a plain note with no quoting at all.</div>"),
        )
    }

    @Test
    fun a_reply_thread_card_shows_only_my_own_text() {
        val preview = thread_card_preview(reply_body, "Thanks for getting back to me From: Lifetime Labs")

        assertTrue(preview, preview.startsWith("Thanks for getting back to me"))
        assertFalse(preview, preview.contains("From:"))
        assertFalse(preview, preview.contains("all great questions"))
    }

    @Test
    fun a_thread_card_without_html_keeps_the_plain_body() {
        assertEquals("Plain text body", thread_card_preview(null, "Plain text body"))
    }

    @Test
    fun a_forward_thread_card_still_shows_the_quoted_body() {
        val forward_only =
            "<div class=\"aster_quote gmail_quote\"><blockquote class=\"gmail_quote\">" +
                quoted_original + "</blockquote></div>"

        assertEquals("fallback body", thread_card_preview(forward_only, "fallback body"))
    }
}
