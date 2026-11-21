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

import androidx.biometric.BiometricPrompt

enum class BiometricErrorOutcome { IGNORE, SHOW_MESSAGE, SHOW_MESSAGE_AND_HIDE }

enum class BiometricLaunchBlock { NONE, NO_HOST, NOT_RESUMED, STATE_SAVED }

fun biometric_error_outcome(code: Int): BiometricErrorOutcome = when (code) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
    -> BiometricErrorOutcome.IGNORE
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
    -> BiometricErrorOutcome.SHOW_MESSAGE_AND_HIDE
    else -> BiometricErrorOutcome.SHOW_MESSAGE
}

fun biometric_error_message(platform_message: CharSequence?, fallback: String): String =
    platform_message?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

fun biometric_launch_block(has_host: Boolean, resumed: Boolean, state_saved: Boolean): BiometricLaunchBlock = when {
    !has_host -> BiometricLaunchBlock.NO_HOST
    !resumed -> BiometricLaunchBlock.NOT_RESUMED
    state_saved -> BiometricLaunchBlock.STATE_SAVED
    else -> BiometricLaunchBlock.NONE
}

fun should_auto_prompt_biometric(
    armed: Boolean,
    resumed: Boolean,
    biometric_available: Boolean,
    biometric_enrolled: Boolean,
): Boolean = armed && resumed && biometric_available && biometric_enrolled

fun should_rearm_auto_prompt(started: Boolean): Boolean = !started
