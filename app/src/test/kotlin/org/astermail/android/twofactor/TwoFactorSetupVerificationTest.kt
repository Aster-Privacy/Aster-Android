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

package org.astermail.android.twofactor

import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TwoFactorSetupVerificationTest {
    private val scanning = TwoFactorUiState(
        is_loading = false,
        is_busy = true,
        mode = TwoFactorMode.Setup,
        setup_secret = "setup-secret",
        setup_otpauth_uri = "otpauth://totp/example",
        setup_token = "setup-token",
        code_input = "123456",
        password_input = "typed",
    )

    @Test
    fun a_password_demand_returns_to_the_password_step() {
        val next = after_failed_setup_verification(
            scanning,
            ApiError.ForbiddenError("To make this change, confirm your password.", "STEP_UP_REQUIRED"),
            "confirm",
        )
        assertEquals(TwoFactorMode.SetupReauth, next.mode)
        assertFalse(next.is_busy)
        assertNull(next.setup_secret)
        assertNull(next.setup_otpauth_uri)
        assertNull(next.setup_token)
        assertEquals("", next.code_input)
        assertEquals("", next.password_input)
        assertEquals("confirm", next.error)
    }

    @Test
    fun a_wrong_code_stays_on_the_code_step() {
        val next = after_failed_setup_verification(
            scanning,
            ApiError.ValidationError(listOf("Invalid verification code"), "INVALID_TWO_FACTOR_CODE"),
            "wrong code",
        )
        assertEquals(TwoFactorMode.Setup, next.mode)
        assertFalse(next.is_busy)
        assertEquals("setup-token", next.setup_token)
        assertEquals("setup-secret", next.setup_secret)
        assertEquals("wrong code", next.error)
    }

    @Test
    fun a_rate_limit_stays_on_the_code_step() {
        val next = after_failed_setup_verification(scanning, ApiError.RateLimited(), "slow down")
        assertEquals(TwoFactorMode.Setup, next.mode)
        assertEquals("setup-token", next.setup_token)
        assertEquals("slow down", next.error)
    }
}
