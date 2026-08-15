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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.astermail.android.storage.SecurePrefs

sealed class BiometricGatePreparation {
    data class Enroll(val cipher: Cipher) : BiometricGatePreparation()
    data class Verify(val cipher: Cipher) : BiometricGatePreparation()
    object Unavailable : BiometricGatePreparation()
}

object BiometricUnlockGate {

    private const val KEY_ALIAS = "aster_app_lock_biometric_v1"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "aster_app_lock_biometric"
    private const val KEY_TOKEN_CIPHERTEXT = "token_ciphertext"
    private const val KEY_TOKEN_IV = "token_iv"
    private const val KEY_TOKEN_DIGEST = "token_digest"
    private const val TOKEN_BYTES = 32
    private const val GCM_TAG_BITS = 128

    private fun prefs(context: Context) = SecurePrefs.open(context, PREFS_NAME)

    private fun keystore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun load_key(): SecretKey? {
        val store = keystore()
        if (!store.containsAlias(KEY_ALIAS)) return null
        return store.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun create_key(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun reset(context: Context) {
        runCatching {
            val store = keystore()
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        }
        runCatching {
            prefs(context).edit()
                .remove(KEY_TOKEN_CIPHERTEXT)
                .remove(KEY_TOKEN_IV)
                .remove(KEY_TOKEN_DIGEST)
                .apply()
        }
    }

    private fun has_token(context: Context): Boolean {
        val p = prefs(context)
        return p.contains(KEY_TOKEN_CIPHERTEXT) && p.contains(KEY_TOKEN_IV) && p.contains(KEY_TOKEN_DIGEST)
    }

    fun prepare(context: Context): BiometricGatePreparation = try {
        if (has_token(context)) {
            val key = load_key()
            if (key == null) {
                reset(context)
                prepare_enroll()
            } else {
                val p = prefs(context)
                val iv = Base64.decode(p.getString(KEY_TOKEN_IV, "") ?: "", Base64.NO_WRAP)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                BiometricGatePreparation.Verify(cipher)
            }
        } else {
            prepare_enroll()
        }
    } catch (invalidated: KeyPermanentlyInvalidatedException) {
        reset(context)
        runCatching { prepare_enroll() }.getOrElse { BiometricGatePreparation.Unavailable }
    } catch (t: Throwable) {
        BiometricGatePreparation.Unavailable
    }

    private fun prepare_enroll(): BiometricGatePreparation {
        val key = load_key() ?: create_key()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return BiometricGatePreparation.Enroll(cipher)
    }

    fun complete_enroll(context: Context, cipher: Cipher): Boolean = try {
        val token = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val ciphertext = cipher.doFinal(token)
        prefs(context).edit()
            .putString(KEY_TOKEN_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_TOKEN_DIGEST, Base64.encodeToString(digest(token), Base64.NO_WRAP))
            .commit()
        token.fill(0)
        true
    } catch (t: Throwable) {
        false
    }

    fun complete_verify(context: Context, cipher: Cipher): Boolean = try {
        val p = prefs(context)
        val ciphertext = Base64.decode(p.getString(KEY_TOKEN_CIPHERTEXT, "") ?: "", Base64.NO_WRAP)
        val expected = Base64.decode(p.getString(KEY_TOKEN_DIGEST, "") ?: "", Base64.NO_WRAP)
        val token = cipher.doFinal(ciphertext)
        val ok = constant_time_equals(digest(token), expected)
        token.fill(0)
        ok
    } catch (t: Throwable) {
        false
    }

    private fun digest(value: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(value)

    private fun constant_time_equals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size || a.isEmpty()) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
