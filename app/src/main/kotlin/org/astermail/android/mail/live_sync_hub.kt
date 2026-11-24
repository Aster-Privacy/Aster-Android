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

import java.util.concurrent.CopyOnWriteArraySet

internal const val LIVE_SYNC_QUIET_MS = 120_000L
internal const val LIVE_SYNC_POLL_CHECK_MS = 5_000L
private val LIVE_SYNC_POLL_STEPS_MS = longArrayOf(30_000L, 60_000L, 120_000L)

interface live_sync_listener {
    fun on_live_sync_event(event: LiveSyncEvent)
    fun on_live_mail_mutation(action: String, item_ids: List<String>)
    fun on_live_sync_poll()
}

object live_sync_hub {
    private val listeners = CopyOnWriteArraySet<live_sync_listener>()

    @Volatile
    private var last_event_at = 0L

    fun register(listener: live_sync_listener) {
        listeners.add(listener)
    }

    fun unregister(listener: live_sync_listener) {
        listeners.remove(listener)
    }

    fun last_event_at(): Long = last_event_at

    fun mark_activity(now: Long = System.currentTimeMillis()) {
        last_event_at = now
    }

    fun dispatch_event(event: LiveSyncEvent, now: Long = System.currentTimeMillis()) {
        last_event_at = now
        listeners.forEach { listener -> runCatching { listener.on_live_sync_event(event) } }
    }

    fun dispatch_mutation(action: String, item_ids: List<String>, now: Long = System.currentTimeMillis()) {
        last_event_at = now
        listeners.forEach { listener -> runCatching { listener.on_live_mail_mutation(action, item_ids) } }
    }

    fun dispatch_poll() {
        listeners.forEach { listener -> runCatching { listener.on_live_sync_poll() } }
    }

    fun reset_activity() {
        last_event_at = 0L
    }
}

internal fun live_sync_poll_interval_ms(step: Int): Long =
    LIVE_SYNC_POLL_STEPS_MS[step.coerceIn(0, LIVE_SYNC_POLL_STEPS_MS.size - 1)]

internal fun live_sync_poll_due(
    connected: Boolean,
    last_event_at: Long,
    last_poll_at: Long,
    step: Int,
    now: Long,
): Boolean {
    val interval = live_sync_poll_interval_ms(step)
    if (connected) {
        if (last_event_at > 0L && now - last_event_at in 0L until LIVE_SYNC_QUIET_MS) return false
        return now - last_poll_at !in 0L until interval
    }
    val since = maxOf(last_poll_at, last_event_at)
    return now - since !in 0L until interval
}

internal fun live_sync_next_step(step: Int, event_seen: Boolean, polled: Boolean): Int = when {
    event_seen -> 0
    polled -> (step + 1).coerceAtMost(LIVE_SYNC_POLL_STEPS_MS.size - 1)
    else -> step
}
