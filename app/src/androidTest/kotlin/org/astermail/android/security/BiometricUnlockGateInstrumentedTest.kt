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
import android.security.keystore.KeyInfo
import android.security.keystore.UserNotAuthenticatedException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricUnlockGateInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun device_is_secured(): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguard.isDeviceSecure
    }

    private fun stored_key(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey("aster_app_lock_biometric_v1", null) as? SecretKey
    }

    @Before
    fun clear_gate_state() {
        BiometricUnlockGate.reset(context)
    }

    @After
    fun clear_gate_state_after() {
        BiometricUnlockGate.reset(context)
    }

    @Test
    fun an_unsecured_device_never_yields_an_unlockable_cipher() {
        if (device_is_secured()) return

        val preparation = BiometricUnlockGate.prepare(context)

        assertEquals(BiometricGatePreparation.Unavailable, preparation)
        assertNull(stored_key())
    }

    @Test
    fun the_generated_key_demands_user_authentication_on_every_use() {
        if (!device_is_secured()) return

        val preparation = BiometricUnlockGate.prepare(context)

        if (preparation is BiometricGatePreparation.Unavailable) return

        val key = stored_key()

        assertNotNull(key)

        val factory = SecretKeyFactory.getInstance(key!!.algorithm, "AndroidKeyStore")
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo

        assertTrue(info.isUserAuthenticationRequired)
        assertTrue(info.isInvalidatedByBiometricEnrollment)
        assertTrue(info.userAuthenticationValidityDurationSeconds <= 0)
    }

    @Test
    fun an_unauthenticated_cipher_cannot_produce_a_token() {
        if (!device_is_secured()) return

        val preparation = BiometricUnlockGate.prepare(context)

        if (preparation !is BiometricGatePreparation.Enroll) return

        var failure: Throwable? = null

        try {
            preparation.cipher.doFinal(ByteArray(32))
        } catch (thrown: GeneralSecurityException) {
            failure = thrown
        }

        assertNotNull("cipher produced a token without authentication", failure)

        val chain = generateSequence(failure) { it.cause }.toList()

        assertTrue(
            "unexpected failure chain: $chain",
            chain.any {
                it is UserNotAuthenticatedException ||
                    it is IllegalBlockSizeException ||
                    it is KeyStoreException
            },
        )
    }

    @Test
    fun complete_enroll_fails_and_writes_nothing_without_authentication() {
        if (!device_is_secured()) return

        val preparation = BiometricUnlockGate.prepare(context)

        if (preparation !is BiometricGatePreparation.Enroll) return

        val enrolled = BiometricUnlockGate.complete_enroll(context, preparation.cipher)

        assertFalse(enrolled)

        val second = BiometricUnlockGate.prepare(context)

        assertTrue(second is BiometricGatePreparation.Enroll)
    }

    @Test
    fun a_foreign_cipher_cannot_satisfy_verification() {
        if (!device_is_secured()) return

        val attacker_key = javax.crypto.KeyGenerator.getInstance("AES")
            .apply { init(256) }
            .generateKey()
        val attacker_cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, attacker_key) }

        assertFalse(BiometricUnlockGate.complete_verify(context, attacker_cipher))
    }

    @Test
    fun reset_removes_the_keystore_entry() {
        BiometricUnlockGate.prepare(context)
        BiometricUnlockGate.reset(context)

        assertNull(stored_key())
    }
}
