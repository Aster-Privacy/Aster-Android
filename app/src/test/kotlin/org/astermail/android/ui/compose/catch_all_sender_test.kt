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

import org.astermail.android.api.billing.LimitInfo
import org.astermail.android.api.billing.PlanLimitsResponse
import org.astermail.android.api.settings.CustomDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class catch_all_sender_test {

    private val domain = CustomDomain(
        id = "d1",
        domain_name = "Example.com",
        status = "active",
        catch_all_enabled = true,
    )

    private fun options(
        candidates: List<String?>,
        domains: List<CustomDomain> = listOf(domain),
        existing: List<String> = listOf("me@astermail.org"),
        disabled: List<String> = emptyList(),
        unlocked: Boolean = true,
    ) = catch_all_sender_options(unlocked, domains, candidates, existing, disabled)

    private fun limits(value: Int?) = PlanLimitsResponse(
        plan_code = "x",
        limits = if (value == null) emptyMap() else mapOf(catch_all_feature to LimitInfo(limit = value)),
    )

    @Test
    fun plan_gate_fails_closed() {
        assertFalse(is_catch_all_unlocked(null))
        assertFalse(is_catch_all_unlocked(limits(null)))
        assertFalse(is_catch_all_unlocked(limits(0)))
        assertTrue(is_catch_all_unlocked(limits(1)))
        assertTrue(is_catch_all_unlocked(limits(-1)))
    }

    @Test
    fun offers_catch_all_address_on_paid_plan() {
        assertEquals(listOf("sales@example.com"), options(listOf("Sales@Example.com")))
    }

    @Test
    fun offers_nothing_when_locked() {
        assertEquals(emptyList<String>(), options(listOf("sales@example.com"), unlocked = false))
    }

    @Test
    fun skips_ineligible_domains() {
        val cases = listOf(
            domain.copy(catch_all_enabled = false),
            domain.copy(status = "pending"),
            domain.copy(is_shared = true),
            domain.copy(domain_name = "other.com"),
        )
        for (d in cases) {
            assertEquals(emptyList<String>(), options(listOf("sales@example.com"), domains = listOf(d)))
        }
    }

    @Test
    fun skips_known_disabled_and_duplicate_addresses() {
        assertEquals(emptyList<String>(), options(listOf("sa.les@example.com"), existing = listOf("sales@example.com")))
        assertEquals(emptyList<String>(), options(listOf("old@example.com"), disabled = listOf("Old@example.com")))
        assertEquals(listOf("a@example.com"), options(listOf("a@example.com", "A@example.com")))
    }

    @Test
    fun rejects_malformed_addresses() {
        val bad = listOf(
            null, "", "   ", "*@example.com", "+tag@example.com", ".a@example.com", "a.@example.com", "a..b@example.com",
            "${"a".repeat(65)}@example.com", "a b@example.com", "a@@example.com", "no-at",
            "a@example.com\r\nBcc: x@y.z", "<a@example.com>",
        )
        for (candidate in bad) {
            assertNull(candidate, to_catch_all_address(candidate))
        }
        assertEquals("a.b+c@example.com", to_catch_all_address(" A.b+C@example.com "))
    }

    @Test
    fun reply_candidate_only_when_no_registered_address_was_addressed() {
        val message = compose_thread_message(
            id = "m1",
            to_addresses = listOf("list@lists.example.org"),
            delivered_to = "sales@example.com",
        )
        val existing = listOf("me@astermail.org", "support@example.com")
        assertEquals(listOf("sales@example.com"), catch_all_reply_candidates(message, existing))
        assertEquals(
            emptyList<String>(),
            catch_all_reply_candidates(message.copy(cc_addresses = listOf("Supp.ort@example.com")), existing),
        )
        assertEquals(emptyList<String>(), catch_all_reply_candidates(message.copy(is_sent = true), existing))
        assertEquals(emptyList<String>(), catch_all_reply_candidates(message.copy(delivered_to = null), existing))
        assertEquals(emptyList<String>(), catch_all_reply_candidates(null, existing))
    }

    @Test
    fun reply_from_resolves_to_catch_all_address() {
        val base = listOf("me@astermail.org")
        val message = compose_thread_message(id = "m1", delivered_to = "sales@example.com", to_addresses = listOf("sales@example.com"))
        val alias_options = base + options(catch_all_reply_candidates(message, base), existing = base)
        val received = compute_received_on_alias(
            listOfNotNull(message.delivered_to) + message.to_addresses,
            alias_options,
            "me@astermail.org",
        )
        assertEquals("sales@example.com", received)
    }
}
