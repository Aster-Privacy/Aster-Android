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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricUnlockPolicyTest {

    private val bound = BiometricBindingState(
        has_token = true,
        binding_version = BIOMETRIC_BINDING_VERSION,
        key_present = true,
        key_valid = true,
    )
    private val never_enabled = BiometricBindingState(
        has_token = false,
        binding_version = 0,
        key_present = false,
        key_valid = false,
    )

    @Test
    fun a_current_binding_with_a_valid_key_verifies() {
        assertEquals(BiometricUnlockAction.VERIFY, biometric_unlock_action(bound))
    }

    @Test
    fun a_lock_without_biometrics_enabled_always_needs_the_pin() {
        assertEquals(BiometricUnlockAction.PIN_REQUIRED, biometric_unlock_action(never_enabled))
        assertEquals(
            BiometricUnlockAction.PIN_REQUIRED,
            biometric_unlock_action(never_enabled.copy(key_present = true, key_valid = true)),
        )
        assertFalse(should_offer_biometric_unlock(biometric_available = true, state = never_enabled))
    }

    @Test
    fun a_new_fingerprint_on_the_device_needs_the_pin() {
        val invalidated = bound.copy(key_valid = false)
        assertEquals(BiometricUnlockAction.PIN_REQUIRED, biometric_unlock_action(invalidated))
        assertTrue(biometric_binding_is_stale(invalidated))
    }

    @Test
    fun a_missing_key_needs_the_pin() {
        val missing = bound.copy(key_present = false, key_valid = false)
        assertEquals(BiometricUnlockAction.PIN_REQUIRED, biometric_unlock_action(missing))
        assertTrue(biometric_binding_is_stale(missing))
    }

    @Test
    fun an_enrollment_from_before_the_binding_change_needs_the_pin_once() {
        val legacy = bound.copy(binding_version = 0)
        assertEquals(BiometricUnlockAction.PIN_REQUIRED, biometric_unlock_action(legacy))
        assertTrue(biometric_binding_is_stale(legacy))
        assertFalse(should_offer_biometric_unlock(biometric_available = true, state = legacy))
    }

    @Test
    fun the_lock_screen_never_enrolls_before_the_pin() {
        for (pin_verified in listOf(true, false)) {
            assertFalse(
                may_enroll_biometric_unlock(
                    BiometricEnrollOrigin.LOCK_SCREEN_BEFORE_PIN,
                    pin_verified = pin_verified,
                    biometric_available = true,
                ),
            )
        }
    }

    @Test
    fun enrollment_needs_a_verified_pin_everywhere() {
        for (origin in BiometricEnrollOrigin.values()) {
            assertFalse(may_enroll_biometric_unlock(origin, pin_verified = false, biometric_available = true))
            assertFalse(may_enroll_biometric_unlock(origin, pin_verified = true, biometric_available = false))
        }
        assertTrue(
            may_enroll_biometric_unlock(
                BiometricEnrollOrigin.SETTINGS_AFTER_PIN,
                pin_verified = true,
                biometric_available = true,
            ),
        )
        assertTrue(
            may_enroll_biometric_unlock(
                BiometricEnrollOrigin.AFTER_PIN_ON_LOCK_SCREEN,
                pin_verified = true,
                biometric_available = true,
            ),
        )
    }

    @Test
    fun the_biometric_option_shows_only_for_a_current_binding() {
        assertTrue(should_offer_biometric_unlock(biometric_available = true, state = bound))
        assertFalse(should_offer_biometric_unlock(biometric_available = false, state = bound))
    }

    @Test
    fun a_rebind_is_offered_only_after_a_reset_binding() {
        assertTrue(should_rebind_after_pin(rebind_pending = true, biometric_available = true))
        assertFalse(should_rebind_after_pin(rebind_pending = false, biometric_available = true))
        assertFalse(should_rebind_after_pin(rebind_pending = true, biometric_available = false))
    }
}
