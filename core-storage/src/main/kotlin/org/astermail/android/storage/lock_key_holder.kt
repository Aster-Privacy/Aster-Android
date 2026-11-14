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

package org.astermail.android.storage

import java.util.concurrent.CopyOnWriteArrayList

object LockKeyHolder {

    private val guard = Any()
    private var lock_key: ByteArray? = null
    private val open_listeners = CopyOnWriteArrayList<() -> Unit>()
    private val close_listeners = CopyOnWriteArrayList<() -> Unit>()

    fun is_open(): Boolean = synchronized(guard) { lock_key != null }

    fun current(): ByteArray? = synchronized(guard) { lock_key?.copyOf() }

    fun open(key: ByteArray) {
        require(key.size == LOCK_KEY_BYTES)
        val was_closed = synchronized(guard) {
            val closed = lock_key == null
            lock_key?.fill(0)
            lock_key = key.copyOf()
            closed
        }
        if (was_closed) for (listener in open_listeners) runCatching { listener() }
    }

    fun close() {
        for (listener in close_listeners) runCatching { listener() }
        synchronized(guard) {
            lock_key?.fill(0)
            lock_key = null
        }
    }

    fun add_open_listener(listener: () -> Unit) {
        open_listeners.addIfAbsent(listener)
    }

    fun add_close_listener(listener: () -> Unit) {
        close_listeners.addIfAbsent(listener)
    }

    fun <T> with_key(block: (ByteArray?) -> T): T {
        val key = current()
        try {
            return block(key)
        } finally {
            key?.fill(0)
        }
    }
}
