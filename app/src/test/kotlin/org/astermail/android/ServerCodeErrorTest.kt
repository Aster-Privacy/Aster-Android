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


package org.astermail.android

import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCodeErrorTest {
    @Test
    fun maps_every_account_state_code_to_a_string_resource() {
        val codes = listOf(
            "ACCOUNT_SUSPENDED",
            "ACCOUNT_LOCKED",
            "ACCOUNT_PROBATION",
            "VERIFICATION_REQUIRED",
            "CLIENT_UPGRADE_REQUIRED",
            "ALIAS_REENCRYPTION_INCOMPLETE",
            "SENDER_NOT_AUTHORIZED",
            "ALIAS_SENDING_DISABLED",
        )
        val seen = mutableSetOf<Int>()
        for (code in codes) {
            val res = server_code_string_res(code)
            assertNotNull(code, res)
            seen.add(res!!)
        }
        assertEquals(codes.size, seen.size)
    }

    @Test
    fun maps_a_taken_username_to_its_own_string() {
        assertEquals(R.string.error_username_taken, server_code_string_res(USERNAME_IN_USE_CODE))
    }

    @Test
    fun flags_only_a_username_conflict_as_taken() {
        assertTrue(is_username_taken(ApiError.Conflict("This username is already taken", USERNAME_IN_USE_CODE)))
        assertFalse(is_username_taken(ApiError.Conflict("Registration failed", "CONFLICT")))
        assertFalse(is_username_taken(ApiError.Conflict("conflict", null)))
        assertFalse(is_username_taken(ApiError.RateLimited()))
        assertFalse(is_username_taken(ApiError.ForbiddenError("nope", USERNAME_IN_USE_CODE)))
    }

    @Test
    fun maps_a_password_confirmation_demand_to_a_string_resource() {
        assertEquals(R.string.recovery_step_up_description, server_code_string_res(STEP_UP_REQUIRED_CODE))
        assertEquals("STEP_UP_REQUIRED", STEP_UP_REQUIRED_CODE)
    }

    @Test
    fun leaves_an_unknown_code_to_the_server_message() {
        assertNull(server_code_string_res("SOMETHING_ELSE"))
        assertNull(server_code_string_res(""))
    }

    @Test
    fun carries_the_server_code_on_forbidden_and_conflict() {
        assertEquals("ACCOUNT_SUSPENDED", ApiError.ForbiddenError("nope", "ACCOUNT_SUSPENDED").code)
        assertEquals("CLIENT_UPGRADE_REQUIRED", ApiError.Conflict("nope", "CLIENT_UPGRADE_REQUIRED").code)
        assertNull(ApiError.ForbiddenError("nope").code)
        assertNull(ApiError.Conflict("nope").code)
    }

    @Test
    fun tells_a_wrong_authenticator_code_apart_from_a_wrong_password() {
        assertEquals(R.string.error_invalid_2fa_code, validation_code_string_res("INVALID_TWO_FACTOR_CODE"))
        assertEquals(R.string.error_totp_code_replayed, validation_code_string_res("TOTP_CODE_REPLAYED"))
        assertEquals(R.string.error_2fa_code_required, validation_code_string_res("TOTP_REQUIRED"))
        assertNull(validation_code_string_res("INVALID_CREDENTIALS"))
        assertNull(validation_code_string_res(null))
    }

    @Test
    fun clears_the_code_entry_only_when_the_code_was_refused() {
        assertTrue(is_rejected_second_factor_code(ApiError.ValidationError(listOf("x"), "INVALID_TWO_FACTOR_CODE")))
        assertTrue(is_rejected_second_factor_code(ApiError.ValidationError(listOf("x"), "TOTP_CODE_REPLAYED")))
        assertFalse(is_rejected_second_factor_code(ApiError.ValidationError(listOf("x"), "TOTP_REQUIRED")))
        assertFalse(is_rejected_second_factor_code(ApiError.InvalidCredentials))
        assertFalse(is_rejected_second_factor_code(ApiError.ForbiddenError("nope", "INVALID_TWO_FACTOR_CODE")))
    }

    @Test
    fun scheduled_failure_names_a_disabled_sending_address() {
        assertEquals(R.string.error_alias_sending_disabled, scheduled_failure_string_res("ALIAS_SENDING_DISABLED"))
        assertEquals(R.string.scheduled_send_failed, scheduled_failure_string_res(null))
        assertEquals(R.string.scheduled_send_failed, scheduled_failure_string_res("SMTP_TIMEOUT"))
        assertEquals(R.string.error_alias_sending_disabled, send_failure_detail_res(" ALIAS_SENDING_DISABLED "))
        assertNull(send_failure_detail_res("SMTP_TIMEOUT"))
        assertNull(send_failure_detail_res(null))
    }
}
