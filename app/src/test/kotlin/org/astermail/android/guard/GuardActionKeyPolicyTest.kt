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

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import java.security.UnrecoverableKeyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardActionKeyPolicyTest {
    @Test
    fun api_30_and_later_requires_strong_biometric_or_credential() {
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, guard_authenticators(30))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, guard_authenticators(36))
    }

    @Test
    fun api_28_and_29_fall_back_to_weak_biometric_or_credential() {
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, guard_authenticators(28))
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, guard_authenticators(29))
    }

    @Test
    fun api_26_and_27_require_strong_biometric_or_credential() {
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, guard_authenticators(26))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, guard_authenticators(27))
    }

    @Test
    fun only_api_30_and_later_signs_through_a_crypto_object() {
        assertTrue(guard_uses_crypto_object(30))
        assertEquals(false, guard_uses_crypto_object(29))
    }

    @Test
    fun invalidated_key_maps_to_key_lost() {
        assertEquals(guard_sign_prep.key_lost, map_init_failure(KeyPermanentlyInvalidatedException()))
        assertEquals(guard_sign_prep.key_lost, map_init_failure(UnrecoverableKeyException()))
    }

    @Test(expected = IllegalStateException::class)
    fun unknown_init_failure_is_rethrown() {
        map_init_failure(IllegalStateException("x"))
    }

    @Test
    fun prompt_errors_map_to_results() {
        assertEquals(guard_sign_result.canceled, map_prompt_error(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(guard_sign_result.canceled, map_prompt_error(BiometricPrompt.ERROR_CANCELED))
        assertEquals(guard_sign_result.canceled, map_prompt_error(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertEquals(guard_sign_result.no_lock_screen, map_prompt_error(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL))
        assertEquals(guard_sign_result.failed("lockout"), map_prompt_error(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
    }
}
