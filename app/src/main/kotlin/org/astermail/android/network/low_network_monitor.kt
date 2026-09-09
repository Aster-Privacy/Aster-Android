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

package org.astermail.android.network

import android.content.Context
import org.astermail.android.api.network.low_network_state

object low_network_monitor {

    private const val PREFS_NAME = "aster_low_network"
    private const val KEY_PREFERENCE_ENABLED = "low_network_preference_enabled"

    fun start(context: Context) {
        low_network_state.set_preference(read_cached_preference(context.applicationContext))
    }

    fun apply_preference(context: Context, enabled: Boolean) {
        val app_context = context.applicationContext
        app_context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFERENCE_ENABLED, enabled)
            .apply()
        low_network_state.set_preference(enabled)
    }

    fun read_cached_preference(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFERENCE_ENABLED, false)

    fun is_active(context: Context): Boolean {
        if (!low_network_state.is_preference_enabled()) {
            low_network_state.set_preference(read_cached_preference(context.applicationContext))
        }
        return low_network_state.active()
    }
}
