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

package org.astermail.android.security

import android.content.Context

object LockdownStore {

    private const val PREFS_NAME = "aster_lockdown"
    private const val KEY_ENABLED = "lockdown_mode_enabled"

    fun is_enabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun set_enabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()
    }

    fun register_listener(
        context: Context,
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister_listener(
        context: Context,
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }
}
