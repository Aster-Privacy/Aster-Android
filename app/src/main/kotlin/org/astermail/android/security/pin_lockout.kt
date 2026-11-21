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

data class PinLockoutState(
    val failed_attempts: Int = 0,
    val duration_ms: Long = 0L,
    val started_elapsed_ms: Long = 0L,
    val boot_count: Int = UNKNOWN_BOOT_COUNT,
    val boot_wall_ms: Long = UNKNOWN_BOOT_WALL,
) {
    companion object {
        const val UNKNOWN_BOOT_COUNT = -1
        const val UNKNOWN_BOOT_WALL = Long.MIN_VALUE
    }
}

sealed interface PinAttemptNotice {
    data object None : PinAttemptNotice
    data class AttemptsLeft(val count: Int) : PinAttemptNotice
    data object NextFailureLocks : PinAttemptNotice
}

object PinLockout {
    const val MAX_ATTEMPTS = 5
    const val BASE_LOCKOUT_MS = 5L * 60 * 1000
    const val MAX_LOCKOUT_MS = 60L * 60 * 1000
    const val BOOT_WALL_TOLERANCE_MS = 30L * 1000
    const val ANCHOR_STEP_MS = 30L * 1000

    fun attempts_left(failed_attempts: Int): Int =
        (MAX_ATTEMPTS - failed_attempts.coerceAtLeast(0)).coerceAtLeast(0)

    fun notice_for(failed_attempts: Int): PinAttemptNotice {
        if (failed_attempts <= 0) return PinAttemptNotice.None
        val left = attempts_left(failed_attempts)
        return if (left > 0) PinAttemptNotice.AttemptsLeft(left) else PinAttemptNotice.NextFailureLocks
    }

    fun boot_wall_ms(now_wall_ms: Long?, now_elapsed_ms: Long): Long =
        now_wall_ms?.let { it - now_elapsed_ms } ?: PinLockoutState.UNKNOWN_BOOT_WALL

    private fun boot_moved(state: PinLockoutState, boot_count: Int, boot_wall: Long): Boolean {
        val counted = state.boot_count != PinLockoutState.UNKNOWN_BOOT_COUNT &&
            boot_count != PinLockoutState.UNKNOWN_BOOT_COUNT
        if (counted) return false
        if (state.boot_wall_ms == PinLockoutState.UNKNOWN_BOOT_WALL) return false
        if (boot_wall == PinLockoutState.UNKNOWN_BOOT_WALL) return false
        return kotlin.math.abs(boot_wall - state.boot_wall_ms) > BOOT_WALL_TOLERANCE_MS
    }

    fun lockout_ms_for(failed_attempts: Int): Long {
        val over = (failed_attempts - MAX_ATTEMPTS).coerceAtMost(20)
        if (over < 0) return 0L
        return minOf(BASE_LOCKOUT_MS shl over, MAX_LOCKOUT_MS)
    }

    fun after_failure(
        state: PinLockoutState,
        now_elapsed_ms: Long,
        boot_count: Int,
        now_wall_ms: Long? = null,
    ): PinLockoutState {
        val attempts = state.failed_attempts + 1
        return PinLockoutState(
            failed_attempts = attempts,
            duration_ms = lockout_ms_for(attempts),
            started_elapsed_ms = now_elapsed_ms,
            boot_count = boot_count,
            boot_wall_ms = boot_wall_ms(now_wall_ms, now_elapsed_ms),
        )
    }

    fun anchored(
        state: PinLockoutState,
        now_elapsed_ms: Long,
        boot_count: Int,
        now_wall_ms: Long? = null,
    ): PinLockoutState {
        if (state.duration_ms <= 0L) return state
        val boot_wall = boot_wall_ms(now_wall_ms, now_elapsed_ms)
        val counted_reboot = state.boot_count != PinLockoutState.UNKNOWN_BOOT_COUNT &&
            boot_count != PinLockoutState.UNKNOWN_BOOT_COUNT &&
            state.boot_count != boot_count
        val ended_this_boot = !counted_reboot &&
            !boot_moved(state, boot_count, boot_wall) &&
            now_elapsed_ms >= state.started_elapsed_ms &&
            now_elapsed_ms - state.started_elapsed_ms >= state.duration_ms
        if (ended_this_boot) return state.copy(duration_ms = 0L, started_elapsed_ms = 0L)
        val rebooted = state.boot_count != boot_count ||
            now_elapsed_ms < state.started_elapsed_ms ||
            boot_moved(state, boot_count, boot_wall)
        if (rebooted) {
            return state.copy(
                duration_ms = state.duration_ms.coerceAtMost(MAX_LOCKOUT_MS),
                started_elapsed_ms = now_elapsed_ms,
                boot_count = boot_count,
                boot_wall_ms = boot_wall,
            )
        }
        val remaining = remaining_ms(state, now_elapsed_ms)
        if (remaining <= 0L) return state.copy(duration_ms = 0L, started_elapsed_ms = 0L)
        if (state.duration_ms - remaining < ANCHOR_STEP_MS) return state
        return state.copy(duration_ms = remaining, started_elapsed_ms = now_elapsed_ms)
    }

    fun remaining_ms(state: PinLockoutState, now_elapsed_ms: Long): Long {
        if (state.duration_ms <= 0L) return 0L
        val passed = (now_elapsed_ms - state.started_elapsed_ms).coerceAtLeast(0L)
        return (state.duration_ms - passed).coerceAtLeast(0L)
    }

    fun from_legacy_deadlines(
        failed_attempts: Int,
        wall_deadline_ms: Long,
        elapsed_deadline_ms: Long,
        now_wall_ms: Long,
        now_elapsed_ms: Long,
        boot_count: Int,
    ): PinLockoutState {
        val elapsed_remaining = (elapsed_deadline_ms - now_elapsed_ms).takeIf { it in 1..MAX_LOCKOUT_MS } ?: 0L
        val wall_remaining = (wall_deadline_ms - now_wall_ms).coerceIn(0L, MAX_LOCKOUT_MS)
        val remaining = maxOf(elapsed_remaining, wall_remaining)
        return PinLockoutState(
            failed_attempts = failed_attempts,
            duration_ms = remaining,
            started_elapsed_ms = now_elapsed_ms,
            boot_count = boot_count,
            boot_wall_ms = boot_wall_ms(now_wall_ms, now_elapsed_ms),
        )
    }
}
