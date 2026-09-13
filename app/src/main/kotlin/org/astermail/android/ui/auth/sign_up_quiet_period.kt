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

package org.astermail.android.ui.auth

import android.content.Context

private const val sign_up_quiet_period_ms = 24L * 60L * 60L * 1000L
private const val onboarding_prefs = "aster_onboarding"
private const val signed_up_at_key = "signed_up_at"

fun mark_signed_up_now(context: Context) {
    context.getSharedPreferences(onboarding_prefs, Context.MODE_PRIVATE)
        .edit()
        .putLong(signed_up_at_key, System.currentTimeMillis())
        .apply()
}

fun within_sign_up_quiet_period(context: Context): Boolean {
    val signed_up_at = context.getSharedPreferences(onboarding_prefs, Context.MODE_PRIVATE)
        .getLong(signed_up_at_key, 0L)
    return signed_up_at > 0L && System.currentTimeMillis() - signed_up_at < sign_up_quiet_period_ms
}
