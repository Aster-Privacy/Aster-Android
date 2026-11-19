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

import java.util.concurrent.ConcurrentHashMap

interface BoundFieldRecordStore {
    fun records(kind: String): Set<String>?
    fun save(kind: String, records: Set<String>): Boolean
}

object BoundFieldRecords {

    const val MAX_RECORDS = 4096

    @Volatile
    private var store: BoundFieldRecordStore? = null

    private val known = ConcurrentHashMap<String, LinkedHashSet<String>>()

    fun bind(next: BoundFieldRecordStore?) {
        store = next
        known.clear()
    }

    fun is_bound(kind: String, record_id: String?): Boolean {
        if (record_id.isNullOrEmpty()) return false
        val records = records_of(kind)
        return synchronized(records) { record_id in records }
    }

    fun mark(kind: String, record_id: String?) {
        if (record_id.isNullOrEmpty()) return
        val records = records_of(kind)
        synchronized(records) {
            if (!records.add(record_id)) return
            while (records.size > MAX_RECORDS) records.remove(records.first())
            runCatching { store?.save(kind, LinkedHashSet(records)) }
        }
    }

    private fun records_of(kind: String): LinkedHashSet<String> =
        known.computeIfAbsent(kind) { key ->
            LinkedHashSet(runCatching { store?.records(key) }.getOrNull().orEmpty())
        }
}
