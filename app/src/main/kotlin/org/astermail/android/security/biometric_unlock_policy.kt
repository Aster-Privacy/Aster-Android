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

const val BIOMETRIC_BINDING_VERSION = 2

data class BiometricBindingState(
    val has_token: Boolean,
    val binding_version: Int,
    val key_present: Boolean,
    val key_valid: Boolean,
)

enum class BiometricUnlockAction { VERIFY, PIN_REQUIRED }

enum class BiometricEnrollOrigin { LOCK_SCREEN_BEFORE_PIN, AFTER_PIN_ON_LOCK_SCREEN, SETTINGS_AFTER_PIN }

fun biometric_unlock_action(state: BiometricBindingState): BiometricUnlockAction =
    if (
        state.has_token &&
        state.binding_version == BIOMETRIC_BINDING_VERSION &&
        state.key_present &&
        state.key_valid
    ) {
        BiometricUnlockAction.VERIFY
    } else {
        BiometricUnlockAction.PIN_REQUIRED
    }

fun biometric_binding_is_stale(state: BiometricBindingState): Boolean =
    state.has_token && biometric_unlock_action(state) == BiometricUnlockAction.PIN_REQUIRED

fun may_enroll_biometric_unlock(
    origin: BiometricEnrollOrigin,
    pin_verified: Boolean,
    biometric_available: Boolean,
): Boolean =
    origin != BiometricEnrollOrigin.LOCK_SCREEN_BEFORE_PIN && pin_verified && biometric_available

fun should_offer_biometric_unlock(biometric_available: Boolean, state: BiometricBindingState): Boolean =
    biometric_available &&
        state.has_token &&
        state.binding_version == BIOMETRIC_BINDING_VERSION

fun should_rebind_after_pin(rebind_pending: Boolean, biometric_available: Boolean): Boolean =
    rebind_pending && biometric_available
