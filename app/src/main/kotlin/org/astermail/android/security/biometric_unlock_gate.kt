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
    private const val KEY_BINDING_VERSION = "binding_version"
    private const val KEY_REBIND_PENDING = "rebind_pending"
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

    private fun delete_key() {
        runCatching {
            val store = keystore()
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        }
    }

    private fun drop_binding(context: Context, rebind_pending: Boolean) {
        delete_key()
        runCatching {
            prefs(context).edit()
                .remove(KEY_TOKEN_CIPHERTEXT)
                .remove(KEY_TOKEN_IV)
                .remove(KEY_TOKEN_DIGEST)
                .remove(KEY_BINDING_VERSION)
                .apply {
                    if (rebind_pending) putBoolean(KEY_REBIND_PENDING, true) else remove(KEY_REBIND_PENDING)
                }
                .commit()
        }
    }

    fun reset(context: Context) {
        drop_binding(context, rebind_pending = false)
    }

    private fun has_token(context: Context): Boolean {
        val p = prefs(context)
        return p.contains(KEY_TOKEN_CIPHERTEXT) && p.contains(KEY_TOKEN_IV) && p.contains(KEY_TOKEN_DIGEST)
    }

    private fun binding_state(context: Context, key_present: Boolean, key_valid: Boolean) =
        BiometricBindingState(
            has_token = has_token(context),
            binding_version = prefs(context).getInt(KEY_BINDING_VERSION, 0),
            key_present = key_present,
            key_valid = key_valid,
        )

    fun reconcile(context: Context) {
        runCatching {
            val key_present = runCatching { load_key() != null }.getOrDefault(false)
            val state = binding_state(context, key_present = key_present, key_valid = key_present)
            if (biometric_binding_is_stale(state)) drop_binding(context, rebind_pending = true)
        }
    }

    fun is_enrolled(context: Context): Boolean = runCatching {
        val p = prefs(context)
        has_token(context) && p.getInt(KEY_BINDING_VERSION, 0) == BIOMETRIC_BINDING_VERSION
    }.getOrDefault(false)

    fun is_rebind_pending(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_REBIND_PENDING, false) }.getOrDefault(false)

    fun clear_rebind_pending(context: Context) {
        runCatching { prefs(context).edit().remove(KEY_REBIND_PENDING).commit() }
    }

    fun prepare_unlock(context: Context): BiometricGatePreparation = try {
        val key = load_key()
        val state = binding_state(context, key_present = key != null, key_valid = key != null)
        if (key == null || biometric_unlock_action(state) != BiometricUnlockAction.VERIFY) {
            if (biometric_binding_is_stale(state)) drop_binding(context, rebind_pending = true)
            BiometricGatePreparation.Unavailable
        } else {
            val iv = Base64.decode(prefs(context).getString(KEY_TOKEN_IV, "") ?: "", Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            BiometricGatePreparation.Verify(cipher)
        }
    } catch (invalidated: KeyPermanentlyInvalidatedException) {
        drop_binding(context, rebind_pending = true)
        BiometricGatePreparation.Unavailable
    } catch (t: Throwable) {
        BiometricGatePreparation.Unavailable
    }

    fun prepare_enroll(context: Context): BiometricGatePreparation = try {
        drop_binding(context, rebind_pending = false)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, create_key())
        BiometricGatePreparation.Enroll(cipher)
    } catch (t: Throwable) {
        delete_key()
        BiometricGatePreparation.Unavailable
    }

    fun complete_enroll(context: Context, cipher: Cipher): Boolean = try {
        val token = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val ciphertext = cipher.doFinal(token)
        prefs(context).edit()
            .putString(KEY_TOKEN_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_TOKEN_DIGEST, Base64.encodeToString(digest(token), Base64.NO_WRAP))
            .putInt(KEY_BINDING_VERSION, BIOMETRIC_BINDING_VERSION)
            .remove(KEY_REBIND_PENDING)
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
