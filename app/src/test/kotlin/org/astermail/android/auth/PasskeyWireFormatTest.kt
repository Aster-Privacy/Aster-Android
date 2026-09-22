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
import org.astermail.android.api.auth.PasskeyLoginOptions
import org.astermail.android.api.security.PasskeyRegistrationOptions
import org.astermail.android.api.security.PasskeyRegistrationParam
import org.astermail.android.api.security.PasskeyRegistrationRp
import org.astermail.android.api.security.PasskeyRegistrationUser
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun registration_request_asks_for_discoverable_platform_passkey_with_prf() {
        val json = JSONObject(registration_request_json(registration_options))
        assertEquals("app.astermail.org", json.getJSONObject("rp").getString("id"))
        val selection = json.getJSONObject("authenticatorSelection")
        assertEquals("platform", selection.getString("authenticatorAttachment"))
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
        val options = PasskeyLoginOptions(challenge = "c2lnbg", challenge_token = "token-2", rpId = "app.astermail.org")
        val assertion = JSONObject()
            .put("id", "Y3JlZC1pZA")
            .put("rawId", "Y3JlZC1pZA")
            .put("type", "public-key")
            .put(
                "response",
                JSONObject()
                    .put("authenticatorData", "YXV0aA")
                    .put("clientDataJSON", "Y2xpZW50")
                    .put("signature", "c2ln")
                    .put("userHandle", handle),
            )
            .toString()
        val request = passkey_login_verify_request(assertion, options, "Pixel")
        val sent_handle = request.response.user_handle!!
        val decoded_text = String(Base64.getUrlDecoder().decode(sent_handle), Charsets.UTF_8)
        val decoded_uuid = ByteBuffer.wrap(Base64.getDecoder().decode(decoded_text)).let { UUID(it.long, it.long) }
        assertEquals(user_uuid, decoded_uuid)
        assertEquals("token-2", request.challenge_token)
        assertEquals("Y3JlZC1pZA", request.raw_id)
        assertTrue(request.remember_me)
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
}
