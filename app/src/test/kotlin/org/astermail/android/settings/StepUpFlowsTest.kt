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

import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.astermail.android.api.ApiClient
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.auth.WebAuthnAssertionData
import org.astermail.android.api.recovery.RecoveryApiImpl
import org.astermail.android.api.recovery_email.RecoveryEmailApiImpl
import org.astermail.android.api.recovery_email.SaveRecoveryEmailRequest
import org.astermail.android.api.security.StepUpKeyAssertion
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class StepUpFlowsTest {
    private lateinit var server: MockWebServer
    private val test_origin = "https://app.example.test"
    private val key_required = MockResponse()
        .setResponseCode(400)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error":"A security key is required","code":"SECURITY_KEY_REQUIRED"}""")
    private val assertion = StepUpKeyAssertion(
        raw_id = "a2V5LTE",
        response = WebAuthnAssertionData(
            authenticator_data = "YXV0aA",
            client_data_json = "Y2xpZW50",
            signature = "c2ln",
        ),
        challenge_token = "token-1",
    )
    private var key_prompts = 0
    private val key_assertion: suspend () -> StepUpKeyAssertion = {
        key_prompts += 1
        assertion
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun build_client(): ApiClient {
        val token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? = BearerTokens("access", "refresh")
            override suspend fun refresh(): BearerTokens? = load()
            override suspend fun clear() {}
        }
        return ApiClient(
            base_url = server.url("/").toString().trimEnd('/'),
            token_provider = token_provider,
            initial_csrf = "session-A:1799999999.sig",
            allow_cleartext_for_test = true,
            webauthn_origin = "$test_origin/",
        )
    }

    private fun ok(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun body_of(request: RecordedRequest): JSONObject = JSONObject(request.body.readUtf8())

    private fun assert_password_only(request: RecordedRequest) {
        val body = body_of(request)
        assertEquals("hash", body.getString("password_hash"))
        assertFalse(body.has("hardware_key_assertion"))
        assertEquals(null, request.getHeader("Origin"))
    }

    private fun assert_key_proof(request: RecordedRequest) {
        val body = body_of(request)
        assertEquals("hash", body.getString("password_hash"))
        val sent = body.getJSONObject("hardware_key_assertion")
        assertEquals("a2V5LTE", sent.getString("raw_id"))
        assertEquals("token-1", sent.getString("challenge_token"))
        assertEquals("c2ln", sent.getJSONObject("response").getString("signature"))
        assertEquals(test_origin, request.getHeader("Origin"))
    }

    @Test
    fun recovery_codes_check_answers_a_key_demand_with_a_key() = runBlocking {
        server.enqueue(key_required)
        server.enqueue(ok("""{"step_up_token":"step-1","expires_at":"2026-11-18T00:00:00Z"}"""))
        val response = verify_codes_with_reauth(
            recovery_api = RecoveryApiImpl(build_client()),
            password_hash = "hash",
            totp_code = null,
            key_assertion = key_assertion,
        )
        assertEquals("step-1", response.step_up_token)
        assertEquals(1, key_prompts)
        assertEquals(2, server.requestCount)
        assert_password_only(server.takeRequest())
        assert_key_proof(server.takeRequest())
    }

    @Test
    fun recovery_codes_check_with_a_code_does_not_ask_for_a_key() = runBlocking {
        server.enqueue(key_required)
        try {
            verify_codes_with_reauth(
                recovery_api = RecoveryApiImpl(build_client()),
                password_hash = "hash",
                totp_code = "123456",
                key_assertion = key_assertion,
            )
            fail("expected the key demand to surface")
        } catch (t: Throwable) {
            assertEquals(ReauthDemand.security_key, reauth_demand_of(t))
        }
        assertEquals(0, key_prompts)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun saving_a_recovery_email_answers_a_key_demand_with_a_key() = runBlocking {
        server.enqueue(key_required)
        server.enqueue(ok("""{"success":true}"""))
        val response = save_recovery_email_with_reauth(
            recovery_email_api = RecoveryEmailApiImpl(build_client()),
            request = SaveRecoveryEmailRequest(
                encrypted_email = "ZW5j",
                email_nonce = "bm9uY2U",
                email_hash = "aGFzaA",
                plaintext_email = "backup@example.com",
                password_hash = "hash",
            ),
            key_assertion = key_assertion,
        )
        assertTrue(response.success)
        assertEquals(1, key_prompts)
        assert_password_only(server.takeRequest())
        val retried = server.takeRequest()
        assertEquals("PUT", retried.method)
        assert_key_proof(retried)
    }

    @Test
    fun saving_a_recovery_email_without_a_password_sends_one_request() = runBlocking {
        server.enqueue(ok("""{"success":true}"""))
        save_recovery_email_with_reauth(
            recovery_email_api = RecoveryEmailApiImpl(build_client()),
            request = SaveRecoveryEmailRequest(
                encrypted_email = "ZW5j",
                email_nonce = "bm9uY2U",
                email_hash = "aGFzaA",
                plaintext_email = "backup@example.com",
            ),
            key_assertion = key_assertion,
        )
        assertEquals(0, key_prompts)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun removing_a_recovery_email_answers_a_key_demand_with_a_key() = runBlocking {
        server.enqueue(key_required)
        server.enqueue(ok("""{"success":true}"""))
        remove_recovery_email_with_reauth(
            recovery_email_api = RecoveryEmailApiImpl(build_client()),
            password_hash = "hash",
            totp_code = null,
            key_assertion = key_assertion,
        )
        assertEquals(1, key_prompts)
        assert_password_only(server.takeRequest())
        val retried = server.takeRequest()
        assertEquals("DELETE", retried.method)
        assert_key_proof(retried)
    }
}
