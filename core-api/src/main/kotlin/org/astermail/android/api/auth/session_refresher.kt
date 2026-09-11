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

package org.astermail.android.api.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

enum class RefreshOutcome { Success, AuthFailed, Transient }

class SessionRefresher(
    private val read_refresh_token: () -> String?,
    private val perform_refresh: suspend (String?) -> RefreshOutcome,
    private val notify_scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now_ms: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    private val mutex = Mutex()
    private val auth_failure_listeners = CopyOnWriteArrayList<suspend (String?) -> Unit>()
    private val refreshed_listeners = CopyOnWriteArrayList<suspend (String?) -> Unit>()

    @Volatile
    private var dead_token: String? = null
    private var transient_token: String? = null
    private var transient_failures = 0
    private var retry_after_ms = 0L

    fun on_auth_failure(listener: suspend (String?) -> Unit) {
        auth_failure_listeners.add(listener)
    }

    fun on_refreshed(listener: suspend (String?) -> Unit) {
        refreshed_listeners.add(listener)
    }

    suspend fun refresh(): RefreshOutcome {
        val observed = read_refresh_token()
        var presented: String? = null
        var fresh_outcome = false
        val outcome = mutex.withLock {
            val current = read_refresh_token()
            when {
                current.isNullOrEmpty() -> RefreshOutcome.AuthFailed
                current != observed -> RefreshOutcome.Success
                current == dead_token -> RefreshOutcome.AuthFailed
                current == transient_token && now_ms() < retry_after_ms -> RefreshOutcome.Transient
                else -> {
                    presented = current
                    fresh_outcome = true
                    val result = withContext(NonCancellable) { perform_refresh(current) }
                    record(current, result)
                    result
                }
            }
        }
        if (fresh_outcome) {
            val listeners = when (outcome) {
                RefreshOutcome.AuthFailed -> auth_failure_listeners
                RefreshOutcome.Success -> refreshed_listeners
                RefreshOutcome.Transient -> null
            }
            if (!listeners.isNullOrEmpty()) {
                val token = presented
                notify_scope.launch {
                    listeners.forEach { listener -> runCatching { listener(token) } }
                }
            }
        }
        return outcome
    }

    private fun record(presented: String, outcome: RefreshOutcome) {
        when (outcome) {
            RefreshOutcome.Success -> {
                transient_token = null
                transient_failures = 0
                retry_after_ms = 0L
            }
            RefreshOutcome.AuthFailed -> {
                dead_token = presented
                transient_token = null
                transient_failures = 0
                retry_after_ms = 0L
            }
            RefreshOutcome.Transient -> {
                if (transient_token != presented) {
                    transient_token = presented
                    transient_failures = 0
                }
                transient_failures += 1
                val shift = (transient_failures - 1).coerceAtMost(4)
                val backoff = (TRANSIENT_BACKOFF_BASE_MS shl shift).coerceAtMost(TRANSIENT_BACKOFF_MAX_MS)
                retry_after_ms = now_ms() + backoff
            }
        }
    }

    companion object {
        const val TRANSIENT_BACKOFF_BASE_MS = 1_000L
        const val TRANSIENT_BACKOFF_MAX_MS = 15_000L
    }
}
