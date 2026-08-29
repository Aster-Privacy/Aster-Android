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

package org.astermail.android.api.network

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class low_network_state_test {

    @Before
    fun reset_before() {
        low_network_state.reset()
    }

    @After
    fun reset_after() {
        low_network_state.reset()
    }

    @Test
    fun the_state_is_inactive_by_default() {
        assertFalse(low_network_state.active())
        assertFalse(low_network_state.is_active.value)
    }

    @Test
    fun the_preference_activates_the_state() {
        low_network_state.set_preference(true)
        assertTrue(low_network_state.active())
        assertTrue(low_network_state.is_preference_enabled())
    }

    @Test
    fun data_saver_activates_the_state_without_the_preference() {
        low_network_state.set_data_saver(true)
        assertTrue(low_network_state.active())
        assertFalse(low_network_state.is_preference_enabled())
        assertTrue(low_network_state.is_data_saver_enabled())
    }

    @Test
    fun the_state_stays_active_while_either_source_is_on() {
        low_network_state.set_preference(true)
        low_network_state.set_data_saver(true)
        low_network_state.set_preference(false)
        assertTrue(low_network_state.active())
        low_network_state.set_data_saver(false)
        assertFalse(low_network_state.active())
    }

    @Test
    fun the_flow_publishes_every_change() {
        val seen = mutableListOf<Boolean>()
        seen.add(low_network_state.is_active.value)
        low_network_state.set_preference(true)
        seen.add(low_network_state.is_active.value)
        low_network_state.set_preference(false)
        seen.add(low_network_state.is_active.value)
        assertFalse(seen[0])
        assertTrue(seen[1])
        assertFalse(seen[2])
    }
}
