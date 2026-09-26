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

package org.astermail.android.ui.drawer

import android.content.Context

object folder_expansion_store {
    private const val prefs_name = "aster_sidebar"
    private const val legacy_key = "expanded_folders"
    private const val max_tokens = 500
    private val token_pattern = Regex("^[A-Za-z0-9+/=_-]{1,128}$")

    private fun key_for(account_id: String?): String =
        if (account_id.isNullOrBlank()) legacy_key else "$legacy_key:$account_id"

    fun sanitize(raw: Collection<String>?): Set<String> =
        raw.orEmpty()
            .filter { token_pattern.matches(it) }
            .takeLast(max_tokens)
            .toCollection(LinkedHashSet())

    fun load(context: Context, account_id: String?): Set<String> {
        val prefs = context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(key_for(account_id), null)
            ?: prefs.getStringSet(legacy_key, null)
        return sanitize(raw)
    }

    fun save(context: Context, account_id: String?, tokens: Set<String>) {
        context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
            .edit()
            .remove(legacy_key)
            .putStringSet(key_for(account_id), sanitize(tokens))
            .apply()
    }

    fun clear(context: Context, account_id: String) {
        if (account_id.isBlank()) return
        context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
            .edit()
            .remove(key_for(account_id))
            .apply()
    }
}
