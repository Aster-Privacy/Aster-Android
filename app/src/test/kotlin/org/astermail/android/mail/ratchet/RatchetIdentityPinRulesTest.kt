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
package org.astermail.android.mail.ratchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatchetIdentityPinRulesTest {

    @Test
    fun first_sighting_pins_when_nothing_is_stored() {
        assertEquals(IdentityPinDecision.PIN_FIRST, RatchetIdentityPinRules.decide(null, "fp-a", confirmed = false))
        assertEquals(IdentityPinDecision.PIN_FIRST, RatchetIdentityPinRules.decide("", "fp-a", confirmed = false))
        assertEquals(IdentityPinDecision.PIN_FIRST, RatchetIdentityPinRules.decide("   ", "fp-a", confirmed = true))
    }

    @Test
    fun matching_fingerprint_keeps_the_pin() {
        assertEquals(IdentityPinDecision.KEEP, RatchetIdentityPinRules.decide("fp-a", "fp-a", confirmed = false))
        assertEquals(IdentityPinDecision.KEEP, RatchetIdentityPinRules.decide("fp-a", "fp-a", confirmed = true))
    }

    @Test
    fun unconfirmed_drift_never_overwrites_the_pin() {
        assertEquals(IdentityPinDecision.FLAG_DRIFT, RatchetIdentityPinRules.decide("fp-a", "fp-b", confirmed = false))
    }

    @Test
    fun confirmed_drift_replaces_the_pin() {
        assertEquals(IdentityPinDecision.REPLACE, RatchetIdentityPinRules.decide("fp-a", "fp-b", confirmed = true))
    }

    @Test
    fun unconsumed_pq_prekey_is_accepted() {
        assertTrue(RatchetIdentityPinRules.pq_prekey_accepts(null, "eph-1"))
        assertTrue(RatchetIdentityPinRules.pq_prekey_accepts("", "eph-1"))
    }

    @Test
    fun pq_prekey_accepts_only_the_ephemeral_key_that_consumed_it() {
        assertTrue(RatchetIdentityPinRules.pq_prekey_accepts("eph-1", "eph-1"))
        assertFalse(RatchetIdentityPinRules.pq_prekey_accepts("eph-1", "eph-2"))
    }

    @Test
    fun scoped_key_falls_back_to_legacy_key_without_an_account() {
        assertEquals("pin_conv", RatchetIdentityPinRules.scoped_key(null, "pin_conv"))
        assertEquals("pin_conv", RatchetIdentityPinRules.scoped_key("", "pin_conv"))
        assertEquals("pin_conv", RatchetIdentityPinRules.scoped_key("  ", "pin_conv"))
    }

    @Test
    fun scoped_key_isolates_accounts() {
        val a = RatchetIdentityPinRules.scoped_key("user-a", "pin_conv")
        val b = RatchetIdentityPinRules.scoped_key("user-b", "pin_conv")
        assertEquals("acct/user-a/pin_conv", a)
        assertTrue(a != b)
        assertTrue(RatchetIdentityPinRules.belongs_to_account(a, "user-a"))
        assertFalse(RatchetIdentityPinRules.belongs_to_account(a, "user-b"))
        assertFalse(RatchetIdentityPinRules.belongs_to_account("pin_conv", "user-a"))
        assertFalse(RatchetIdentityPinRules.belongs_to_account(a, ""))
    }

    @Test
    fun account_prefix_does_not_match_a_longer_account_id() {
        val key = RatchetIdentityPinRules.scoped_key("user-ab", "pin_conv")
        assertFalse(RatchetIdentityPinRules.belongs_to_account(key, "user-a"))
    }
}
