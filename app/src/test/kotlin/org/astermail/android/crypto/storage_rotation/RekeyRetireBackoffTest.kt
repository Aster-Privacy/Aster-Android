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

package org.astermail.android.crypto.storage_rotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RekeyRetireBackoffTest {

    private val pending = ResealRunResult(ResealRunStatus.INTERRUPTED, StepCounts(), LEGACY_KEY_RETIRE_PENDING)

    @Test
    fun a_pending_key_retirement_keeps_being_rescheduled() {
        var next = storage_rekey_next_run(pending, 0, 0)
        repeat(40) {
            assertNotNull(next)
            next = storage_rekey_next_run(pending, next!!.attempt, next!!.retire_attempt)
        }
        assertNotNull(next)
    }

    @Test
    fun later_retirement_runs_wait_longer_up_to_a_cap() {
        val waits = mutableListOf<Long>()
        var next = storage_rekey_next_run(pending, 0, 0)
        repeat(15) {
            val run = next ?: throw AssertionError("expected another retirement run")
            waits += run.delay_secs
            next = storage_rekey_next_run(pending, run.attempt, run.retire_attempt)
        }
        assertEquals(STORAGE_REKEY_RETIRE_WAIT_SECS, waits.first())
        assertTrue(waits.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(waits.last() > STORAGE_REKEY_RETIRE_WAIT_SECS)
        assertTrue(waits.all { it <= STORAGE_REKEY_RETIRE_MAX_WAIT_SECS })
        assertEquals(STORAGE_REKEY_RETIRE_MAX_WAIT_SECS, waits.last())
    }
}
