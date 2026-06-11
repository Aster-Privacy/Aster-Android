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

class TrustedDeviceStore(context: Context) {

    private val prefs: SharedPreferences = SecurePrefs.open(context, prefs_name)

    fun get_token(user_email: String): String? {
        val key = key_for(user_email)
        return runCatching { prefs.getString(key, null) }.getOrNull()
    }

    fun put_token(user_email: String, token: String) {
        val key = key_for(user_email)
        runCatching { prefs.edit().putString(key, token).apply() }
    }

    fun clear(user_email: String) {
        val key = key_for(user_email)
        runCatching { prefs.edit().remove(key).apply() }
    }

    private fun key_for(user_email: String): String =
        "${key_prefix}${user_email.lowercase().trim()}"

    companion object {
        private const val prefs_name = "aster_trusted_devices_v1"
        private const val key_prefix = "td_"
    }
}
