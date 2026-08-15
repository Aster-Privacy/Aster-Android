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
private const val health_prefs_name = "aster_secure_prefs_health"
private const val failure_key_prefix = "keystore_failure_"
private const val key_material_lost_flag = "key_material_lost"

object SecurePrefs {

    @Volatile
    var key_material_lost: Boolean = false
        private set

    fun open(context: Context, name: String): SharedPreferences {
        val app = context.applicationContext
        return try {
            create_encrypted(app, name).also { clear_failure_record(app, name) }
        } catch (strongbox: Throwable) {
            open_after_failure(app, name)
        }
    }

    fun was_key_material_lost(context: Context): Boolean =
        key_material_lost || health_prefs(context).getBoolean(key_material_lost_flag, false)

    private fun open_after_failure(app: Context, name: String): SharedPreferences {
        return try {
            create_encrypted(app, name, allow_strongbox = false).also { clear_failure_record(app, name) }
        } catch (first: Throwable) {
            runCatching { app.deleteSharedPreferences(name) }
            try {
                create_encrypted(app, name, allow_strongbox = false).also { clear_failure_record(app, name) }
            } catch (second: Throwable) {
                if (!record_failure(app, name)) {
                    StorageHealth.mark_secure_prefs_degraded()
                    return InMemoryPrefs()
                }
                reset_key_material(app, name)
                try {
                    create_encrypted(app, name, allow_strongbox = false).also { clear_failure_record(app, name) }
                } catch (third: Throwable) {
                    key_material_lost = true
                    StorageHealth.mark_key_material_lost()
                    InMemoryPrefs()
                }
            }
        }
    }

    private fun health_prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(health_prefs_name, Context.MODE_PRIVATE)

    private fun record_failure(context: Context, name: String): Boolean {
        val prefs = health_prefs(context)
        val key = failure_key_prefix + name
        val already_failed = prefs.getBoolean(key, false)
        if (!already_failed) {
            prefs.edit().putBoolean(key, true).apply()
        }
        return already_failed
    }

    private fun clear_failure_record(context: Context, name: String) {
        val prefs = health_prefs(context)
        val key = failure_key_prefix + name
        if (prefs.contains(key)) {
            prefs.edit().remove(key).commit()
        }
    }

    private fun reset_key_material(context: Context, name: String) {
        key_material_lost = true
        StorageHealth.mark_key_material_lost()
        runCatching { health_prefs(context).edit().putBoolean(key_material_lost_flag, true).commit() }
        runCatching { context.deleteSharedPreferences(name) }
        runCatching {
            val store = java.security.KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (store.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                store.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }
    }

    private fun strongbox_available(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun create_encrypted(
        context: Context,
        name: String,
        allow_strongbox: Boolean = true,
    ): SharedPreferences {
        val master_key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(allow_strongbox && strongbox_available(context))
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
