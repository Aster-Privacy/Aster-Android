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

    fun load_identities(account_id: String): Set<String> =
        prefs.getStringSet(IDENTITY_PREFIX + hash_entry_key(account_id), null)?.toSet().orEmpty()

    fun save_identities(account_id: String, identity_keys: Set<String>) {
        prefs.edit().putStringSet(IDENTITY_PREFIX + hash_entry_key(account_id), identity_keys).apply()
    }

    private fun pref_key(account_id: String): String = KEY_PREFIX + hash_entry_key(account_id)

    private companion object {
        const val PREFS = "device_contact_import"
        const val KEY_PREFIX = "baseline_v2_"
        const val IDENTITY_PREFIX = "identities_v1_"
    }
}

class DeletedContactTombstones(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(account_id: String): Set<String> =
        prefs.getString(pref_key(account_id), null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    fun record(account_id: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val updated = merge_tombstones(read_ordered(account_id), keys, removed = emptySet())
        write(account_id, updated)
    }

    fun forget(account_id: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val updated = merge_tombstones(read_ordered(account_id), emptyList(), removed = keys.toSet())
        write(account_id, updated)
    }

    private fun read_ordered(account_id: String): List<String> =
        prefs.getString(pref_key(account_id), null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private fun write(account_id: String, keys: List<String>) {
        prefs.edit().putString(pref_key(account_id), keys.joinToString(SEPARATOR)).apply()
    }

    private fun pref_key(account_id: String): String = KEY_PREFIX + hash_entry_key(account_id)

    private companion object {
        const val PREFS = "device_contact_import"
        const val KEY_PREFIX = "tombstones_v1_"
        const val SEPARATOR = ","
    }
}

const val MAX_CONTACT_TOMBSTONES = 5000

fun merge_tombstones(
    existing: List<String>,
    added: Collection<String>,
    removed: Set<String>,
    cap: Int = MAX_CONTACT_TOMBSTONES,
): List<String> {
    val fresh = added.toSet()
    val ordered = LinkedHashSet<String>()
    existing.filter { it !in removed && it !in fresh }.forEach { ordered.add(it) }
    fresh.filter { it !in removed }.forEach { ordered.add(it) }
    return ordered.toList().takeLast(cap)
}

fun hash_entry_key(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
