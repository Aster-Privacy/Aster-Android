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

enum class AppLockTimeout(val key: String, val millis: Long) {
    IMMEDIATELY("immediately", 0L),
    ONE_MINUTE("1m", 60_000L),
    FIVE_MINUTES("5m", 5 * 60_000L),
    FIFTEEN_MINUTES("15m", 15 * 60_000L),
    ONE_HOUR("1h", 60 * 60_000L),
    ;

    companion object {
        val DEFAULT = IMMEDIATELY

        fun from_key(key: String?): AppLockTimeout = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

data class AppLockDeparture(val elapsed_ms: Long, val wall_ms: Long)

object AppLockTimeoutPolicy {
    const val EXTERNAL_FLOW_GRACE_MS = 5 * 60_000L
    const val WALL_BACKWARD_TOLERANCE_MS = 2_000L

    fun away_ms(departure: AppLockDeparture, now_elapsed_ms: Long, now_wall_ms: Long): Long? {
        if (now_elapsed_ms < departure.elapsed_ms) return null
        val wall_delta = now_wall_ms - departure.wall_ms
        if (wall_delta < -WALL_BACKWARD_TOLERANCE_MS) return null
        return maxOf(now_elapsed_ms - departure.elapsed_ms, wall_delta)
    }

    fun should_lock(
        session_unlocked: Boolean,
        departure: AppLockDeparture?,
        now_elapsed_ms: Long,
        now_wall_ms: Long,
        timeout: AppLockTimeout,
        external_flow: Boolean,
    ): Boolean {
        if (!session_unlocked) return true
        if (departure == null) return false
        if (now_elapsed_ms < departure.elapsed_ms) return true
        val elapsed = now_elapsed_ms - departure.elapsed_ms
        if (external_flow && elapsed <= EXTERNAL_FLOW_GRACE_MS) {
            return now_wall_ms - departure.wall_ms < -WALL_BACKWARD_TOLERANCE_MS
        }
        val away = away_ms(departure, now_elapsed_ms, now_wall_ms) ?: return true
        return away >= timeout.millis
    }

    fun locks_when_leaving(timeout: AppLockTimeout, external_flow: Boolean): Boolean =
        timeout == AppLockTimeout.IMMEDIATELY && !external_flow

    fun background_lock_delay_ms(timeout: AppLockTimeout, external_flow: Boolean): Long =
        if (external_flow) maxOf(timeout.millis, EXTERNAL_FLOW_GRACE_MS + 1_000L) else timeout.millis

    fun is_external_target(target_package: String?, own_package: String): Boolean =
        target_package != own_package
}

class AppLockSession(
    private val elapsed_clock: () -> Long,
    private val wall_clock: () -> Long,
) {
    @Volatile var unlocked: Boolean = false
        private set
    @Volatile private var departure: AppLockDeparture? = null
    @Volatile private var external_flow_started_ms: Long? = null

    fun mark_unlocked() {
        unlocked = true
        departure = null
        external_flow_started_ms = null
    }

    fun mark_locked() {
        unlocked = false
        departure = null
    }

    fun begin_external_flow() {
        external_flow_started_ms = elapsed_clock()
    }

    fun end_external_flow() {
        external_flow_started_ms = null
    }

    fun external_flow_active(): Boolean {
        val started = external_flow_started_ms ?: return false
        val now = elapsed_clock()
        return now >= started && now - started <= AppLockTimeoutPolicy.EXTERNAL_FLOW_GRACE_MS
    }

    fun on_leave(timeout: AppLockTimeout): Boolean {
        if (!unlocked) return false
        val external = external_flow_active()
        if (AppLockTimeoutPolicy.locks_when_leaving(timeout, external)) {
            mark_locked()
            return true
        }
        departure = AppLockDeparture(elapsed_clock(), wall_clock())
        return false
    }

    fun expire_if_due(timeout: AppLockTimeout): Boolean {
        if (!unlocked || departure == null) return false
        val lock = AppLockTimeoutPolicy.should_lock(
            session_unlocked = true,
            departure = departure,
            now_elapsed_ms = elapsed_clock(),
            now_wall_ms = wall_clock(),
            timeout = timeout,
            external_flow = external_flow_active(),
        )
        if (lock) mark_locked()
        return lock
    }

    fun on_return(timeout: AppLockTimeout): Boolean {
        val lock = AppLockTimeoutPolicy.should_lock(
            session_unlocked = unlocked,
            departure = departure,
            now_elapsed_ms = elapsed_clock(),
            now_wall_ms = wall_clock(),
            timeout = timeout,
            external_flow = external_flow_active(),
        )
        external_flow_started_ms = null
        if (lock) mark_locked() else departure = null
        return lock
    }
}
