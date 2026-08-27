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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

enum class RefreshOutcome { Success, AuthFailed, Transient }

class SessionRefresher(
    private val read_refresh_token: () -> String?,
    private val perform_refresh: suspend (String?) -> RefreshOutcome,
) {

    private val mutex = Mutex()
    private val auth_failure_listeners = CopyOnWriteArrayList<suspend (String?) -> Unit>()

    fun on_auth_failure(listener: suspend (String?) -> Unit) {
        auth_failure_listeners.add(listener)
    }

    suspend fun refresh(): RefreshOutcome {
        val observed = read_refresh_token()
        var presented: String? = null
        val outcome = mutex.withLock {
            val current = read_refresh_token()
            if (!current.isNullOrEmpty() && current != observed) {
                RefreshOutcome.Success
            } else {
                presented = current
                perform_refresh(current)
            }
        }
        if (outcome == RefreshOutcome.AuthFailed) {
            auth_failure_listeners.forEach { listener -> runCatching { listener(presented) } }
        }
        return outcome
    }
}
