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

package org.astermail.android.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackTickDueTest {
    @Test
    fun a_fresh_list_with_live_sync_connected_is_left_alone() {
        assertFalse(fallback_tick_due(live_sync_connected = true, list_loaded_at = 1_000_000L, last_live_event_at = 0L, now = 1_060_000L))
        assertFalse(fallback_tick_due(live_sync_connected = true, list_loaded_at = 1_000_000L, last_live_event_at = 0L, now = 1_000_000L + 15 * 60_000L - 1L))
    }

    @Test
    fun a_list_older_than_the_bound_is_refetched_even_with_live_sync_connected() {
        assertTrue(fallback_tick_due(live_sync_connected = true, list_loaded_at = 1_000_000L, last_live_event_at = 0L, now = 1_000_000L + 15 * 60_000L))
        assertTrue(fallback_tick_due(live_sync_connected = true, list_loaded_at = 0L, last_live_event_at = 0L, now = 1_000_000L))
        assertTrue(fallback_tick_due(live_sync_connected = true, list_loaded_at = 2_000_000L, last_live_event_at = 0L, now = 1_000_000L))
    }

    @Test
    fun a_live_event_newer_than_the_list_is_refetched_with_live_sync_connected() {
        assertTrue(fallback_tick_due(live_sync_connected = true, list_loaded_at = 1_000_000L, last_live_event_at = 1_000_001L, now = 1_060_000L))
        assertFalse(fallback_tick_due(live_sync_connected = true, list_loaded_at = 1_000_000L, last_live_event_at = 1_000_000L, now = 1_060_000L))
    }

    @Test
    fun without_live_sync_every_tick_refetches() {
        assertTrue(fallback_tick_due(live_sync_connected = false, list_loaded_at = 1_000_000L, last_live_event_at = 0L, now = 1_000_001L))
        assertTrue(fallback_tick_due(live_sync_connected = false, list_loaded_at = 0L, last_live_event_at = 0L, now = 1_000_000L))
    }
}
