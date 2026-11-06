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

package org.astermail.android.auth

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.security.PasskeyCredentialDescriptor
import org.astermail.android.api.security.PasskeyRegistrationOptions
import org.astermail.android.api.security.PasskeyRegistrationParam
import org.astermail.android.api.security.PasskeyRegistrationRp
import org.astermail.android.api.security.PasskeyRegistrationUser
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyWireFormatTest {

    private val user_uuid = UUID.fromString("5f0c2d3e-8a41-4b7c-9e22-1d3f4a5b6c7d")

    private fun uuid_bytes(uuid: UUID): ByteArray =
        ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()

    private val registration_options = PasskeyRegistrationOptions(
        challenge = "Y2hhbGxlbmdlLWJ5dGVz",
        challenge_token = "token-1",
        rp = PasskeyRegistrationRp(name = "Aster Mail", id = "app.astermail.org"),
        user = PasskeyRegistrationUser(
            id = Base64.getEncoder().encodeToString(uuid_bytes(user_uuid)),
            name = "brightpine2026",
            displayName = "brightpine2026",
        ),
        pubKeyCredParams = listOf(PasskeyRegistrationParam(alg = -7), PasskeyRegistrationParam(alg = -257)),
    )

    @Test
    fun prf_eval_input_matches_web_client() {
        assertEquals("t79oGc5t6_VspxiRuQewZD8lwyThqeGpQRv0V6wcPvU", prf_eval_input())
    }

    @Test
    fun decrypts_passphrase_sealed_by_web_client() {
        val prf_output = ByteArray(32) { (it + 1).toByte() }
        val plain = decrypt_passphrase_with_prf(
            prf_output,
            "9PRytl50HjsZhcqIme/ZlG3gB9RbcgC8PKBdXsIFKYZP",
            "oKGio6Slpqeoqaqr",
        )
        assertEquals("vector passphrase", plain?.toString(Charsets.UTF_8))
    }

    @Test
    fun prf_seal_round_trips_and_rejects_wrong_key() {
        val prf_output = ByteArray(32) { 7 }
        val (sealed, nonce) = encrypt_passphrase_with_prf(prf_output, "round trip".toByteArray())
        assertEquals(12, Base64.getDecoder().decode(nonce).size)
        assertEquals("round trip", decrypt_passphrase_with_prf(prf_output, sealed, nonce)?.toString(Charsets.UTF_8))
        assertNull(decrypt_passphrase_with_prf(ByteArray(32) { 8 }, sealed, nonce))
    }

    @Test
    fun registration_request_asks_for_discoverable_passkey_from_any_provider_with_prf() {
        val json = JSONObject(registration_request_json(registration_options))
        assertEquals("app.astermail.org", json.getJSONObject("rp").getString("id"))
        val selection = json.getJSONObject("authenticatorSelection")
        assertFalse(selection.has("authenticatorAttachment"))
        assertEquals("required", selection.getString("residentKey"))
        assertEquals("required", selection.getString("userVerification"))
        assertEquals(
            prf_eval_input(),
            json.getJSONObject("extensions").getJSONObject("prf").getJSONObject("eval").getString("first"),
        )
        assertEquals(2, json.getJSONArray("pubKeyCredParams").length())
    }

    @Test
    fun user_handle_round_trips_to_backend_user_id() {
        val json = JSONObject(registration_request_json(registration_options))
        val handle = json.getJSONObject("user").getString("id")
        val decoded_text = String(Base64.getUrlDecoder().decode(handle), Charsets.UTF_8)
        val decoded_uuid = ByteBuffer.wrap(Base64.getDecoder().decode(decoded_text)).let { UUID(it.long, it.long) }
        assertEquals(user_uuid, decoded_uuid)
    }

    @Test
    fun registration_response_is_converted_to_standard_base64() {
        val attestation = byteArrayOf(-5, -1, -2, 0, 1, 2, 3)
        val client_data = "{\"type\":\"webauthn.create\"}".toByteArray()
        val prf = ByteArray(32) { 3 }
        val url = Base64.getUrlEncoder().withoutPadding()
        val response = JSONObject()
            .put("id", "Y3JlZC1pZA")
            .put("rawId", "Y3JlZC1pZA")
            .put("type", "public-key")
            .put(
                "response",
                JSONObject()
                    .put("attestationObject", url.encodeToString(attestation))
                    .put("clientDataJSON", url.encodeToString(client_data)),
            )
            .put(
                "clientExtensionResults",
                JSONObject().put(
                    "prf",
                    JSONObject().put("enabled", true).put("results", JSONObject().put("first", url.encodeToString(prf))),
                ),
            )
            .toString()
        val registration = registration_complete_request(response, registration_options, "Passkey (Pixel)")
        assertArrayEquals(attestation, Base64.getDecoder().decode(registration.request.response.attestation_object))
        assertArrayEquals(client_data, Base64.getDecoder().decode(registration.request.response.client_data_json))
        assertEquals("token-1", registration.request.challenge_token)
        assertTrue(registration.request.is_passkey)
        assertTrue(registration.prf_enabled)
        assertArrayEquals(prf, registration.prf_output)
    }

    @Test
    fun registration_request_excludes_nothing_when_backend_sends_no_credentials() {
        val json = JSONObject(registration_request_json(registration_options))
        assertEquals(0, json.getJSONArray("excludeCredentials").length())
    }

    @Test
    fun registration_request_excludes_credentials_the_backend_already_has() {
        val options = registration_options.copy(
            excludeCredentials = listOf(
                PasskeyCredentialDescriptor(id = "Y3JlZC1vbmU"),
                PasskeyCredentialDescriptor(id = ""),
            ),
        )
        val excluded = JSONObject(registration_request_json(options)).getJSONArray("excludeCredentials")
        assertEquals(1, excluded.length())
        assertEquals("Y3JlZC1vbmU", excluded.getJSONObject(0).getString("id"))
        assertEquals("public-key", excluded.getJSONObject(0).getString("type"))
    }

    @Test
    fun expired_challenge_is_detected_only_from_the_server_message() {
        assertTrue(is_passkey_challenge_expired(ApiError.ValidationError(listOf("Challenge expired"))))
        assertFalse(is_passkey_challenge_expired(ApiError.ValidationError(listOf("Validation failed"))))
        assertFalse(is_passkey_challenge_expired(IllegalStateException("Challenge expired")))
    }

    @Test
    fun compat_registration_request_drops_prf_and_keeps_user_verification() {
        val json = JSONObject(registration_request_json(registration_options, compat = true))
        val selection = json.getJSONObject("authenticatorSelection")
        assertFalse(selection.has("authenticatorAttachment"))
        assertEquals("required", selection.getString("residentKey"))
        assertEquals("required", selection.getString("userVerification"))
        assertFalse(json.has("extensions"))
        assertEquals("app.astermail.org", json.getJSONObject("rp").getString("id"))
    }

    @Test
    fun padded_registration_id_is_stored_without_padding() {
        val url = Base64.getUrlEncoder()
        val response = JSONObject()
            .put("id", "Y3JlZC1pZA==")
            .put("rawId", "Y3JlZC1pZA==")
            .put(
                "response",
                JSONObject()
                    .put("attestationObject", url.encodeToString(byteArrayOf(1, 2, 3, 4)))
                    .put("clientDataJSON", url.encodeToString("{}".toByteArray())),
            )
            .toString()
        val registration = registration_complete_request(response, registration_options, "Passkey")
        assertEquals("Y3JlZC1pZA", registration.request.raw_id)
        assertEquals("Y3JlZC1pZA", registration.credential_id)
        assertFalse(registration.prf_enabled)
        assertNull(registration.prf_output)
    }

    private val assertion_options = WebAuthnAssertionOptions(
        challenge = "Y2hhbGxlbmdl",
        challenge_token = "assert-token",
        rpId = "app.astermail.org",
    )

    private val totp_challenge = TotpChallenge(
        pending_login_token = "pending",
        available_methods = listOf("webauthn"),
        password_hash_bytes = ByteArray(0),
        password_bytes = ByteArray(0),
        salt_bytes = ByteArray(0),
        email = "brightpine2026@astermail.org",
        remember_me = true,
    )

    private fun assertion_response(encoder: Base64.Encoder, id: ByteArray): String = JSONObject()
        .put("id", encoder.encodeToString(id))
        .put("rawId", encoder.encodeToString(id))
        .put(
            "response",
            JSONObject()
                .put("authenticatorData", encoder.encodeToString(byteArrayOf(-1, -2, 0, 5)))
                .put("clientDataJSON", encoder.encodeToString("{\"type\":\"webauthn.get\"}".toByteArray()))
                .put("signature", encoder.encodeToString(byteArrayOf(-3, 62, 63)))
                .put("userHandle", encoder.encodeToString("user".toByteArray())),
        )
        .toString()

    @Test
    fun assertion_fields_are_sent_as_unpadded_base64url_whatever_the_provider_returns() {
        val id = byteArrayOf(-5, -1, 0, 1, 2)
        val expected = assertion_verify_request(
            assertion_response(Base64.getUrlEncoder().withoutPadding(), id),
            assertion_options, totp_challenge, trust_device = false, device_label = null,
        )
        for (encoder in listOf(Base64.getUrlEncoder(), Base64.getEncoder(), Base64.getEncoder().withoutPadding())) {
            val request = assertion_verify_request(
                assertion_response(encoder, id),
                assertion_options, totp_challenge, trust_device = false, device_label = null,
            )
            assertEquals(expected, request)
            listOf(request.id, request.raw_id, request.response.authenticator_data, request.response.client_data_json, request.response.signature)
                .forEach { assertTrue(it, it.none { c -> c == '=' || c == '+' || c == '/' }) }
        }
        assertEquals("assert-token", expected.challenge_token)
        assertEquals("pending", expected.pending_login_token)
    }

    @Test(expected = PasskeyFailedException::class)
    fun assertion_with_garbage_signature_fails_cleanly() {
        val response = JSONObject(assertion_response(Base64.getUrlEncoder().withoutPadding(), byteArrayOf(1, 2, 3)))
        response.getJSONObject("response").put("signature", "not*base64")
        assertion_verify_request(response.toString(), assertion_options, totp_challenge, false, null)
    }

    @Test
    fun missing_passkey_is_not_reported_as_missing_provider() {
        val mapped = map_get_credential_failure(NoCredentialException("none"))
        assertTrue(mapped is PasskeyUnavailableException)
        assertFalse((mapped as PasskeyUnavailableException).no_provider)
        assertEquals("NO_CREDENTIAL", passkey_error_code(mapped))
    }

    @Test
    fun provider_configuration_failures_are_reported_as_missing_provider() {
        val get = map_get_credential_failure(GetCredentialProviderConfigurationException("none"))
        assertTrue((get as PasskeyUnavailableException).no_provider)
        val create = map_create_credential_failure(CreateCredentialNoCreateOptionException("none"))
        assertTrue((create as PasskeyUnavailableException).no_provider)
        assertNotNull(passkey_error_code(get))
        assertNotNull(passkey_error_code(create))
    }

    @Test
    fun cancelled_sign_in_stays_silent() {
        assertTrue(map_get_credential_failure(GetCredentialCancellationException("x")) is PasskeyCancelledException)
    }

    @Test
    fun unknown_create_failure_carries_a_readable_code() {
        val mapped = map_create_credential_failure(CreateCredentialUnknownException("boom"))
        assertTrue(mapped is PasskeyFailedException)
        val code = passkey_error_code(mapped)
        assertNotNull(code)
        assertFalse(code!!.contains('.'))
        assertFalse(code.contains('/'))
        assertFalse(code.startsWith("TYPE_"))
    }

    @Test
    fun error_code_is_absent_without_an_underlying_failure() {
        assertNull(passkey_error_code(PasskeyFailedException()))
        assertNull(passkey_error_code(null))
    }
}
