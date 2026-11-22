//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.guard

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.WebAuthnAllowedCredential
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.auth.PasskeyCancelledException
import org.astermail.android.auth.PasskeyUnavailableException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class GuardPasskeyTest {
    private val user_a = UUID.fromString("0b6e2c1a-4f3d-4e5a-9b8c-7d6e5f4a3b2c")
    private val user_b = UUID.fromString("7a1d9e33-2c4b-4f60-8d1e-5b3a2c1d0e9f")
    private val options = WebAuthnAssertionOptions(
        challenge = "Y2hhbGxlbmdl",
        challenge_token = "token-1",
        rpId = "app.astermail.org",
        allowCredentials = listOf(WebAuthnAllowedCredential(id = "aGFyZHdhcmU")),
        timeout = 60000,
        userVerification = "preferred",
    )
    private val assertion_json = JSONObject()
        .put("id", "AQID")
        .put("rawId", "AQID")
        .put("type", "public-key")
        .put(
            "response",
            JSONObject()
                .put("authenticatorData", "YXV0aA")
                .put("clientDataJSON", "Y2xpZW50")
                .put("signature", "c2ln"),
        )
        .toString()

    private fun api(): GuardApi = mockk<GuardApi>(relaxed = true).also {
        coEvery { it.step_up_passkey_options() } returns options
    }

    @Test
    fun `request matches the web step-up shape`() {
        val json = JSONObject(guard_passkey_request_json(options))
        assertEquals("Y2hhbGxlbmdl", json.getString("challenge"))
        assertEquals("app.astermail.org", json.getString("rpId"))
        assertEquals(0, json.getJSONArray("allowCredentials").length())
        assertEquals("required", json.getString("userVerification"))
        assertEquals("client-device", json.getJSONArray("hints").getString(0))
        assertEquals(1, json.getJSONArray("hints").length())
    }

    @Test
    fun `proof carries the assertion and the challenge token`() = runTest {
        val proof = guard_passkey_proof(api(), user_a, { user_a }) { assertion_json }
        assertEquals("token-1", proof.assertion.challenge_token)
        assertEquals("AQID", proof.assertion.raw_id)
        assertEquals("c2ln", proof.assertion.response.signature)
    }

    @Test
    fun `switching accounts while the sheet is open drops the proof`() = runTest {
        var current = user_a
        try {
            guard_passkey_proof(api(), user_a, { current }) {
                current = user_b
                assertion_json
            }
            fail("expected account_changed")
        } catch (e: GuardPasskeyException) {
            assertEquals(guard_passkey_failure.account_changed, e.failure)
        }
    }

    @Test
    fun `a different active account never asks the server for options`() = runTest {
        val api = api()
        try {
            guard_passkey_proof(api, user_a, { user_b }) { assertion_json }
            fail("expected account_changed")
        } catch (e: GuardPasskeyException) {
            assertEquals(guard_passkey_failure.account_changed, e.failure)
        }
        coVerify(exactly = 0) { api.step_up_passkey_options() }
    }

    @Test
    fun `sheet errors map to passkey failures`() = runTest {
        val cases = mapOf(
            PasskeyCancelledException() to guard_passkey_failure.canceled,
            PasskeyCancelledException(interrupted = true) to guard_passkey_failure.interrupted,
            PasskeyUnavailableException() to guard_passkey_failure.no_passkey,
            PasskeyUnavailableException(no_provider = true) to guard_passkey_failure.unavailable,
        )
        cases.forEach { (error, expected) ->
            try {
                guard_passkey_proof(api(), user_a, { user_a }) { throw error }
                fail("expected $expected")
            } catch (e: GuardPasskeyException) {
                assertEquals(expected, e.failure)
            }
        }
    }

    @Test
    fun `server errors map to account and expiry failures`() {
        assertEquals(
            guard_passkey_failure.wrong_account,
            guard_passkey_failure_for(ApiError.ValidationError(listOf("Unknown credential"))),
        )
        assertEquals(
            guard_passkey_failure.account_changed,
            guard_passkey_failure_for(ApiError.ValidationError(listOf("Challenge user mismatch"))),
        )
        assertEquals(
            guard_passkey_failure.expired,
            guard_passkey_failure_for(ApiError.ValidationError(listOf("Challenge already used or expired"))),
        )
        assertNull(guard_passkey_failure_for(ApiError.ValidationError(listOf("Invalid password"))))
        assertNull(guard_passkey_failure_for(IllegalStateException()))
    }
}
