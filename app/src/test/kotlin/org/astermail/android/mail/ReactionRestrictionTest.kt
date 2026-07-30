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
import org.junit.Assert.assertNull
import org.junit.Test

class ReactionRestrictionTest {

    private val me = "me@astermail.org"

    private fun restriction(
        item_type: String = "received",
        sender_email: String = "sender@example.com",
        to_addresses: List<String> = listOf(me),
        cc_addresses: List<String> = emptyList(),
        raw_headers: List<Pair<String, String>> = emptyList(),
        reactions: List<DecryptedReaction> = emptyList(),
        user_email: String = me,
        is_spam: Boolean = false,
        is_trashed: Boolean = false,
        reactions_enabled: Boolean = true,
        is_own_address: (String) -> Boolean = { false },
    ): ReactionRestriction? = reaction_restriction(
        item_type = item_type,
        sender_email = sender_email,
        to_addresses = to_addresses,
        cc_addresses = cc_addresses,
        raw_headers = raw_headers,
        reactions = reactions,
        user_email = user_email,
        is_spam = is_spam,
        is_trashed = is_trashed,
        reactions_enabled = reactions_enabled,
        is_own_address = is_own_address,
    )

    @Test
    fun allows_a_normal_received_message() {
        assertNull(restriction())
    }

    @Test
    fun blocks_when_reactions_are_turned_off() {
        assertEquals(ReactionRestriction.disabled, restriction(reactions_enabled = false))
    }

    @Test
    fun blocks_own_sent_messages() {
        assertEquals(ReactionRestriction.own_message, restriction(item_type = "sent"))
    }

    @Test
    fun blocks_drafts_and_scheduled_messages() {
        assertEquals(ReactionRestriction.draft, restriction(item_type = "draft"))
        assertEquals(ReactionRestriction.draft, restriction(item_type = "scheduled"))
    }

    @Test
    fun blocks_spam_and_trash() {
        assertEquals(ReactionRestriction.spam_or_trash, restriction(is_spam = true))
        assertEquals(ReactionRestriction.spam_or_trash, restriction(is_trashed = true))
    }

    @Test
    fun blocks_a_message_with_a_different_reply_to_address() {
        assertEquals(
            ReactionRestriction.reply_to,
            restriction(raw_headers = listOf("Reply-To" to "List <list@example.com>")),
        )
    }

    @Test
    fun allows_a_reply_to_that_matches_the_sender() {
        assertNull(restriction(raw_headers = listOf("Reply-To" to "Sender <sender@example.com>")))
    }

    @Test
    fun blocks_messages_with_more_than_twenty_recipients() {
        val recipients = listOf(me) + (0 until 20).map { "person$it@example.com" }
        assertEquals(
            ReactionRestriction.too_many_recipients,
            restriction(to_addresses = recipients),
        )
    }

    @Test
    fun blocks_messages_the_user_was_only_bcc_d_on() {
        assertEquals(
            ReactionRestriction.bcc,
            restriction(to_addresses = listOf("someone@example.com")),
        )
    }

    @Test
    fun allows_cc_d_recipients_case_insensitively() {
        assertNull(
            restriction(
                to_addresses = listOf("someone@example.com"),
                cc_addresses = listOf(me.uppercase()),
            ),
        )
    }

    @Test
    fun allows_a_message_addressed_to_one_of_the_user_aliases() {
        assertNull(
            restriction(
                to_addresses = listOf("alias@astermail.org"),
                is_own_address = { it == "alias@astermail.org" },
            ),
        )
    }

    @Test
    fun blocks_once_the_message_has_twenty_distinct_reactions() {
        val reactions = (0 until 20).map { index ->
            DecryptedReaction(
                reaction_mail_item_id = "reaction_$index",
                emoji = String(Character.toChars(0x1F600 + index)),
                reactor_email = "person$index@example.com",
            )
        }
        assertEquals(ReactionRestriction.too_many_emojis, restriction(reactions = reactions))
    }

    @Test
    fun allows_repeats_of_the_same_emoji() {
        val reactions = (0 until 25).map { index ->
            DecryptedReaction(
                reaction_mail_item_id = "reaction_$index",
                emoji = "👍",
                reactor_email = "person$index@example.com",
            )
        }
        assertNull(restriction(reactions = reactions))
    }

    @Test
    fun blocks_when_there_is_no_sender_to_reply_to() {
        assertEquals(ReactionRestriction.no_recipient, restriction(sender_email = "  "))
    }

    @Test
    fun every_restriction_maps_to_a_string_resource() {
        ReactionRestriction.values().forEach { value ->
            assert(reaction_restriction_string(value) != 0)
        }
    }
}
