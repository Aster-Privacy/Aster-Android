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

const val SENDER_KEY_HIT_TTL_MS = 60L * 60L * 1000L
const val SENDER_KEY_MISS_TTL_MS = 10L * 60L * 1000L

class SenderKeyCache(
    private val hit_ttl_ms: Long = SENDER_KEY_HIT_TTL_MS,
    private val miss_ttl_ms: Long = SENDER_KEY_MISS_TTL_MS,
    private val now_ms: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val key: String?, val stored_at_ms: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun fresh_entry(email: String): Entry? {
        val entry = entries[email] ?: return null
        val ttl = if (entry.key == null) miss_ttl_ms else hit_ttl_ms
        val age = now_ms() - entry.stored_at_ms
        if (age in 0 until ttl) return entry
        entries.remove(email, entry)
        return null
    }

    fun fresh_key(email: String): String? = fresh_entry(email)?.key

    fun recently_missed(email: String): Boolean = fresh_entry(email)?.let { it.key == null } == true

    fun remember(email: String, key: String?) {
        entries[email] = Entry(key?.takeIf { it.isNotBlank() }, now_ms())
    }

    fun clear() {
        entries.clear()
    }
}
