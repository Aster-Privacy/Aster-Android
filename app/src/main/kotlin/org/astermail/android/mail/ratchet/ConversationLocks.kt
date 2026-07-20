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

package org.astermail.android.mail.ratchet

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ConversationLocks @Inject constructor() {

    private val locks = mutableMapOf<String, Mutex>()
    private val guard = Mutex()

    private suspend fun lock_for(conversation_id: String): Mutex = guard.withLock {
        locks.getOrPut(conversation_id) { Mutex() }
    }

    suspend fun <T> with_lock(conversation_id: String, block: suspend () -> T): T =
        lock_for(conversation_id).withLock { block() }
}
