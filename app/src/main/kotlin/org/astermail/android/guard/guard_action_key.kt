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

package org.astermail.android.guard

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed interface guard_sign_prep {
    data class ready(val signature: Signature, val needs_crypto_object: Boolean) : guard_sign_prep
    data object key_lost : guard_sign_prep
    data object no_lock_screen : guard_sign_prep
}

sealed interface guard_sign_result {
    class signed(val der: ByteArray) : guard_sign_result
    data object canceled : guard_sign_result
    data object key_lost : guard_sign_result
    data object no_lock_screen : guard_sign_result
    data class failed(val reason: String) : guard_sign_result
}

const val NOT_AUTHENTICATED_REASON = "not_authenticated"

fun guard_uses_crypto_object(sdk_int: Int): Boolean = sdk_int >= Build.VERSION_CODES.R

fun guard_authenticators(sdk_int: Int): Int =
    if (sdk_int == Build.VERSION_CODES.P || sdk_int == Build.VERSION_CODES.Q) {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }

fun map_init_failure(t: Throwable): guard_sign_prep = when (t) {
    is KeyPermanentlyInvalidatedException -> guard_sign_prep.key_lost
    is UnrecoverableKeyException -> guard_sign_prep.key_lost
    else -> throw t
}

fun map_prompt_error(code: Int): guard_sign_result = when (code) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    -> guard_sign_result.canceled
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    BiometricPrompt.ERROR_NO_BIOMETRICS,
    -> guard_sign_result.no_lock_screen
    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> guard_sign_result.failed("lockout")
    else -> guard_sign_result.failed("prompt_$code")
}

object GuardActionKey {
    const val LEGACY_ALIAS = "aster_guard_action_v1"
    const val ALIAS_PREFIX = "aster_guard_action_v1_"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val LEGACY_VALIDITY_SECONDS = 5
    private const val COORD_LEN = 32

    private fun keystore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    fun new_alias(): String = ALIAS_PREFIX + UUID.randomUUID()

    fun default_alias(user_id: UUID): String = ALIAS_PREFIX + user_id

    private fun spec(alias: String, strongbox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(LEGACY_VALIDITY_SECONDS)
        }
        if (strongbox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        return builder.build()
    }

    fun create(alias: String): ByteArray {
        delete(alias)
        val pub = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { generate(alias, strongbox = true) }.getOrElse {
                delete(alias)
                generate(alias, strongbox = false)
            }
        } else {
            generate(alias, strongbox = false)
        }
        return encode_point(pub)
    }

    private fun generate(alias: String, strongbox: Boolean): ECPublicKey {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
        generator.initialize(spec(alias, strongbox))
        return generator.generateKeyPair().public as ECPublicKey
    }

    fun public_key_sec1(alias: String): ByteArray? = runCatching {
        val store = keystore()
        if (!store.containsAlias(alias)) return null
        val pub = store.getCertificate(alias)?.publicKey as? ECPublicKey ?: return null
        encode_point(pub)
    }.getOrNull()

    fun delete(alias: String) {
        runCatching {
            val store = keystore()
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }

    internal fun private_key(alias: String): PrivateKey? {
        val store = keystore()
        if (!store.containsAlias(alias)) return null
        return store.getKey(alias, null) as? PrivateKey
    }

    fun prepare_signature(alias: String): guard_sign_prep = try {
        val key = private_key(alias)
        if (key == null) {
            guard_sign_prep.key_lost
        } else {
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            if (guard_uses_crypto_object(Build.VERSION.SDK_INT)) {
                signature.initSign(key)
                guard_sign_prep.ready(signature, needs_crypto_object = true)
            } else {
                try {
                    signature.initSign(key)
                } catch (_: UserNotAuthenticatedException) {
                }
                guard_sign_prep.ready(signature, needs_crypto_object = false)
            }
        }
    } catch (t: Throwable) {
        map_init_failure(t)
    }

    private fun encode_point(pub: ECPublicKey): ByteArray {
        val out = ByteArray(1 + 2 * COORD_LEN)
        out[0] = 0x04
        write_coord(pub.w.affineX, out, 1)
        write_coord(pub.w.affineY, out, 1 + COORD_LEN)
        return out
    }

    private fun write_coord(value: BigInteger, out: ByteArray, offset: Int) {
        val raw = value.toByteArray()
        val trimmed = if (raw.size > COORD_LEN) raw.copyOfRange(raw.size - COORD_LEN, raw.size) else raw
        System.arraycopy(trimmed, 0, out, offset + COORD_LEN - trimmed.size, trimmed.size)
    }
}

private fun device_is_secure(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

suspend fun guard_sign(
    activity: FragmentActivity,
    alias: String,
    message: ByteArray,
    title: String,
    subtitle: String,
): guard_sign_result = withContext(Dispatchers.Main) {
    val sdk = Build.VERSION.SDK_INT
    val authenticators = guard_authenticators(sdk)
    if (!device_is_secure(activity)) return@withContext guard_sign_result.no_lock_screen
    if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        return@withContext guard_sign_result.no_lock_screen
    }
    val prep = try {
        GuardActionKey.prepare_signature(alias)
    } catch (t: Throwable) {
        return@withContext guard_sign_result.failed(t.javaClass.simpleName)
    }
    val ready = when (prep) {
        is guard_sign_prep.ready -> prep
        guard_sign_prep.key_lost -> return@withContext guard_sign_result.key_lost
        guard_sign_prep.no_lock_screen -> return@withContext guard_sign_result.no_lock_screen
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(authenticators)
        .setConfirmationRequired(false)
        .build()
    suspendCancellableCoroutine { cont ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(finish_sign(alias, ready, result, message))
            }

            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (cont.isActive) cont.resume(map_prompt_error(code))
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        try {
            if (ready.needs_crypto_object) {
                prompt.authenticate(info, BiometricPrompt.CryptoObject(ready.signature))
            } else {
                prompt.authenticate(info)
            }
        } catch (t: Throwable) {
            if (cont.isActive) cont.resume(guard_sign_result.failed(t.javaClass.simpleName))
        }
        cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
    }
}

private fun finish_sign(
    alias: String,
    ready: guard_sign_prep.ready,
    result: BiometricPrompt.AuthenticationResult,
    message: ByteArray,
): guard_sign_result = try {
    val signature = if (ready.needs_crypto_object) {
        result.cryptoObject?.signature ?: return guard_sign_result.failed("no_crypto_object")
    } else {
        val key = GuardActionKey.private_key(alias) ?: return guard_sign_result.key_lost
        ready.signature.apply { initSign(key) }
    }
    signature.update(message)
    guard_sign_result.signed(signature.sign())
} catch (t: KeyPermanentlyInvalidatedException) {
    guard_sign_result.key_lost
} catch (t: UserNotAuthenticatedException) {
    guard_sign_result.failed(NOT_AUTHENTICATED_REASON)
} catch (t: UnrecoverableKeyException) {
    guard_sign_result.key_lost
} catch (t: Throwable) {
    guard_sign_result.failed(t.javaClass.simpleName)
}
