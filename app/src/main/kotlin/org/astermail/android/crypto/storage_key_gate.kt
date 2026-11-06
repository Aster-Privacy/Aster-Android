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

package org.astermail.android.crypto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.astermail.android.storage.SessionKeyStore

class StorageKeyUnavailableException(message: String) : IllegalStateException(message)

object StorageKeyGate {

    val retry_delays_ms: List<Long> = listOf(0L, 2_000L, 5_000L, 10_000L, 30_000L, 60_000L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flight_lock = Any()

    @Volatile private var provisioner: (suspend () -> Boolean)? = null
    @Volatile private var in_flight: Deferred<Boolean>? = null
    @Volatile internal var sleeper: suspend (Long) -> Unit = { delay(it) }

    fun install(provision: suspend () -> Boolean) {
        provisioner = provision
    }

    fun reset() {
        synchronized(flight_lock) {
            provisioner = null
            in_flight = null
        }
        sleeper = { delay(it) }
    }

    private fun provision_once(): Deferred<Boolean>? = synchronized(flight_lock) {
        in_flight?.takeIf { it.isActive }?.let { return@synchronized it }
        val provision = provisioner ?: return@synchronized null
        scope.async { runCatching { provision() }.getOrDefault(false) }.also { in_flight = it }
    }

    suspend fun await_write_key(store: SessionKeyStore): ByteArray {
        StorageWriteKey.write_key(store)?.let { return it }
        if (provisioner == null) throw StorageKeyUnavailableException("no storage key")
        val user = store.get_user_id() ?: throw StorageKeyUnavailableException("no storage key")
        for (wait in retry_delays_ms) {
            if (wait > 0) sleeper(wait)
            if (store.get_user_id() != user) throw StorageKeyUnavailableException("account changed")
            val flight = provision_once() ?: throw StorageKeyUnavailableException("no storage key")
            flight.await()
            StorageWriteKey.write_key(store)?.let { return it }
        }
        throw StorageKeyUnavailableException("storage key pending")
    }
}
