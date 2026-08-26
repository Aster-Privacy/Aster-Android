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

import org.astermail.android.ui.compose.build_reply_recipient
import org.astermail.android.ui.compose.extract_reply_to
import org.astermail.android.ui.compose.parse_header_address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyRecipientTest {

    @Test
    fun parses_angle_form_with_quoted_name() {
        val parsed = parse_header_address("\"Support Team\" <help@example.com>")
        assertEquals("Support Team", parsed?.name)
        assertEquals("help@example.com", parsed?.email)
    }

    @Test
    fun parses_bare_address() {
        val parsed = parse_header_address(" list@example.org ")
        assertNull(parsed?.name)
        assertEquals("list@example.org", parsed?.email)
    }

    @Test
    fun rejects_malformed_address() {
        assertNull(parse_header_address("not-an-address"))
    }

    @Test
    fun accepts_internationalized_domain() {
        assertEquals("info@bücher.de", parse_header_address("info@bücher.de")?.email)
    }

    @Test
    fun extracts_first_reply_to_address() {
        val headers = listOf(
            "From" to "noreply@example.com",
            "Reply-To" to "\"Team, Support\" <help@example.com>, other@example.com",
        )
        assertEquals("help@example.com", extract_reply_to(headers)?.email)
    }

    @Test
    fun reply_to_header_wins_over_sender() {
        val target = build_reply_recipient(
            sender_email = "noreply@example.com",
            first_to = "me@astermail.org",
            reply_to = parse_header_address("help@example.com"),
            display_sender_email = null,
            own_addresses = listOf("me@astermail.org"),
            is_own_message = false,
        )
        assertEquals("help@example.com", target)
    }

    @Test
    fun own_reply_to_is_ignored() {
        val target = build_reply_recipient(
            sender_email = "noreply@example.com",
            first_to = "me@astermail.org",
            reply_to = parse_header_address("m.e@astermail.org"),
            display_sender_email = null,
            own_addresses = listOf("me@astermail.org"),
            is_own_message = false,
        )
        assertEquals("noreply@example.com", target)
    }

    @Test
    fun forwarded_message_replies_to_real_sender() {
        val target = build_reply_recipient(
            sender_email = "sender@example.com",
            first_to = "me@astermail.org",
            reply_to = parse_header_address("help@example.com"),
            display_sender_email = "alias@example.net",
            own_addresses = listOf("me@astermail.org"),
            is_own_message = false,
        )
        assertEquals("sender@example.com", target)
    }

    @Test
    fun own_message_replies_to_original_recipient() {
        val target = build_reply_recipient(
            sender_email = "me@astermail.org",
            first_to = "friend@example.com",
            reply_to = null,
            display_sender_email = null,
            own_addresses = listOf("me@astermail.org"),
            is_own_message = true,
        )
        assertEquals("friend@example.com", target)
    }

    @Test
    fun own_message_without_recipient_falls_back_to_sender() {
        val target = build_reply_recipient(
            sender_email = "me@astermail.org",
            first_to = null,
            reply_to = null,
            display_sender_email = null,
            own_addresses = listOf("me@astermail.org"),
            is_own_message = true,
        )
        assertEquals("me@astermail.org", target)
    }
}
