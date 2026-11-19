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
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object StrongBoxKeys {
    @Volatile
    var available_override: Boolean? = null

    fun available(context: Context): Boolean {
        available_override?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        }.getOrDefault(false)
    }

    fun request(builder: KeyGenParameterSpec.Builder, strongbox: Boolean): KeyGenParameterSpec.Builder {
        if (strongbox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder
    }

    fun <T> create(
        available: Boolean,
        fresh: Boolean = true,
        probe: (T) -> Boolean = { true },
        discard: () -> Unit = {},
        build: (strongbox: Boolean) -> T,
    ): T {
        if (available) {
            val made = runCatching { build(true) }
            made.getOrNull()?.let { key ->
                if (!fresh || runCatching { probe(key) }.getOrDefault(false)) return key
            }
            if (made.isSuccess) runCatching { discard() }
        }
        return build(false)
    }

    fun round_trips(key: SecretKey): Boolean {
        val sample = ByteArray(PROBE_BYTES) { it.toByte() }
        val sealer = Cipher.getInstance(PROBE_TRANSFORMATION)
        sealer.init(Cipher.ENCRYPT_MODE, key)
        val sealed = sealer.doFinal(sample)
        val opener = Cipher.getInstance(PROBE_TRANSFORMATION)
        opener.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(PROBE_TAG_BITS, sealer.iv))
        return opener.doFinal(sealed).contentEquals(sample)
    }

    fun starts_operation(key: SecretKey, not_authenticated: (Throwable) -> Boolean = ::is_user_not_authenticated): Boolean =
        try {
            Cipher.getInstance(PROBE_TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
            true
        } catch (t: Throwable) {
            not_authenticated(t)
        }

    fun stored_key(alias: String): SecretKey? = runCatching {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.getKey(alias, null) as? SecretKey
    }.getOrNull()

    fun has_key(alias: String): Boolean? = runCatching {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.containsAlias(alias)
    }.getOrNull()

    fun delete_key(alias: String) {
        runCatching {
            val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }

    private fun is_user_not_authenticated(t: Throwable): Boolean =
        t is UserNotAuthenticatedException

    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val PROBE_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PROBE_TAG_BITS = 128
    private const val PROBE_BYTES = 16
}
