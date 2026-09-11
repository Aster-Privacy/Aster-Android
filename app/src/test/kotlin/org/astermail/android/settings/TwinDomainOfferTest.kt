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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinDomainOfferTest {

    @Test
    fun a_free_plan_is_not_offered_a_premium_matching_address() {
        assertFalse(twin_domain_offerable("astermail.me", "available", premium_allowed = false))
        assertFalse(twin_domain_offerable("astermail.net", "reserved", premium_allowed = false))
    }

    @Test
    fun a_free_plan_keeps_the_standard_matching_address() {
        assertTrue(twin_domain_offerable("aster.cx", "available", premium_allowed = false))
        assertTrue(twin_domain_offerable("astermail.org", "reserved", premium_allowed = false))
    }

    @Test
    fun a_premium_plan_is_offered_every_matching_address() {
        assertTrue(twin_domain_offerable("astermail.me", "available", premium_allowed = true))
        assertTrue(twin_domain_offerable("astermail.net", "reserved", premium_allowed = true))
    }

    @Test
    fun a_taken_or_claimed_address_is_never_offered() {
        assertFalse(twin_domain_offerable("aster.cx", "taken", premium_allowed = true))
        assertFalse(twin_domain_offerable("astermail.me", "claimed", premium_allowed = true))
    }
}
