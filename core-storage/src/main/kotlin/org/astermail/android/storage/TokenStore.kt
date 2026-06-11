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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Tokens(val access_token: String, val refresh_token: String)

class TokenStore(context: Context) {

    private val prefs: SharedPreferences = SecurePrefs.open(context, prefs_name)

    private val _tokens = MutableStateFlow<Tokens?>(load_current())
    val tokens: StateFlow<Tokens?> = _tokens.asStateFlow()

    val access_token: String?
        get() = runCatching { prefs.getString(key_access, null) }.getOrNull()

    val refresh_token: String?
        get() = runCatching { prefs.getString(key_refresh, null) }.getOrNull()

    val csrf_token: String?
        get() = runCatching { prefs.getString(key_csrf, null) }.getOrNull()

    suspend fun save(access: String, refresh: String) {
        runCatching {
            prefs.edit()
                .putString(key_access, access)
                .putString(key_refresh, refresh)
                .apply()
        }
        _tokens.value = Tokens(access, refresh)
    }

    fun save_csrf(csrf: String?) {
        runCatching {
            val edit = prefs.edit()
            if (csrf.isNullOrBlank()) edit.remove(key_csrf) else edit.putString(key_csrf, csrf)
            edit.apply()
        }
    }

    suspend fun clear() {
        runCatching {
            prefs.edit()
                .remove(key_access)
                .remove(key_refresh)
                .remove(key_csrf)
                .apply()
        }
        _tokens.value = null
    }

    private fun load_current(): Tokens? {
        val a = runCatching { prefs.getString(key_access, null) }.getOrNull() ?: return null
        val r = runCatching { prefs.getString(key_refresh, null) }.getOrNull() ?: return null
        return Tokens(a, r)
    }

    companion object {
        private const val prefs_name = "aster_tokens_v1"
        private const val key_access = "access_token"
        private const val key_refresh = "refresh_token"
        private const val key_csrf = "csrf_token"
    }
}
