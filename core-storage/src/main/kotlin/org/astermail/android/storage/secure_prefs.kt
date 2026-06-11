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

object SecurePrefs {

    fun open(context: Context, name: String): SharedPreferences {
        val app = context.applicationContext
        return try {
            create_encrypted(app, name)
        } catch (first: Throwable) {
            if (!is_corruption_error(first)) {
                throw SecurePrefsUnavailableException(name, first)
            }
            runCatching { app.deleteSharedPreferences(name) }
            try {
                create_encrypted(app, name)
            } catch (second: Throwable) {
                throw SecurePrefsUnavailableException(name, second).also {
                    it.addSuppressed(first)
                }
            }
        }
    }

    private fun is_corruption_error(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            val msg = cur.message?.lowercase().orEmpty()
            val cn = cur.javaClass.name
            if (cn.contains("InvalidProtocolBufferException", ignoreCase = true)) return true
            if (cn.contains("GeneralSecurityException", ignoreCase = true) &&
                (msg.contains("decryption") || msg.contains("invalid") || msg.contains("tag"))
            ) return true
            if (msg.contains("decryption failed") || msg.contains("invalid protocol buffer")) return true
            cur = cur.cause
        }
        return false
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

class SecurePrefsUnavailableException(name: String, cause: Throwable) :
    RuntimeException("encrypted prefs unavailable: $name", cause)
