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


package org.astermail.android.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSlotPolicyTest {

    @Test
    fun limit_reached_keeps_an_account_already_on_the_device() {
        assertEquals(
            AccountSlotDecision.Allow,
            decide_account_slot(AccountSlotLink.LimitReached, account_known = true, can_add_locally = false),
        )
    }

    @Test
    fun limit_reached_rejects_a_new_account() {
        assertEquals(
            AccountSlotDecision.Reject,
            decide_account_slot(AccountSlotLink.LimitReached, account_known = false, can_add_locally = true),
        )
        assertEquals(
            AccountSlotDecision.Reject,
            decide_account_slot(AccountSlotLink.LimitReached, account_known = false, can_add_locally = false),
        )
    }

    @Test
    fun linked_slot_always_allows() {
        assertEquals(
            AccountSlotDecision.Allow,
            decide_account_slot(AccountSlotLink.Linked(3), account_known = false, can_add_locally = false),
        )
    }

    @Test
    fun unknown_link_falls_back_to_the_local_cap() {
        assertEquals(
            AccountSlotDecision.Allow,
            decide_account_slot(AccountSlotLink.Unknown, account_known = true, can_add_locally = false),
        )
        assertEquals(
            AccountSlotDecision.Allow,
            decide_account_slot(AccountSlotLink.Unknown, account_known = false, can_add_locally = true),
        )
        assertEquals(
            AccountSlotDecision.Reject,
            decide_account_slot(AccountSlotLink.Unknown, account_known = false, can_add_locally = false),
        )
    }
}
