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

package org.astermail.android.contacts

import android.content.Context
import java.security.MessageDigest

class DeviceImportBaseline(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(account_id: String): Set<String>? =
        prefs.getStringSet(pref_key(account_id), null)?.toSet()

    fun save(account_id: String, entry_keys: Set<String>) {
        prefs.edit().putStringSet(pref_key(account_id), entry_keys).apply()
    }

    private fun pref_key(account_id: String): String = KEY_PREFIX + hash_entry_key(account_id)

    private companion object {
        const val PREFS = "device_contact_import"
        const val KEY_PREFIX = "baseline_"
    }
}

fun hash_entry_key(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
