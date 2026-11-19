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

package org.astermail.android.settings

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.security.ReauthProofRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReauthFlowTest {
    private val context = mockk<android.content.Context> {
        every { getString(any()) } answers { "string-${firstArg<Int>()}" }
    }

    private val options = WebAuthnAssertionOptions(
        challenge = "Y2hhbGxlbmdl",
        challenge_token = "token-1",
        rpId = "app.astermail.org",
    )

    private val assertion_json = """
        {"id":"a2V5LTE","rawId":"a2V5LTE","type":"public-key",
         "response":{"authenticatorData":"YXV0aA","clientDataJSON":"Y2xpZW50","signature":"c2ln"}}
    """.trimIndent()

    @Test
    fun maps_server_codes_to_the_missing_proof() {
        assertEquals(ReauthDemand.password, reauth_demand_of(ApiError.StepUpRequired()))
        assertEquals(
            ReauthDemand.password,
            reauth_demand_of(ApiError.ValidationError(listOf("x"), code = "STEP_UP_REQUIRED")),
        )
        assertEquals(
            ReauthDemand.totp_code,
            reauth_demand_of(ApiError.ValidationError(listOf("x"), code = "TOTP_REQUIRED")),
        )
        assertEquals(
            ReauthDemand.security_key,
            reauth_demand_of(ApiError.ValidationError(listOf("x"), code = "SECURITY_KEY_REQUIRED")),
        )
        assertEquals(
            ReauthDemand.password,
            reauth_demand_of(ApiError.ForbiddenError(code = "STEP_UP_REQUIRED")),
        )
        assertNull(reauth_demand_of(ApiError.InvalidCredentials))
    }

    @Test
    fun password_and_code_go_out_in_the_first_request() = runBlocking {
        val sent = mutableListOf<ReauthProofRequest>()
        run_with_reauth(
            password_hash = "hash",
            totp_code = " 123456 ",
            key_assertion = { fail("no key prompt expected"); throw IllegalStateException() },
        ) { proof -> sent += proof }
        assertEquals(listOf(ReauthProofRequest(password_hash = "hash", totp_code = "123456")), sent)
    }

    @Test
    fun a_key_only_account_retries_with_a_key_assertion() = runBlocking {
        val sent = mutableListOf<ReauthProofRequest>()
        val result = run_with_reauth(
            password_hash = "hash",
            totp_code = null,
            key_assertion = { step_up_assertion_from(assertion_json, options) },
        ) { proof ->
            sent += proof
            if (proof.hardware_key_assertion == null) {
                throw ApiError.ValidationError(listOf("key"), code = "SECURITY_KEY_REQUIRED")
            }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, sent.size)
        val key = sent[1].hardware_key_assertion!!
        assertEquals("hash", sent[1].password_hash)
        assertEquals("a2V5LTE", key.raw_id)
        assertEquals("token-1", key.challenge_token)
        assertEquals("YXV0aA", key.response.authenticator_data)
    }

    @Test
    fun a_wrong_code_is_not_retried_with_a_key() = runBlocking {
        var calls = 0
        try {
            run_with_reauth(
                password_hash = "hash",
                totp_code = "000000",
                key_assertion = { fail("no key prompt expected"); throw IllegalStateException() },
            ) { _ ->
                calls += 1
                throw ApiError.ValidationError(listOf("code"), code = "SECURITY_KEY_REQUIRED")
            }
            fail("expected the error to surface")
        } catch (expected: ApiError.ValidationError) {
            assertEquals(1, calls)
        }
    }

    @Test
    fun a_totp_demand_surfaces_without_a_key_prompt() = runBlocking {
        var calls = 0
        try {
            run_with_reauth(
                password_hash = "hash",
                totp_code = null,
                key_assertion = { fail("no key prompt expected"); throw IllegalStateException() },
            ) { _ ->
                calls += 1
                throw ApiError.ValidationError(listOf("code"), code = "TOTP_REQUIRED")
            }
            fail("expected the error to surface")
        } catch (expected: ApiError.ValidationError) {
            assertEquals(1, calls)
            assertEquals(ReauthDemand.totp_code, reauth_demand_of(expected))
        }
    }

    @Test
    fun proof_serializes_with_the_server_field_names() {
        val encoded = Json { explicitNulls = false }.encodeToString(
            ReauthProofRequest.serializer(),
            ReauthProofRequest(
                password_hash = "hash",
                hardware_key_assertion = step_up_assertion_from(assertion_json, options),
            ),
        )
        assertTrue(encoded.contains("\"password_hash\":\"hash\""))
        assertTrue(encoded.contains("\"challenge_token\":\"token-1\""))
        assertTrue(encoded.contains("\"client_data_json\":\"Y2xpZW50\""))
        assertFalse(encoded.contains("totp_code"))
    }

    @Test
    fun a_totp_demand_asks_for_the_code_in_the_dialog() {
        val next = reauth_retry_prompt(
            context,
            ReauthPrompt(busy = true),
            ApiError.ValidationError(listOf("code"), code = "TOTP_REQUIRED"),
        )
        assertTrue(next.needs_code)
        assertFalse(next.busy)
        assertEquals("string-${org.astermail.android.R.string.error_2fa_code_required}", next.error)
    }

    @Test
    fun a_blank_password_never_reaches_the_server() = runBlocking {
        val outcome = reauthorize<Unit>(
            context = context,
            prompt = ReauthPrompt(),
            password = " ",
            totp_code = null,
            derive_password_hash = { fail("no derivation expected"); null },
            load_key_options = { options },
            get_credential = { assertion_json },
        ) { _ -> fail("no request expected") }
        assertTrue(outcome is Reauthorized.Retry)
        assertEquals(
            "string-${org.astermail.android.R.string.enter_account_password}",
            (outcome as Reauthorized.Retry).prompt.error,
        )
    }
}
