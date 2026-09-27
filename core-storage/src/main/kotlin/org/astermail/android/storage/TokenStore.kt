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

class TokenStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(SecurePrefs.open(context, prefs_name))

    private val _tokens = MutableStateFlow<Tokens?>(load_current())
    val tokens: StateFlow<Tokens?> = _tokens.asStateFlow()

    val access_token: String?
        get() = unsaved?.access_token ?: runCatching { prefs.getString(key_access, null) }.getOrNull()

    val refresh_token: String?
        get() = unsaved?.refresh_token ?: runCatching { prefs.getString(key_refresh, null) }.getOrNull()

    val csrf_token: String?
        get() = runCatching { prefs.getString(key_csrf, null) }.getOrNull()

    val has_unsaved_tokens: Boolean
        get() = unsaved != null

    private var unsaved: Tokens?
        get() = unsaved_by_prefs[prefs]
        set(value) {
            if (value == null) unsaved_by_prefs.remove(prefs) else unsaved_by_prefs[prefs] = value
        }

    suspend fun save(access: String, refresh: String) {
        val tokens = Tokens(access, refresh)
        unsaved = tokens
        val stored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            persist(tokens) || persist(tokens)
        }
        if (stored) unsaved_by_prefs.remove(prefs, tokens)
        _tokens.value = tokens
    }

    private fun persist(tokens: Tokens): Boolean = runCatching {
        prefs.edit()
            .putString(key_access, tokens.access_token)
            .putString(key_refresh, tokens.refresh_token)
            .commit()
    }.getOrDefault(false)

    fun save_csrf(csrf: String?) {
        runCatching {
            val edit = prefs.edit()
            if (csrf.isNullOrBlank()) edit.remove(key_csrf) else edit.putString(key_csrf, csrf)
            edit.apply()
        }
    }

    suspend fun clear() {
        unsaved = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                prefs.edit()
                    .remove(key_access)
                    .remove(key_refresh)
                    .remove(key_csrf)
                    .commit()
            }
        }
        _tokens.value = null
    }

    private fun load_current(): Tokens? {
        val a = runCatching { prefs.getString(key_access, null) }.getOrNull() ?: return null
        val r = runCatching { prefs.getString(key_refresh, null) }.getOrNull() ?: return null
        return Tokens(a, r)
    }

    companion object {
        private val unsaved_by_prefs: MutableMap<SharedPreferences, Tokens> =
            java.util.Collections.synchronizedMap(java.util.WeakHashMap())

        private const val prefs_name = "aster_tokens_v1"
        private const val key_access = "access_token"
        private const val key_refresh = "refresh_token"
        private const val key_csrf = "csrf_token"
    }
}
