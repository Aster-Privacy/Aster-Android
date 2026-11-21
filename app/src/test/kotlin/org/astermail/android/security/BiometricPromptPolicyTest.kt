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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptPolicyTest {

    @Test
    fun user_dismissals_stay_quiet() {
        assertEquals(BiometricErrorOutcome.IGNORE, biometric_error_outcome(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(BiometricErrorOutcome.IGNORE, biometric_error_outcome(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertEquals(BiometricErrorOutcome.IGNORE, biometric_error_outcome(BiometricPrompt.ERROR_CANCELED))
    }

    @Test
    fun lockout_and_transient_failures_show_a_message() {
        listOf(
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_NO_SPACE,
            BiometricPrompt.ERROR_VENDOR,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        ).forEach { code ->
            assertEquals("code $code", BiometricErrorOutcome.SHOW_MESSAGE, biometric_error_outcome(code))
        }
    }

    @Test
    fun missing_hardware_or_enrollment_hides_the_option_and_explains_why() {
        listOf(
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        ).forEach { code ->
            assertEquals("code $code", BiometricErrorOutcome.SHOW_MESSAGE_AND_HIDE, biometric_error_outcome(code))
        }
    }

    @Test
    fun an_unknown_error_code_is_never_swallowed() {
        assertEquals(BiometricErrorOutcome.SHOW_MESSAGE, biometric_error_outcome(999))
        assertEquals(BiometricErrorOutcome.SHOW_MESSAGE, biometric_error_outcome(-1))
    }

    @Test
    fun the_platform_message_wins_when_present() {
        assertEquals("Too many attempts", biometric_error_message("  Too many attempts ", "fallback"))
    }

    @Test
    fun a_blank_platform_message_falls_back() {
        assertEquals("fallback", biometric_error_message("   ", "fallback"))
        assertEquals("fallback", biometric_error_message(null, "fallback"))
    }

    @Test
    fun a_launch_needs_a_resumed_host_with_live_state() {
        assertEquals(BiometricLaunchBlock.NONE, biometric_launch_block(has_host = true, resumed = true, state_saved = false))
        assertEquals(BiometricLaunchBlock.NO_HOST, biometric_launch_block(has_host = false, resumed = true, state_saved = false))
        assertEquals(BiometricLaunchBlock.NOT_RESUMED, biometric_launch_block(has_host = true, resumed = false, state_saved = false))
        assertEquals(BiometricLaunchBlock.STATE_SAVED, biometric_launch_block(has_host = true, resumed = true, state_saved = true))
    }

    @Test
    fun auto_prompt_waits_for_resume() {
        assertFalse(should_auto_prompt_biometric(armed = true, resumed = false, biometric_available = true, biometric_enrolled = true))
        assertTrue(should_auto_prompt_biometric(armed = true, resumed = true, biometric_available = true, biometric_enrolled = true))
    }

    @Test
    fun auto_prompt_fires_once_per_arm() {
        assertFalse(should_auto_prompt_biometric(armed = false, resumed = true, biometric_available = true, biometric_enrolled = true))
    }

    @Test
    fun auto_prompt_needs_an_available_and_enrolled_sensor() {
        assertFalse(should_auto_prompt_biometric(armed = true, resumed = true, biometric_available = false, biometric_enrolled = true))
        assertFalse(should_auto_prompt_biometric(armed = true, resumed = true, biometric_available = true, biometric_enrolled = false))
    }

    @Test
    fun leaving_the_foreground_rearms_the_auto_prompt() {
        assertTrue(should_rearm_auto_prompt(started = false))
        assertFalse(should_rearm_auto_prompt(started = true))
    }
}
