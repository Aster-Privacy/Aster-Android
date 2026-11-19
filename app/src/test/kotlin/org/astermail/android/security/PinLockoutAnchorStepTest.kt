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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockoutAnchorStepTest {

    private val minute = 60_000L

    private fun locked(): PinLockoutState {
        var state = PinLockoutState()
        repeat(PinLockout.MAX_ATTEMPTS) { state = PinLockout.after_failure(state, minute, 7) }
        return state
    }

    @Test
    fun checks_a_second_apart_leave_the_stored_state_unchanged() {
        val state = locked()
        var current = state
        for (second in 1..29) {
            current = PinLockout.anchored(current, minute + second * 1_000L, 7)
            assertSame(state, current)
        }
        assertEquals(minute - 29_000L + 4 * minute, PinLockout.remaining_ms(current, minute + 29_000L))
    }

    @Test
    fun the_anchor_moves_once_a_step_has_passed() {
        val state = locked()
        val moved = PinLockout.anchored(state, minute + PinLockout.ANCHOR_STEP_MS, 7)
        assertTrue(moved !== state)
        assertEquals(state.duration_ms - PinLockout.ANCHOR_STEP_MS, moved.duration_ms)
    }

    @Test
    fun a_reboot_between_steps_never_shortens_the_lockout_below_the_time_left() {
        val state = locked()
        val checked = PinLockout.anchored(state, minute + 20_000L, 7)
        val after_boot = PinLockout.anchored(checked, 1_000L, 8)
        assertTrue(PinLockout.remaining_ms(after_boot, 1_000L) >= state.duration_ms - 20_000L)
    }
}
