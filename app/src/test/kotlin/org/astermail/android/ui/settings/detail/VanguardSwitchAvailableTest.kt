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

package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VanguardSwitchAvailableTest {

    @Test
    fun enabled_vanguard_always_keeps_switch() {
        assertTrue(vanguard_switch_available(server_eligible = false, plan_allows = false, subscription_known = true, enabled = true))
        assertTrue(vanguard_switch_available(server_eligible = null, plan_allows = false, subscription_known = true, enabled = true))
    }

    @Test
    fun server_eligibility_wins_over_local_plan() {
        assertTrue(vanguard_switch_available(server_eligible = true, plan_allows = false, subscription_known = true, enabled = false))
        assertFalse(vanguard_switch_available(server_eligible = false, plan_allows = true, subscription_known = true, enabled = false))
    }

    @Test
    fun falls_back_to_plan_when_server_unknown() {
        assertTrue(vanguard_switch_available(server_eligible = null, plan_allows = true, subscription_known = true, enabled = false))
        assertFalse(vanguard_switch_available(server_eligible = null, plan_allows = false, subscription_known = true, enabled = false))
    }

    @Test
    fun unknown_subscription_does_not_show_upgrade() {
        assertTrue(vanguard_switch_available(server_eligible = null, plan_allows = false, subscription_known = false, enabled = false))
    }
}
