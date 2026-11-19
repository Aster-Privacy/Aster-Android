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

package org.astermail.android.di

import android.content.SharedPreferences
import org.astermail.android.crypto.BoundFieldRecordStore

class SecureBoundFieldRecordStore(
    private val prefs: () -> SharedPreferences?,
) : BoundFieldRecordStore {

    override fun records(kind: String): Set<String>? = runCatching {
        val opened = prefs() ?: return@runCatching null
        opened.getString(entry_key(kind), null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toCollection(LinkedHashSet())
            .orEmpty()
    }.getOrNull()

    override fun save(kind: String, records: Set<String>): Boolean = runCatching {
        val editor = prefs()?.edit() ?: return@runCatching false
        editor.putString(entry_key(kind), records.joinToString(",")).commit()
    }.getOrDefault(false)

    companion object {
        const val PREFS_NAME = "aster_bound_field_records_v1"

        fun entry_key(kind: String): String = "bound_$kind"
    }
}
