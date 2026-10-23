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

package org.astermail.android.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginProofPinStore @Inject constructor(
    @ApplicationContext context: Context,
) : LoginPinSource {

    private val lock = Any()

    private val prefs: SharedPreferences? =
        runCatching { context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE) }.getOrNull()

    @Volatile
    private var entries: List<String> = read_entries()

    override fun is_pinned(keys: List<String>): Boolean = LoginProof.is_pinned(entries, keys)

    override fun pin(keys: List<String>) {
        synchronized(lock) {
            val updated = LoginProof.add_pins(entries, keys)
            if (updated == entries) return
            entries = updated
            runCatching { prefs?.edit()?.putString(key_entries, LoginProof.serialize_pins(updated))?.commit() }
        }
    }

    private fun read_entries(): List<String> =
        runCatching { LoginProof.parse_pins(prefs?.getString(key_entries, null)) }.getOrDefault(emptyList())

    companion object {
        private const val prefs_name = "aster_login_proof_pins_v1"
        private const val key_entries = "pinned_accounts"
    }
}
