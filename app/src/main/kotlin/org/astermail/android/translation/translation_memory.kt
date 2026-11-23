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

package org.astermail.android.translation

internal data class remembered_translation(
    val from: String,
    val body: String,
    val subject: String?,
)

internal object TranslationMemory {
    internal const val MAX_ENTRIES = 32

    private val entries = object : LinkedHashMap<String, remembered_translation>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, remembered_translation>?): Boolean =
            size > MAX_ENTRIES
    }

    fun key(source_hash: Int, target: String): String = "$source_hash:$target"

    fun get(key: String): remembered_translation? = synchronized(entries) { entries[key] }

    fun put(key: String, value: remembered_translation) {
        synchronized(entries) { entries[key] = value }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }
}
