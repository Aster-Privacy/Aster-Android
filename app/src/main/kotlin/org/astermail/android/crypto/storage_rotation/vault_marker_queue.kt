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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.storage.ScheduledUntaggedSnapshot

class VaultMarkerQueue(
    private val scope: CoroutineScope,
    private val write: suspend (folder_pins: Map<String, Int>, scheduled_untagged: ScheduledUntaggedSnapshot?) -> Boolean,
    private val retry_delays_ms: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val log: (String) -> Unit = { android.util.Log.w("VaultMarkerQueue", it) },
) {
    private val state = Any()
    private val flush_lock = Mutex()
    private val folder_pins = HashMap<String, Int>()
    private var scheduled_untagged: ScheduledUntaggedSnapshot? = null
    private var retry_job: Job? = null
    private var attempts = 0

    suspend fun add_folder_pin(folder_id: String, level: Int): Boolean {
        if (folder_id.isBlank()) return true
        synchronized(state) {
            val key = folder_id.lowercase(java.util.Locale.ROOT)
            if ((folder_pins[key] ?: 0) < level) folder_pins[key] = level
        }
        return flush()
    }

    suspend fun add_scheduled_untagged(snapshot: ScheduledUntaggedSnapshot): Boolean {
        synchronized(state) {
            if (scheduled_untagged == null) scheduled_untagged = snapshot
        }
        return flush()
    }

    fun pending_count(): Int = synchronized(state) {
        folder_pins.size + if (scheduled_untagged == null) 0 else 1
    }

    suspend fun flush(): Boolean = flush_lock.withLock {
        val (pins, scheduled) = synchronized(state) { HashMap(folder_pins) to scheduled_untagged }
        if (pins.isEmpty() && scheduled == null) return@withLock true
        val written = try {
            write(pins, scheduled)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        synchronized(state) {
            if (written) {
                for ((key, level) in pins) if (folder_pins[key] == level) folder_pins.remove(key)
                if (scheduled_untagged === scheduled) scheduled_untagged = null
                attempts = 0
            }
        }
        if (!written) {
            log("vault marker write failed; ${pins.size} folder pins and ${if (scheduled == null) 0 else 1} snapshot queued for retry")
            schedule_retry()
        }
        written
    }

    private fun schedule_retry() {
        synchronized(state) {
            if (retry_job?.isActive == true || retry_delays_ms.isEmpty()) return
            val wait = retry_delays_ms[minOf(attempts, retry_delays_ms.lastIndex)]
            attempts++
            retry_job = scope.launch {
                delay(wait)
                synchronized(state) { retry_job = null }
                flush()
            }
        }
    }

    companion object {
        val DEFAULT_RETRY_DELAYS_MS = listOf(15_000L, 60_000L, 300_000L, 900_000L)
    }
}
