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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val ANDROID_KEY_STORE = "AndroidKeyStore"

object SecurePrefs {

    @Volatile
    var key_material_lost: Boolean = false
        private set

    fun open(context: Context, name: String): SharedPreferences {
        val app = context.applicationContext
        return try {
            create_encrypted(app, name)
        } catch (first: Throwable) {
            runCatching { app.deleteSharedPreferences(name) }
            try {
                create_encrypted(app, name)
            } catch (second: Throwable) {
                reset_key_material(app, name)
                try {
                    create_encrypted(app, name)
                } catch (third: Throwable) {
                    key_material_lost = true
                    InMemoryPrefs()
                }
            }
        }
    }

    private fun reset_key_material(context: Context, name: String) {
        key_material_lost = true
        runCatching { context.deleteSharedPreferences(name) }
        runCatching {
            val store = java.security.KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (store.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                store.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }
    }

    private fun create_encrypted(context: Context, name: String): SharedPreferences {
        val master_key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            name,
            master_key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
