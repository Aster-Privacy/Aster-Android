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

object stored_key_readiness {
    private const val ready_ttl_ms = 5 * 60 * 1000L
    private const val not_ready_ttl_ms = 30 * 1000L

    private data class cached(val ready: Boolean, val fetched_at: Long)

    private val lock = Any()
    @Volatile private var fetcher: (suspend () -> Boolean)? = null
    private val cache = HashMap<String, cached>()
    internal var clock: () -> Long = System::currentTimeMillis

    fun install(source: suspend () -> Boolean) {
        synchronized(lock) {
            fetcher = source
            cache.clear()
        }
    }

    fun reset() {
        synchronized(lock) { cache.clear() }
    }

    suspend fun is_ready(account: String?): Boolean {
        val owner = account?.takeIf { it.isNotBlank() } ?: return false
        val now = clock()
        val source = synchronized(lock) {
            cache[owner]?.let { entry ->
                val ttl = if (entry.ready) ready_ttl_ms else not_ready_ttl_ms
                if (now - entry.fetched_at in 0 until ttl) return entry.ready
            }
            fetcher
        } ?: return false
        val ready = try {
            source()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            false
        }
        synchronized(lock) { cache[owner] = cached(ready, now) }
        return ready
    }
}
