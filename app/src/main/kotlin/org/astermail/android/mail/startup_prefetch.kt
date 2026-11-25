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

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

class StartupPrefetch<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val pending = ConcurrentHashMap<K, Deferred<V?>>()

    fun start(key: K, loader: suspend () -> V) {
        pending.computeIfAbsent(key) {
            scope.async(dispatcher) { runCatching { loader() }.getOrNull() }
        }
    }

    suspend fun take(key: K): V? {
        val deferred = pending.remove(key) ?: return null
        return try {
            deferred.await()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    fun invalidate(key: K) {
        pending.remove(key)?.cancel()
    }

    fun clear() {
        val keys = pending.keys.toList()
        keys.forEach { invalidate(it) }
    }
}
