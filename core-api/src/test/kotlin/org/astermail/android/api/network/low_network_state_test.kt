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
    fun only_the_preference_activates_the_state() {
        low_network_state.set_preference(true)
        assertTrue(low_network_state.active())
        low_network_state.set_preference(false)
        assertFalse(low_network_state.active())
        assertFalse(low_network_state.is_preference_enabled())
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

    @Test
    fun a_timeout_extends_timeouts_without_activating_the_state() {
        low_network_state.note_timeout(now_ms = 1_000L)
        assertTrue(low_network_state.extend_timeouts(now_ms = 1_000L + SLOW_LINK_WINDOW_MS - 1))
        assertFalse(low_network_state.active())
    }

    @Test
    fun the_timeout_extension_expires() {
        low_network_state.note_timeout(now_ms = 1_000L)
        assertFalse(low_network_state.extend_timeouts(now_ms = 1_000L + SLOW_LINK_WINDOW_MS))
    }

    @Test
    fun the_preference_always_extends_timeouts() {
        low_network_state.set_preference(true)
        assertTrue(low_network_state.extend_timeouts(now_ms = 0L))
    }
}
