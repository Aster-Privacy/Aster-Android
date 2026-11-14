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

package org.astermail.android.settings

import android.content.Context
import android.content.SharedPreferences
import org.astermail.android.api.errors.ClientErrorReporter
import org.astermail.android.security.LockdownStore

fun crash_reports_allowed(consent: Boolean, lockdown_enabled: Boolean): Boolean =
    consent && !lockdown_enabled

object crash_reports {

    private const val PREFS_NAME = "aster_crash_reports"
    private const val KEY_ENABLED = "crash_reports_enabled"

    @Volatile
    private var lockdown_listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun start(context: Context) {
        val app = context.applicationContext
        sync_reporter(app)
        if (lockdown_listener == null) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> sync_reporter(app) }
            lockdown_listener = listener
            LockdownStore.register_listener(app, listener)
        }
    }

    fun read(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun set(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()
        sync_reporter(context)
    }

    private fun sync_reporter(context: Context) {
        ClientErrorReporter.set_enabled(
            crash_reports_allowed(read(context), LockdownStore.is_enabled(context.applicationContext)),
        )
    }
}
