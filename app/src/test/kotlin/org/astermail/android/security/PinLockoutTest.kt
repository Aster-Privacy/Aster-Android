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
import org.junit.Test

class PinLockoutTest {

    private val minute = 60_000L

    private fun fail_times(times: Int, now: Long, boot: Int = 7): PinLockoutState {
        var state = PinLockoutState()
        repeat(times) { state = PinLockout.after_failure(state, now, boot) }
        return state
    }

    @Test
    fun `lockout starts on the fifth failure and doubles up to the cap`() {
        assertEquals(0L, PinLockout.lockout_ms_for(4))
        assertEquals(5 * minute, PinLockout.lockout_ms_for(5))
        assertEquals(10 * minute, PinLockout.lockout_ms_for(6))
        assertEquals(40 * minute, PinLockout.lockout_ms_for(8))
        assertEquals(60 * minute, PinLockout.lockout_ms_for(9))
        assertEquals(60 * minute, PinLockout.lockout_ms_for(500))
    }

    @Test
    fun `lockout counts down on elapsed time only`() {
        val state = fail_times(5, now = 1_000_000L)
        assertEquals(5 * minute, PinLockout.remaining_ms(state, 1_000_000L))
        assertEquals(3 * minute, PinLockout.remaining_ms(state, 1_000_000L + 2 * minute))
        assertEquals(0L, PinLockout.remaining_ms(state, 1_000_000L + 6 * minute))
    }

    @Test
    fun `a reboot restarts the countdown instead of clearing it`() {
        val state = fail_times(5, now = 50 * minute, boot = 7)
        val after_boot = PinLockout.anchored(state, now_elapsed_ms = 2_000L, boot_count = 8)
        assertEquals(5 * minute, PinLockout.remaining_ms(after_boot, 2_000L))
        assertEquals(2_000L, after_boot.started_elapsed_ms)
        assertEquals(8, after_boot.boot_count)
    }

    @Test
    fun `a reboot is detected from elapsed time going back when the boot count is unknown`() {
        val unknown = PinLockoutState.UNKNOWN_BOOT_COUNT
        val state = fail_times(5, now = 50 * minute, boot = unknown)
        val after_boot = PinLockout.anchored(state, now_elapsed_ms = minute, boot_count = unknown)
        assertEquals(5 * minute, PinLockout.remaining_ms(after_boot, minute))
    }

    @Test
    fun `a reboot past the old start is caught from the wall clock when the boot count is unknown`() {
        val unknown = PinLockoutState.UNKNOWN_BOOT_COUNT
        val wall_at_failure = 1_000_000_000L
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, minute, unknown, wall_at_failure) }

        val after_boot = PinLockout.anchored(
            state,
            now_elapsed_ms = 4 * minute,
            boot_count = unknown,
            now_wall_ms = wall_at_failure + 10 * minute,
        )

        assertEquals(5 * minute, PinLockout.remaining_ms(after_boot, 4 * minute))
        assertEquals(wall_at_failure + 6 * minute, after_boot.boot_wall_ms)
    }

    @Test
    fun `a wall clock jump within one counted boot never lengthens or revives a lockout`() {
        val wall_at_failure = 1_000_000_000L
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, minute, 7, wall_at_failure) }

        val jumped_forward = PinLockout.anchored(state, 2 * minute, 7, wall_at_failure + 600 * minute)
        assertEquals(4 * minute, PinLockout.remaining_ms(jumped_forward, 2 * minute))

        val jumped_back = PinLockout.anchored(jumped_forward, 3 * minute, 7, wall_at_failure - 600 * minute)
        assertEquals(3 * minute, PinLockout.remaining_ms(jumped_back, 3 * minute))

        val expired = PinLockout.anchored(jumped_back, 7 * minute, 7, wall_at_failure + 9_000 * minute)
        assertEquals(0L, PinLockout.remaining_ms(expired, 7 * minute))
        val later = PinLockout.anchored(expired, 8 * minute, 7, wall_at_failure - 9_000 * minute)
        assertEquals(0L, PinLockout.remaining_ms(later, 8 * minute))
        assertEquals(0L, later.duration_ms)
    }

    @Test
    fun `a counted reboot keeps the time left even when the wall clock jumps`() {
        val wall_at_failure = 1_000_000_000L
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, 30 * minute, 7, wall_at_failure) }
        val checked = PinLockout.anchored(state, 32 * minute, 7, wall_at_failure + 2 * minute)

        val after_boot = PinLockout.anchored(checked, 40 * minute, 8, wall_at_failure + 900 * minute)

        assertEquals(3 * minute, PinLockout.remaining_ms(after_boot, 40 * minute))
    }

    @Test
    fun `the same boot keeps counting down when the wall clock agrees`() {
        val unknown = PinLockoutState.UNKNOWN_BOOT_COUNT
        val wall_at_failure = 1_000_000_000L
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, minute, unknown, wall_at_failure) }

        val checked = PinLockout.anchored(state, 3 * minute, unknown, wall_at_failure + 2 * minute + 5_000L)

        assertEquals(3 * minute, PinLockout.remaining_ms(checked, 3 * minute))
    }

    @Test
    fun `the anchor written at the first lockout survives a reboot right away`() {
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, 10 * minute, 7, 2_000_000_000L) }
        assertEquals(5 * minute, state.duration_ms)
        assertEquals(10 * minute, state.started_elapsed_ms)
        assertEquals(7, state.boot_count)

        val after_boot = PinLockout.anchored(state, 1_000L, 8, 2_000_000_000L + 30_000L)

        assertEquals(5 * minute, PinLockout.remaining_ms(after_boot, 1_000L))
        assertEquals(5, after_boot.failed_attempts)
    }

    @Test
    fun `a reboot that lands past the old start still restarts the countdown`() {
        val state = fail_times(5, now = minute, boot = 7)
        val after_boot = PinLockout.anchored(state, now_elapsed_ms = 4 * minute, boot_count = 8)
        assertEquals(5 * minute, PinLockout.remaining_ms(after_boot, 4 * minute))
    }

    @Test
    fun `the same boot keeps the stored anchor until time passes`() {
        val state = fail_times(5, now = minute, boot = 7)
        assertSame(state, PinLockout.anchored(state, now_elapsed_ms = minute, boot_count = 7))
        val checked = PinLockout.anchored(state, now_elapsed_ms = 3 * minute, boot_count = 7)
        assertEquals(3 * minute, checked.duration_ms)
        assertEquals(3 * minute, checked.started_elapsed_ms)
        assertEquals(3 * minute, PinLockout.remaining_ms(checked, 3 * minute))
    }

    @Test
    fun `a reboot carries over only the time left at the last check`() {
        val state = fail_times(5, now = minute, boot = 7)
        val checked = PinLockout.anchored(state, now_elapsed_ms = 5 * minute, boot_count = 7)
        val after_boot = PinLockout.anchored(checked, now_elapsed_ms = 2_000L, boot_count = 8)
        assertEquals(minute, PinLockout.remaining_ms(after_boot, 2_000L))
        assertEquals(0L, PinLockout.remaining_ms(after_boot, 2_000L + minute))
    }

    @Test
    fun `an expired lockout is cleared and does not come back after a reboot`() {
        val state = fail_times(5, now = minute, boot = 7)
        val expired = PinLockout.anchored(state, now_elapsed_ms = 7 * minute, boot_count = 7)
        assertEquals(0L, expired.duration_ms)
        assertEquals(5, expired.failed_attempts)
        val after_boot = PinLockout.anchored(expired, now_elapsed_ms = 2_000L, boot_count = 8)
        assertEquals(0L, PinLockout.remaining_ms(after_boot, 2_000L))
    }

    @Test
    fun `a lockout that ended in this boot stays ended when the boot count cannot be read`() {
        val unknown = PinLockoutState.UNKNOWN_BOOT_COUNT
        val wall_at_failure = 1_000_000_000L
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, minute, 7, wall_at_failure) }

        val checked = PinLockout.anchored(state, 7 * minute, unknown, wall_at_failure + 6 * minute)

        assertEquals(0L, checked.duration_ms)
        assertEquals(0L, PinLockout.remaining_ms(checked, 7 * minute))
        assertEquals(5, checked.failed_attempts)
        val after_boot = PinLockout.anchored(checked, 2_000L, 8, wall_at_failure + 20 * minute)
        assertEquals(0L, PinLockout.remaining_ms(after_boot, 2_000L))
    }

    @Test
    fun `a lockout that ended in this boot stays ended when the boot count was unknown at the failure`() {
        val unknown = PinLockoutState.UNKNOWN_BOOT_COUNT
        val wall_at_failure = 1_000_000_000L
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, minute, unknown, wall_at_failure) }

        val checked = PinLockout.anchored(state, 9 * minute, 7, wall_at_failure + 8 * minute)

        assertEquals(0L, checked.duration_ms)
    }

    @Test
    fun `a running lockout is not cut short when the boot count cannot be read`() {
        val unknown = PinLockoutState.UNKNOWN_BOOT_COUNT
        val wall_at_failure = 1_000_000_000L
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.after_failure(state, minute, 7, wall_at_failure) }

        val checked = PinLockout.anchored(state, 3 * minute, unknown, wall_at_failure + 2 * minute)

        assertEquals(5 * minute, PinLockout.remaining_ms(checked, 3 * minute))
    }

    @Test
    fun `failures keep counting after a lockout ends`() {
        val state = fail_times(6, now = minute)
        assertEquals(6, state.failed_attempts)
        assertEquals(10 * minute, state.duration_ms)
    }

    @Test
    fun `legacy deadlines carry the longer remaining time and never exceed the cap`() {
        val migrated = PinLockout.from_legacy_deadlines(
            failed_attempts = 5,
            wall_deadline_ms = 10_000_000L + 4 * minute,
            elapsed_deadline_ms = 500_000L + 2 * minute,
            now_wall_ms = 10_000_000L,
            now_elapsed_ms = 500_000L,
            boot_count = 3,
        )
        assertEquals(4 * minute, PinLockout.remaining_ms(migrated, 500_000L))
        assertEquals(5, migrated.failed_attempts)

        val far = PinLockout.from_legacy_deadlines(5, Long.MAX_VALUE / 2, 0L, 0L, 0L, 3)
        assertEquals(60 * minute, far.duration_ms)
    }

    @Test
    fun `no lockout means nothing to wait for`() {
        val state = fail_times(2, now = minute)
        assertEquals(0L, PinLockout.remaining_ms(state, minute))
        assertSame(state, PinLockout.anchored(state, 0L, 99))
    }
}
