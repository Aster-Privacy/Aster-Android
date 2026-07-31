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

import android.content.Context
import android.content.SharedPreferences

class PreferencesCacheStore(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.let {
        runCatching { SecurePrefs.open(it, prefs_name) }.getOrNull()
    }

    fun read(account_key: String?): String? {
        val key = entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write(account_key: String?, payload: String) {
        val key = entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_badges(account_key: String?): String? {
        val key = badge_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_badges(account_key: String?, payload: String) {
        val key = badge_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_signatures(account_key: String?): String? {
        val key = signature_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_signatures(account_key: String?, payload: String) {
        val key = signature_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_alias_preferences(account_key: String?): String? {
        val key = alias_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_alias_preferences(account_key: String?, payload: String) {
        val key = alias_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun clear(account_key: String?) {
        val key = entry_key(account_key) ?: return
        val badge_key = badge_entry_key(account_key)
        val signature_key = signature_entry_key(account_key)
        val alias_key = alias_entry_key(account_key)
        runCatching {
            prefs?.edit()?.apply {
                remove(key)
                if (badge_key != null) remove(badge_key)
                if (signature_key != null) remove(signature_key)
                if (alias_key != null) remove(alias_key)
            }?.apply()
        }
    }

    fun clear_all() {
        runCatching { prefs?.edit()?.clear()?.apply() }
    }

    private fun entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$key_prefix$id"
    }

    private fun badge_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$badge_key_prefix$id"
    }

    private fun signature_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$signature_key_prefix$id"
    }

    private fun alias_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$alias_key_prefix$id"
    }

    private companion object {
        const val prefs_name = "aster_preferences_cache"
        const val key_prefix = "prefs_json_"
        const val badge_key_prefix = "badges_json_"
        const val signature_key_prefix = "signatures_json_"
        const val alias_key_prefix = "alias_prefs_json_"
    }
}
