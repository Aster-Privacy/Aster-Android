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

class TimedOverrides(
    private val ttl_ms: Long,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry(val value: Boolean, val at: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    operator fun get(id: String): Boolean? {
        val entry = entries[id] ?: return null
        if (now() - entry.at >= ttl_ms) {
            entries.remove(id, entry)
            return null
        }
        return entry.value
    }

    operator fun set(id: String, value: Boolean) {
        entries[id] = Entry(value, now())
    }

    fun confirm(id: String, value: Boolean, grace_ms: Long) {
        val cutoff = now() - ttl_ms + grace_ms
        entries.computeIfPresent(id) { _, entry ->
            if (entry.value != value || entry.at <= cutoff) entry else entry.copy(at = cutoff)
        }
    }

    fun remove(id: String) {
        entries.remove(id)
    }

    fun containsKey(id: String): Boolean = get(id) != null

    fun isEmpty(): Boolean {
        if (entries.isEmpty()) return true
        val at_now = now()
        entries.entries.removeIf { at_now - it.value.at >= ttl_ms }
        return entries.isEmpty()
    }

    fun clear() {
        entries.clear()
    }
}
