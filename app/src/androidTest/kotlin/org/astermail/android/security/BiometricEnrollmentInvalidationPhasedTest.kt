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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.astermail.android.storage.SecurePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricEnrollmentInvalidationPhasedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun phase(): String =
        InstrumentationRegistry.getArguments().getString("biometric_phase").orEmpty()

    private fun device_is_secured(): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguard.isDeviceSecure
    }

    private fun stored_key(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey("aster_app_lock_biometric_v1", null) as? SecretKey
    }

    private fun encoded(size: Int): String = Base64.encodeToString(ByteArray(size) { 7 }, Base64.NO_WRAP)

    private fun write_binding() {
        SecurePrefs.open(context, "aster_app_lock_biometric").edit()
            .putString("token_ciphertext", encoded(48))
            .putString("token_iv", encoded(12))
            .putString("token_digest", encoded(32))
            .putInt("binding_version", BIOMETRIC_BINDING_VERSION)
            .commit()
    }

    private fun init_failure(key: SecretKey): Throwable? = try {
        Cipher.getInstance("AES/GCM/NoPadding")
            .init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ByteArray(12) { 7 }))
        null
    } catch (thrown: GeneralSecurityException) {
        thrown
    }

    @Test
    fun before_enrollment_the_bound_key_is_usable() {
        assumeTrue(phase() == "before_enrollment")
        assertTrue(device_is_secured())

        val preparation = BiometricUnlockGate.prepare_enroll(context)

        assertTrue(preparation is BiometricGatePreparation.Enroll)

        write_binding()

        val key = stored_key()

        assertNotNull(key)
        assertNull(init_failure(key!!))
        assertTrue(BiometricUnlockGate.is_enrolled(context))
        assertTrue(BiometricUnlockGate.prepare_unlock(context) is BiometricGatePreparation.Verify)
    }

    @Test
    fun after_enrollment_the_key_is_invalidated_and_the_binding_is_dropped() {
        assumeTrue(phase() == "after_enrollment")

        val key = stored_key()

        assertNotNull(key)
        assertTrue(BiometricUnlockGate.is_enrolled(context))
        assertTrue(init_failure(key!!) is KeyPermanentlyInvalidatedException)

        assertEquals(BiometricGatePreparation.Unavailable, BiometricUnlockGate.prepare_unlock(context))

        assertFalse(BiometricUnlockGate.is_enrolled(context))
        assertTrue(BiometricUnlockGate.is_rebind_pending(context))
        assertNull(stored_key())

        BiometricUnlockGate.reset(context)
    }
}
