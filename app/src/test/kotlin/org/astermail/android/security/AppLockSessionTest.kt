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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {

    private var elapsed = 10_000_000L
    private var wall = 1_700_000_000_000L
    private val session = AppLockSession({ elapsed }, { wall })
    private val minute = 60_000L

    private fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }

    @Test
    fun starts_locked_on_cold_start() {
        assertFalse(session.unlocked)
        assertTrue(session.on_return(AppLockTimeout.ONE_HOUR))
    }

    @Test
    fun immediately_locks_as_soon_as_the_app_leaves() {
        session.mark_unlocked()
        assertTrue(session.on_leave(AppLockTimeout.IMMEDIATELY))
        assertFalse(session.unlocked)
    }

    @Test
    fun returning_inside_the_timeout_keeps_the_session() {
        session.mark_unlocked()
        assertFalse(session.on_leave(AppLockTimeout.FIVE_MINUTES))
        advance(4 * minute)
        assertFalse(session.on_return(AppLockTimeout.FIVE_MINUTES))
        assertTrue(session.unlocked)
    }

    @Test
    fun returning_after_the_timeout_locks() {
        session.mark_unlocked()
        session.on_leave(AppLockTimeout.ONE_MINUTE)
        advance(minute)
        assertTrue(session.on_return(AppLockTimeout.ONE_MINUTE))
        assertFalse(session.unlocked)
    }

    @Test
    fun the_departure_is_cleared_on_return_so_time_does_not_accumulate() {
        session.mark_unlocked()
        session.on_leave(AppLockTimeout.FIVE_MINUTES)
        advance(4 * minute)
        session.on_return(AppLockTimeout.FIVE_MINUTES)
        advance(10 * minute)
        assertFalse(session.on_return(AppLockTimeout.FIVE_MINUTES))
    }

    @Test
    fun background_expiry_locks_without_a_return() {
        session.mark_unlocked()
        session.on_leave(AppLockTimeout.FIFTEEN_MINUTES)
        advance(14 * minute)
        assertFalse(session.expire_if_due(AppLockTimeout.FIFTEEN_MINUTES))
        advance(minute)
        assertTrue(session.expire_if_due(AppLockTimeout.FIFTEEN_MINUTES))
        assertFalse(session.unlocked)
    }

    @Test
    fun a_file_picker_does_not_relock_with_immediately() {
        session.mark_unlocked()
        session.begin_external_flow()
        assertFalse(session.on_leave(AppLockTimeout.IMMEDIATELY))
        advance(2 * minute)
        assertFalse(session.on_return(AppLockTimeout.IMMEDIATELY))
        assertTrue(session.unlocked)
    }

    @Test
    fun the_external_grace_ends_and_is_single_use() {
        session.mark_unlocked()
        session.begin_external_flow()
        session.on_leave(AppLockTimeout.IMMEDIATELY)
        advance(AppLockTimeoutPolicy.EXTERNAL_FLOW_GRACE_MS + 1)
        assertTrue(session.on_return(AppLockTimeout.IMMEDIATELY))

        session.mark_unlocked()
        session.begin_external_flow()
        session.on_leave(AppLockTimeout.IMMEDIATELY)
        advance(minute)
        assertFalse(session.on_return(AppLockTimeout.IMMEDIATELY))
        assertTrue(session.on_leave(AppLockTimeout.IMMEDIATELY))
    }

    @Test
    fun an_abandoned_external_flow_expires() {
        session.mark_unlocked()
        session.begin_external_flow()
        advance(AppLockTimeoutPolicy.EXTERNAL_FLOW_GRACE_MS + 1)
        assertFalse(session.external_flow_active())
        assertTrue(session.on_leave(AppLockTimeout.IMMEDIATELY))
    }

    @Test
    fun setting_the_clock_back_while_away_locks() {
        session.mark_unlocked()
        session.on_leave(AppLockTimeout.ONE_HOUR)
        elapsed += minute
        wall -= 3 * 60 * minute
        assertTrue(session.on_return(AppLockTimeout.ONE_HOUR))
    }

    @Test
    fun setting_the_clock_forward_while_away_locks() {
        session.mark_unlocked()
        session.on_leave(AppLockTimeout.ONE_HOUR)
        elapsed += minute
        wall += 2 * 60 * minute
        assertTrue(session.on_return(AppLockTimeout.ONE_HOUR))
    }

    @Test
    fun a_reboot_locks_even_with_a_long_timeout() {
        session.mark_unlocked()
        session.on_leave(AppLockTimeout.ONE_HOUR)
        elapsed = 5_000L
        wall += minute
        assertTrue(session.on_return(AppLockTimeout.ONE_HOUR))
    }

    @Test
    fun leaving_while_locked_changes_nothing() {
        assertFalse(session.on_leave(AppLockTimeout.IMMEDIATELY))
        assertFalse(session.unlocked)
    }
}
