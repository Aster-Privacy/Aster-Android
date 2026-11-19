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

package org.astermail.android.api

import io.ktor.client.plugins.auth.providers.BearerTokens
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.astermail.android.api.recovery.RecoveryApiImpl
import org.astermail.android.api.recovery.VerifyCodesStepUpRequest
import org.astermail.android.api.recovery_email.RecoveryEmailApiImpl
import org.astermail.android.api.recovery_email.RemoveRecoveryEmailRequest
import org.astermail.android.api.recovery_email.SaveRecoveryEmailRequest
import org.astermail.android.api.security.ReauthProofRequest
import org.astermail.android.api.security.SecurityApiImpl
import org.astermail.android.api.totp.TotpApiImpl
import org.astermail.android.api.totp.TotpRegenerateBackupCodesRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ReauthRequestContractTest {
    private lateinit var server: MockWebServer
    private val recorded = mutableMapOf<String, RecordedRequest>()
    private val proof_json = """{"password_hash":"cGFzc3dvcmQtaGFzaA","totp_code":"123456"}"""
    private val json = Json { ignoreUnknownKeys = true }
    private val test_origin = "https://app.example.test"
    private val key_assertion_json =
        """{"raw_id":"a2V5LTE","challenge_token":"token-1",""" +
            """"response":{"authenticator_data":"YXV0aA","client_data_json":"Y2xpZW50","signature":"c2ln"}}"""
    private val key_proof_json =
        """{"password_hash":"cGFzc3dvcmQtaGFzaA","hardware_key_assertion":$key_assertion_json}"""

    @Before
    fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded[request.path.orEmpty().substringBefore('?')] = request
                return MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error":"stop","code":"STOP"}""")
            }
        }
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

    private fun argument_for(type: java.lang.reflect.Type): Any? =
        if (type == String::class.java) "key-1" else json.decodeFromString(serializer(type), proof_json)

    private suspend fun call_by_name(target: Any, name: String) {
        val method: Method = target.javaClass.methods.first { it.name == name }
        val args = method.genericParameterTypes
            .filter { it.typeName.startsWith(Continuation::class.java.name).not() }
            .map { argument_for(it) }
        runCatching {
            suspendCoroutineUninterceptedOrReturn<Any?> { cont ->
                try {
                    method.invoke(target, *(args + cont).toTypedArray())
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException
                }
            }
        }
    }

    private fun body_of(path: String): JSONObject {
        val request = recorded[path]
        assertNotNull("no request was sent to $path", request)
        val body = request!!.body.readUtf8()
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    @Test
    fun adding_a_passkey_sends_the_password_proof() = runBlocking {
        call_by_name(SecurityApiImpl(build_client()), "initiate_passkey_registration")
        val body = body_of("/api/core/v1/auth/hardware-keys/register/initiate")
        assertEquals("cGFzc3dvcmQtaGFzaA", body.optString("password_hash"))
        assertEquals("123456", body.optString("totp_code"))
    }

    @Test
    fun starting_authenticator_setup_sends_the_password_proof() = runBlocking {
        call_by_name(TotpApiImpl(build_client()), "initiate_setup")
        val body = body_of("/api/core/v1/auth/totp/setup/initiate")
        assertEquals("cGFzc3dvcmQtaGFzaA", body.optString("password_hash"))
    }

    @Test
    fun regenerating_backup_codes_sends_the_password() = runBlocking {
        val request = json.decodeFromString(
            TotpRegenerateBackupCodesRequest.serializer(),
            """{"code":"123456","password_hash":"cGFzc3dvcmQtaGFzaA"}""",
        )
        runCatching { TotpApiImpl(build_client()).regenerate_backup_codes(request) }
        val body = body_of("/api/core/v1/auth/totp/backup-codes/regenerate")
        assertEquals("123456", body.optString("code"))
        assertEquals("cGFzc3dvcmQtaGFzaA", body.optString("password_hash"))
    }

    private fun sent_to(path: String): RecordedRequest {
        val request = recorded[path]
        assertNotNull("no request was sent to $path", request)
        return request!!
    }

    private fun assert_key_assertion(body: JSONObject) {
        val assertion = body.optJSONObject("hardware_key_assertion")
        assertNotNull("hardware_key_assertion is missing from $body", assertion)
        assertEquals("a2V5LTE", assertion!!.getString("raw_id"))
        assertEquals("token-1", assertion.getString("challenge_token"))
        assertEquals("c2ln", assertion.getJSONObject("response").getString("signature"))
    }

    @Test
    fun removing_a_security_key_sends_the_key_proof_with_the_origin() = runBlocking {
        val proof = json.decodeFromString(ReauthProofRequest.serializer(), key_proof_json)
        runCatching { SecurityApiImpl(build_client()).delete_hardware_key("key-1", proof) }
        val request = sent_to("/api/core/v1/auth/hardware-keys/key-1")
        assertEquals("DELETE", request.method)
        assertEquals(test_origin, request.getHeader("Origin"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("cGFzc3dvcmQtaGFzaA", body.getString("password_hash"))
        assert_key_assertion(body)
    }

    @Test
    fun security_key_challenge_request_carries_the_origin() = runBlocking {
        runCatching { SecurityApiImpl(build_client()).step_up_assertion_options() }
        val request = sent_to("/api/core/v1/auth/hardware-keys/step-up/options")
        assertEquals("POST", request.method)
        assertEquals(test_origin, request.getHeader("Origin"))
    }

    @Test
    fun recovery_codes_check_sends_the_key_proof_with_the_origin() = runBlocking {
        val request = json.decodeFromString(VerifyCodesStepUpRequest.serializer(), key_proof_json)
        runCatching { RecoveryApiImpl(build_client()).verify_step_up(request) }
        val sent = sent_to("/api/core/v1/recovery/codes/verify-step-up")
        assertEquals(test_origin, sent.getHeader("Origin"))
        val body = JSONObject(sent.body.readUtf8())
        assertEquals("cGFzc3dvcmQtaGFzaA", body.getString("password_hash"))
        assert_key_assertion(body)
    }

    @Test
    fun saving_a_recovery_email_sends_the_key_proof_with_the_origin() = runBlocking {
        val request = json.decodeFromString(
            SaveRecoveryEmailRequest.serializer(),
            """{"encrypted_email":"ZW5j","email_nonce":"bm9uY2U","email_hash":"aGFzaA",""" +
                """"plaintext_email":"backup@example.com","password_hash":"cGFzc3dvcmQtaGFzaA",""" +
                """"hardware_key_assertion":$key_assertion_json}""",
        )
        runCatching { RecoveryEmailApiImpl(build_client()).save(request) }
        val sent = sent_to("/api/core/v1/recovery/email")
        assertEquals("PUT", sent.method)
        assertEquals(test_origin, sent.getHeader("Origin"))
        assert_key_assertion(JSONObject(sent.body.readUtf8()))
    }

    @Test
    fun removing_a_recovery_email_sends_the_key_proof_with_the_origin() = runBlocking {
        val request = json.decodeFromString(RemoveRecoveryEmailRequest.serializer(), key_proof_json)
        runCatching { RecoveryEmailApiImpl(build_client()).remove(request) }
        val sent = sent_to("/api/core/v1/recovery/email")
        assertEquals("DELETE", sent.method)
        assertEquals(test_origin, sent.getHeader("Origin"))
        assert_key_assertion(JSONObject(sent.body.readUtf8()))
    }
}
