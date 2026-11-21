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
import org.junit.Test

class PinAttemptsLeftTest {

    @Test
    fun `a clean slate leaves every attempt and shows nothing`() {
        assertEquals(PinLockout.MAX_ATTEMPTS, PinLockout.attempts_left(0))
        assertEquals(PinAttemptNotice.None, PinLockout.notice_for(0))
    }

    @Test
    fun `each failure before the lockout counts down the attempts left`() {
        assertEquals(PinAttemptNotice.AttemptsLeft(4), PinLockout.notice_for(1))
        assertEquals(PinAttemptNotice.AttemptsLeft(3), PinLockout.notice_for(2))
        assertEquals(PinAttemptNotice.AttemptsLeft(2), PinLockout.notice_for(3))
        assertEquals(PinAttemptNotice.AttemptsLeft(1), PinLockout.notice_for(4))
        assertEquals(1, PinLockout.attempts_left(PinLockout.MAX_ATTEMPTS - 1))
    }

    @Test
    fun `the last attempt left matches the failure that starts the lockout`() {
        val left = PinLockout.attempts_left(PinLockout.MAX_ATTEMPTS - 1)
        assertEquals(0L, PinLockout.lockout_ms_for(PinLockout.MAX_ATTEMPTS - 1))
        assertEquals(true, PinLockout.lockout_ms_for(PinLockout.MAX_ATTEMPTS - 1 + left) > 0L)
    }

    @Test
    fun `at or past the limit the next failure locks the app`() {
        assertEquals(0, PinLockout.attempts_left(PinLockout.MAX_ATTEMPTS))
        assertEquals(PinAttemptNotice.NextFailureLocks, PinLockout.notice_for(PinLockout.MAX_ATTEMPTS))
        assertEquals(0, PinLockout.attempts_left(PinLockout.MAX_ATTEMPTS + 1))
        assertEquals(PinAttemptNotice.NextFailureLocks, PinLockout.notice_for(PinLockout.MAX_ATTEMPTS + 1))
        assertEquals(0, PinLockout.attempts_left(Int.MAX_VALUE))
        assertEquals(PinAttemptNotice.NextFailureLocks, PinLockout.notice_for(Int.MAX_VALUE))
    }

    @Test
    fun `a negative count is treated as no failures`() {
        assertEquals(PinLockout.MAX_ATTEMPTS, PinLockout.attempts_left(-3))
        assertEquals(PinAttemptNotice.None, PinLockout.notice_for(-3))
        assertEquals(PinLockout.MAX_ATTEMPTS, PinLockout.attempts_left(Int.MIN_VALUE))
    }

    @Test
    fun `the notice follows the persisted failure count`() {
        var state = PinLockoutState()
        repeat(3) { state = PinLockout.after_failure(state, 1_000L, 7) }
        assertEquals(PinAttemptNotice.AttemptsLeft(2), PinLockout.notice_for(state.failed_attempts))
        repeat(2) { state = PinLockout.after_failure(state, 1_000L, 7) }
        assertEquals(true, state.duration_ms > 0L)
        val ended = PinLockout.anchored(state, 1_000L + PinLockout.MAX_LOCKOUT_MS, 7)
        assertEquals(0L, ended.duration_ms)
        assertEquals(PinAttemptNotice.NextFailureLocks, PinLockout.notice_for(ended.failed_attempts))
    }
}
