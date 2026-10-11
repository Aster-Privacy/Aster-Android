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

package org.astermail.android.subscriptions

import org.astermail.android.api.subscriptions.MailingListSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedSubscriptionsTest {
    private fun subscription(sender_email: String, status: String = "active") =
        MailingListSubscription(id = sender_email, sender_email = sender_email, status = status)

    private fun visible(
        subscriptions: List<MailingListSubscription>,
        index: BlockedSenderIndex,
    ): List<String> =
        without_blocked_senders(subscriptions, index) { it.sender_email }.map { it.sender_email }

    @Test
    fun removes_a_subscription_whose_address_is_blocked() {
        val index = build_blocked_sender_index(listOf("news@shop.example" to false))
        assertEquals(
            listOf("hello@other.example"),
            visible(listOf(subscription("news@shop.example"), subscription("hello@other.example")), index),
        )
    }

    @Test
    fun removes_a_blocked_sender_that_is_already_unsubscribed() {
        val index = build_blocked_sender_index(listOf("news@shop.example" to false))
        assertEquals(
            emptyList<String>(),
            visible(listOf(subscription("news@shop.example", status = "unsubscribed")), index),
        )
    }

    @Test
    fun matches_without_regard_to_case_or_surrounding_spaces() {
        val index = build_blocked_sender_index(listOf("  News@Shop.Example " to false))
        assertTrue(is_sender_in_blocked_index("NEWS@shop.example", index))
        assertTrue(is_sender_in_blocked_index(" news@shop.example ", index))
    }

    @Test
    fun removes_every_sender_on_a_blocked_domain() {
        val index = build_blocked_sender_index(listOf("shop.example" to true))
        assertTrue(is_sender_in_blocked_index("news@shop.example", index))
        assertTrue(is_sender_in_blocked_index("deals@SHOP.example", index))
        assertFalse(is_sender_in_blocked_index("news@other.example", index))
    }

    @Test
    fun treats_an_entry_that_starts_with_an_at_sign_as_a_domain() {
        val index = build_blocked_sender_index(listOf("@shop.example" to false))
        assertTrue(is_sender_in_blocked_index("news@shop.example", index))
    }

    @Test
    fun does_not_match_a_domain_that_only_ends_the_same_way() {
        val index = build_blocked_sender_index(listOf("shop.example" to true))
        assertFalse(is_sender_in_blocked_index("news@myshop.example", index))
        assertFalse(is_sender_in_blocked_index("news@mail.shop.example", index))
    }

    @Test
    fun a_blocked_address_does_not_hide_other_senders_on_its_domain() {
        val index = build_blocked_sender_index(listOf("news@shop.example" to false))
        assertFalse(is_sender_in_blocked_index("orders@shop.example", index))
    }

    @Test
    fun ignores_blocked_entries_that_could_not_be_read() {
        val index = build_blocked_sender_index(listOf("" to false, "  " to true, "@" to true))
        assertTrue(index.is_empty)
        assertFalse(is_sender_in_blocked_index("", index))
        assertFalse(is_sender_in_blocked_index("news@shop.example", index))
    }

    @Test
    fun returns_the_same_list_when_nothing_is_blocked() {
        val list = listOf(subscription("news@shop.example"))
        assertSame(list, without_blocked_senders(list, BlockedSenderIndex()) { it.sender_email })
    }
}
