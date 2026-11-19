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

import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ratchet.RatchetApi

data class SyncFormatReadiness(val ratchet_state_v2: Boolean, val recovery_backup_v2: Boolean) {
    companion object {
        val none = SyncFormatReadiness(ratchet_state_v2 = false, recovery_backup_v2 = false)
    }
}

object sync_format_readiness {
    private const val ready_ttl_ms = 5 * 60 * 1000L
    private const val not_ready_ttl_ms = 30 * 1000L

    private data class cached(val readiness: SyncFormatReadiness, val fetched_at: Long)

    private val lock = Any()
    @Volatile private var fetcher: (suspend () -> SyncFormatReadiness?)? = null
    private val cache = HashMap<String, cached>()
    internal var clock: () -> Long = System::currentTimeMillis

    fun install(source: suspend () -> SyncFormatReadiness?) {
        synchronized(lock) {
            fetcher = source
            cache.clear()
        }
    }

    fun install_api(api: RatchetApi) = install(source_for(api))

    fun install_api_if_absent(make: () -> RatchetApi) {
        synchronized(lock) {
            if (fetcher != null) return
            fetcher = source_for(make())
            cache.clear()
        }
    }

    private fun source_for(api: RatchetApi): suspend () -> SyncFormatReadiness? = {
        api.fetch_sync_format_readiness()?.let {
            SyncFormatReadiness(it.ratchet_sync_v3_ready, it.recovery_backup_v2_ready)
        }
    }

    internal fun uninstall() {
        synchronized(lock) {
            fetcher = null
            cache.clear()
        }
    }

    fun reset() {
        synchronized(lock) { cache.clear() }
    }

    fun peek(account: String?): SyncFormatReadiness {
        val owner = account?.takeIf { it.isNotBlank() } ?: return SyncFormatReadiness.none
        return synchronized(lock) { cache[owner]?.readiness } ?: SyncFormatReadiness.none
    }

    suspend fun current(account: String?): SyncFormatReadiness {
        val owner = account?.takeIf { it.isNotBlank() } ?: return SyncFormatReadiness.none
        val now = clock()
        val source = synchronized(lock) {
            cache[owner]?.let { entry ->
                val settled = entry.readiness.ratchet_state_v2 && entry.readiness.recovery_backup_v2
                val ttl = if (settled) ready_ttl_ms else not_ready_ttl_ms
                if (now - entry.fetched_at in 0 until ttl) return entry.readiness
            }
            fetcher
        } ?: return SyncFormatReadiness.none
        val readiness = try {
            source() ?: SyncFormatReadiness.none
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            SyncFormatReadiness.none
        }
        synchronized(lock) { cache[owner] = cached(readiness, now) }
        return readiness
    }
}
