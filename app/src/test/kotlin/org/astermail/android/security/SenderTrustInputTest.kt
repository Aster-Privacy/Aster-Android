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

package org.astermail.android.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTrustInputTest {

    private val context = SenderTrustContext(own_addresses = listOf("me@astermail.org"))

    @Test
    fun internal_only_when_the_server_marked_it_so() {
        val base = SenderTrustMessage(sender_name = "Ana", sender_email = "ana@astermail.org")

        assertTrue(sender_trust_input(base.copy(is_external = false), context).is_internal)
        assertFalse(sender_trust_input(base.copy(is_external = true), context).is_internal)
        assertFalse(sender_trust_input(base, context).is_internal)
        assertFalse(
            sender_trust_input(
                base.copy(is_external = false, sender_verification = "invalid"),
                context,
            ).is_internal,
        )
    }

    @Test
    fun rewritten_shown_sender_drops_internal_and_verified_status() {
        val input = sender_trust_input(
            SenderTrustMessage(
                sender_name = "Relay",
                sender_email = "relay@forwarder.example",
                display_sender_email = "ceo@bank.example",
                is_external = false,
                sender_verified = true,
                sender_verified_domain = "forwarder.example",
            ),
            context,
        )

        assertEquals("ceo@bank.example", input.email)
        assertFalse(input.is_internal)
        assertEquals(false, input.sender_verified)
        assertNull(input.sender_verified_domain)
    }

    @Test
    fun rewritten_shown_sender_does_not_borrow_the_carrier_authentication() {
        val relayed = SenderTrustMessage(
            sender_name = "Bank",
            sender_email = "relay@forwarder.example",
            display_sender_email = "ceo@bank.example",
            is_external = true,
        )
        val passed = sender_trust_input(
            relayed.copy(spf_result = "pass", dkim_result = "pass", dmarc_result = "pass", sender_aligned = true),
            context,
        )

        assertNull(passed.sender_aligned)
        assertNull(passed.dmarc_result)
        assertEquals(SenderAuthState.unknown, evaluate_sender_trust(passed).auth_state)

        val legacy = sender_trust_input(relayed.copy(dkim_result = "pass", dmarc_result = "pass"), context)

        assertEquals(SenderAuthState.unknown, evaluate_sender_trust(legacy).auth_state)

        val signed = sender_trust_input(
            relayed.copy(spf_result = "pass", dkim_result = "pass", dmarc_result = "none"),
            context,
        )

        assertEquals(SenderAuthState.unknown, evaluate_sender_trust(signed).auth_state)

        val unsigned = sender_trust_input(
            relayed.copy(spf_result = "pass", dkim_result = "fail", dmarc_result = "none"),
            context,
        )

        assertEquals(false, unsigned.sender_aligned)
        assertFalse(evaluate_sender_trust(unsigned).auth_state == SenderAuthState.unknown)

        val failed = sender_trust_input(
            relayed.copy(spf_result = "fail", dkim_result = "fail", dmarc_result = "fail", sender_aligned = false),
            context,
        )

        assertEquals(false, failed.sender_aligned)
        assertEquals(SenderAuthState.failed, evaluate_sender_trust(failed).auth_state)

        val direct = sender_trust_input(
            SenderTrustMessage(
                sender_name = "Bank",
                sender_email = "ceo@bank.example",
                is_external = true,
                spf_result = "pass",
                dkim_result = "pass",
                dmarc_result = "pass",
                sender_aligned = true,
            ),
            context,
        )

        assertEquals(true, direct.sender_aligned)
        assertEquals(SenderAuthState.authenticated, evaluate_sender_trust(direct).auth_state)
    }

    @Test
    fun verified_domain_reads_as_verified_when_no_flag_is_carried() {
        val message = SenderTrustMessage(
            sender_name = "Shop",
            sender_email = "news@shop.example",
            is_external = true,
        )

        assertEquals(
            true,
            sender_trust_input(message.copy(sender_verified_domain = "shop.example"), context).sender_verified,
        )
        assertEquals(false, sender_trust_input(message, context).sender_verified)
        assertEquals(
            false,
            sender_trust_input(
                message.copy(sender_verified = false, sender_verified_domain = "shop.example"),
                context,
            ).sender_verified,
        )
    }

    @Test
    fun evaluates_the_shown_name_not_the_stored_one() {
        val input = sender_trust_input(
            SenderTrustMessage(sender_name = "Stored", sender_email = "a@b.example"),
            context,
            shown_name = "Shown",
        )

        assertEquals("Shown", input.name)
    }

    @Test
    fun address_case_alone_is_not_a_rewrite() {
        val input = sender_trust_input(
            SenderTrustMessage(
                sender_name = "Ana",
                sender_email = "Ana@Astermail.org",
                display_sender_email = " ana@astermail.org ",
                is_external = false,
            ),
            context,
        )

        assertTrue(input.is_internal)
    }

    @Test
    fun context_lists_pass_through_by_identity() {
        val input = sender_trust_input(SenderTrustMessage(sender_email = "a@b.example"), context)

        assertTrue(input.own_addresses === context.own_addresses)
        assertTrue(input.contacts === context.contacts)
    }

    @Test
    fun missing_message_has_no_trust_and_keeps_the_fallback_name() {
        assertNull(sender_trust_for(null, context))
        assertEquals("Fallback", trusted_sender_name(sender_trust_for(null, context), "Fallback", "Unknown"))
    }
}
