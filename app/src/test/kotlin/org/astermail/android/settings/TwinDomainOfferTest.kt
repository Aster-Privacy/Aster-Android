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

package org.astermail.android.settings

import org.astermail.android.api.settings.TwinAddressResponse
import org.astermail.android.api.settings.TwinSibling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinDomainOfferTest {

    @Test
    fun only_the_aster_cx_matching_address_is_offered() {
        assertTrue(twin_domain_offerable("aster.cx", "available"))
        assertTrue(twin_domain_offerable("aster.cx", "reserved"))
        assertFalse(twin_domain_offerable("astermail.me", "available"))
        assertFalse(twin_domain_offerable("astermail.net", "reserved"))
        assertFalse(twin_domain_offerable("astermail.org", "reserved"))
    }

    @Test
    fun a_taken_or_claimed_address_is_never_offered() {
        assertFalse(twin_domain_offerable("aster.cx", "taken"))
        assertFalse(twin_domain_offerable("aster.cx", "claimed"))
    }

    @Test
    fun sibling_list_is_narrowed_to_aster_cx() {
        val twin = TwinAddressResponse(
            address = "user@aster.cx",
            domain = "aster.cx",
            local_part = "user",
            state = "reserved",
            siblings = listOf(
                TwinSibling("user@aster.cx", "aster.cx", "user", "reserved"),
                TwinSibling("user@astermail.me", "astermail.me", "user", "reserved"),
                TwinSibling("user@astermail.net", "astermail.net", "user", "available"),
            ),
        )
        assertEquals(listOf("user@aster.cx"), offerable_twin_siblings(twin).map { it.address })
        assertTrue(offerable_twin_siblings(null).isEmpty())
    }

    @Test
    fun seed_derives_the_aster_cx_address_from_the_primary_address() {
        val seeded = seed_twin_address("User.Name@astermail.org", emptyList())
        assertEquals("user.name@aster.cx", seeded?.address)
        assertEquals(listOf("user.name@aster.cx"), offerable_twin_siblings(seeded).map { it.address })
    }

    @Test
    fun seed_hides_an_address_the_account_already_owns() {
        val seeded = seed_twin_address("user@astermail.org", listOf("user@aster.cx"))
        assertTrue(offerable_twin_siblings(seeded).isEmpty())
    }

    @Test
    fun a_seeded_twin_waits_for_the_alias_list() {
        val seeded = seed_twin_address("user@astermail.org", emptyList())
        assertTrue(twin_offer_siblings(seeded, emptyList(), verified = false, owned_loaded = false).isEmpty())
        assertEquals(
            listOf("user@aster.cx"),
            twin_offer_siblings(seeded, emptyList(), verified = false, owned_loaded = true).map { it.address },
        )
    }

    @Test
    fun a_twin_the_account_already_owns_is_not_offered_once_aliases_load() {
        val seeded = seed_twin_address("user.name@astermail.org", emptyList())
        assertTrue(
            twin_offer_siblings(seeded, listOf("UserName@aster.cx"), verified = false, owned_loaded = true).isEmpty(),
        )
        assertTrue(
            twin_offer_siblings(seeded, listOf("user.name@aster.cx"), verified = true, owned_loaded = false).isEmpty(),
        )
    }

    @Test
    fun a_verified_twin_shows_before_aliases_load() {
        val twin = TwinAddressResponse(
            address = "user@aster.cx",
            domain = "aster.cx",
            local_part = "user",
            state = "reserved",
        )
        assertEquals(
            listOf("user@aster.cx"),
            twin_offer_siblings(twin, emptyList(), verified = true, owned_loaded = false).map { it.address },
        )
    }

    @Test
    fun seed_skips_accounts_without_a_twin() {
        assertNull(seed_twin_address("user@aster.cx", emptyList()))
        assertNull(seed_twin_address("user@example.com", emptyList()))
        assertNull(seed_twin_address("12345@astermail.org", emptyList()))
        assertNull(seed_twin_address("", emptyList()))
        assertNull(seed_twin_address(null, emptyList()))
    }
}
