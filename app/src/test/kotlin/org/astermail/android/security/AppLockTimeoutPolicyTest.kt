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

class AppLockTimeoutPolicyTest {

    private val minute = 60_000L

    private fun departure(elapsed: Long = 1_000_000L, wall: Long = 1_700_000_000_000L) =
        AppLockDeparture(elapsed, wall)

    @Test
    fun keys_round_trip_and_unknown_falls_back_to_immediately() {
        AppLockTimeout.entries.forEach { assertEquals(it, AppLockTimeout.from_key(it.key)) }
        assertEquals(AppLockTimeout.IMMEDIATELY, AppLockTimeout.from_key(null))
        assertEquals(AppLockTimeout.IMMEDIATELY, AppLockTimeout.from_key("forever"))
    }

    @Test
    fun a_locked_session_always_locks() {
        assertTrue(AppLockTimeoutPolicy.should_lock(false, null, 0, 0, AppLockTimeout.ONE_HOUR, external_flow = true))
    }

    @Test
    fun an_unlocked_session_that_never_left_stays_open() {
        assertFalse(AppLockTimeoutPolicy.should_lock(true, null, 5, 5, AppLockTimeout.IMMEDIATELY, external_flow = false))
    }

    @Test
    fun every_timeout_locks_exactly_at_its_limit() {
        val d = departure()
        AppLockTimeout.entries.filter { it != AppLockTimeout.IMMEDIATELY }.forEach { t ->
            assertFalse(t.key, AppLockTimeoutPolicy.should_lock(true, d, d.elapsed_ms + t.millis - 1, d.wall_ms + t.millis - 1, t, false))
            assertTrue(t.key, AppLockTimeoutPolicy.should_lock(true, d, d.elapsed_ms + t.millis, d.wall_ms + t.millis, t, false))
        }
    }

    @Test
    fun immediately_locks_on_leave_but_not_during_an_external_flow() {
        assertTrue(AppLockTimeoutPolicy.locks_when_leaving(AppLockTimeout.IMMEDIATELY, external_flow = false))
        assertFalse(AppLockTimeoutPolicy.locks_when_leaving(AppLockTimeout.IMMEDIATELY, external_flow = true))
        assertFalse(AppLockTimeoutPolicy.locks_when_leaving(AppLockTimeout.FIVE_MINUTES, external_flow = false))
    }

    @Test
    fun moving_the_wall_clock_back_does_not_extend_the_timeout() {
        val d = departure()
        val elapsed_now = d.elapsed_ms + 6 * minute
        val wall_now = d.wall_ms - 60 * minute
        assertNull(AppLockTimeoutPolicy.away_ms(d, elapsed_now, wall_now))
        assertTrue(AppLockTimeoutPolicy.should_lock(true, d, elapsed_now, wall_now, AppLockTimeout.ONE_HOUR, false))
    }

    @Test
    fun moving_the_wall_clock_forward_counts_as_time_away() {
        val d = departure()
        assertTrue(
            AppLockTimeoutPolicy.should_lock(true, d, d.elapsed_ms + 10, d.wall_ms + 2 * 60 * minute, AppLockTimeout.ONE_HOUR, false),
        )
    }

    @Test
    fun small_wall_clock_jitter_is_tolerated() {
        val d = departure()
        assertEquals(minute, AppLockTimeoutPolicy.away_ms(d, d.elapsed_ms + minute, d.wall_ms - 1_000L))
    }

    @Test
    fun a_reboot_where_elapsed_restarts_locks() {
        val d = departure(elapsed = 50 * minute)
        assertNull(AppLockTimeoutPolicy.away_ms(d, 10_000L, d.wall_ms + 1_000L))
        assertTrue(AppLockTimeoutPolicy.should_lock(true, d, 10_000L, d.wall_ms + 1_000L, AppLockTimeout.ONE_HOUR, true))
    }

    @Test
    fun an_external_flow_gets_a_grace_window_but_not_forever() {
        val d = departure()
        val grace = AppLockTimeoutPolicy.EXTERNAL_FLOW_GRACE_MS
        assertFalse(AppLockTimeoutPolicy.should_lock(true, d, d.elapsed_ms + grace, d.wall_ms + grace, AppLockTimeout.IMMEDIATELY, true))
        assertTrue(AppLockTimeoutPolicy.should_lock(true, d, d.elapsed_ms + grace + 1, d.wall_ms + grace + 1, AppLockTimeout.IMMEDIATELY, true))
    }

    @Test
    fun an_external_flow_still_locks_when_the_wall_clock_goes_back() {
        val d = departure()
        assertTrue(AppLockTimeoutPolicy.should_lock(true, d, d.elapsed_ms + minute, d.wall_ms - 10 * minute, AppLockTimeout.IMMEDIATELY, true))
    }

    @Test
    fun background_delay_covers_the_external_grace() {
        assertEquals(0L, AppLockTimeoutPolicy.background_lock_delay_ms(AppLockTimeout.IMMEDIATELY, false))
        assertTrue(
            AppLockTimeoutPolicy.background_lock_delay_ms(AppLockTimeout.IMMEDIATELY, true) > AppLockTimeoutPolicy.EXTERNAL_FLOW_GRACE_MS,
        )
        assertEquals(60 * minute, AppLockTimeoutPolicy.background_lock_delay_ms(AppLockTimeout.ONE_HOUR, true))
    }

    @Test
    fun only_other_packages_count_as_external_targets() {
        assertTrue(AppLockTimeoutPolicy.is_external_target("com.android.documentsui", "org.astermail.android"))
        assertTrue(AppLockTimeoutPolicy.is_external_target(null, "org.astermail.android"))
        assertFalse(AppLockTimeoutPolicy.is_external_target("org.astermail.android", "org.astermail.android"))
    }
}
