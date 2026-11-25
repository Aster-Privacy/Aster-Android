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

enum class sidebar_show_all_section(val pref_key: String, val sync_key: String) {
    folders("folders_show_all", "sidebar_folders_show_all"),
    aliases("aliases_show_all", "sidebar_aliases_show_all"),
}

object sidebar_show_all_store {
    private const val prefs_name = "aster_sidebar"

    fun key_for(section: sidebar_show_all_section, account_id: String?): String =
        if (account_id.isNullOrBlank()) section.pref_key else "${section.pref_key}:$account_id"

    fun load(context: Context, section: sidebar_show_all_section, account_id: String?): Boolean? {
        val prefs = context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
        val key = key_for(section, account_id)
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun save(context: Context, section: sidebar_show_all_section, account_id: String?, show_all: Boolean) {
        context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key_for(section, account_id), show_all)
            .apply()
    }

    fun clear(context: Context, account_id: String) {
        if (account_id.isBlank()) return
        val editor = context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE).edit()
        sidebar_show_all_section.entries.forEach { editor.remove(key_for(it, account_id)) }
        editor.apply()
    }
}
