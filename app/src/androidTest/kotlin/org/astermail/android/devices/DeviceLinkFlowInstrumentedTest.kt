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

package org.astermail.android.devices

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.devices.DeviceCodeApiImpl
import org.astermail.android.api.devices.DeviceLinkError
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceLinkFlowInstrumentedTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DeviceCodeApiImpl

    private val json = Json { ignoreUnknownKeys = true }

    private class NoTokens : TokenProvider {
        override suspend fun load(): BearerTokens? = null
        override suspend fun refresh(): BearerTokens? = null
        override suspend fun clear() {}
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        api = DeviceCodeApiImpl(
            ApiClient(
                base_url = server.url("/").toString().trimEnd('/'),
                token_provider = NoTokens(),
                allow_cleartext_for_test = true,
            ),
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private class FakeDevice {
        val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
        val x25519_sk = RatchetCrypto.random_bytes(32)
        val x25519_pk: ByteArray = X25519PrivateKeyParameters(x25519_sk, 0)
            .generatePublicKey()
            .encoded
        val ed25519_pk: ByteArray = RatchetCrypto.random_bytes(32)

        fun open(envelope_b64: String): ByteArray = DeviceEnvelope.open_secret_for_device(
            DeviceEnvelope.base64url_decode(envelope_b64),
            mlkem.secret_key,
            x25519_sk,
        )
    }

    private fun enqueue_pending(device: FakeDevice, machine_name: String, device_type: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"ed25519_pk":"${DeviceEnvelope.base64url_encode(device.ed25519_pk)}",
                     "mlkem_pk":"${DeviceEnvelope.base64url_encode(device.mlkem.public_key)}",
                     "x25519_pk":"${DeviceEnvelope.base64url_encode(device.x25519_pk)}",
                     "machine_name":"$machine_name","device_type":"$device_type"}
                    """.trimIndent(),
                ),
        )
    }

    private fun enqueue_error(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    @Test
    fun bridge_device_receives_a_passphrase_it_can_open() = runBlocking {
        val device = FakeDevice()
        val passphrase = "correct horse battery staple".toByteArray(Charsets.UTF_8)
        enqueue_pending(device, "adam-desktop", "bridge")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"device_id":"dev_123","machine_name":"adam-desktop"}"""),
        )

        val pending = api.verify_code("qeme-77et")
        assertEquals("adam-desktop", pending.machine_name)
        assertEquals("bridge", pending.device_type)

        val envelope = DeviceEnvelope.base64url_encode(
            DeviceEnvelope.seal_secret_for_device(
                passphrase,
                DeviceEnvelope.base64url_decode(pending.mlkem_pk),
                DeviceEnvelope.base64url_decode(pending.x25519_pk),
            ),
        )
        val confirmed = api.confirm_code("qeme-77et", envelope)
        assertEquals("dev_123", confirmed.device_id)

        val verify_request = server.takeRequest()
        assertEquals("/api/core/v1/auth/device/code/verify", verify_request.path)
        assertEquals(
            "QEME77ET",
            (json.parseToJsonElement(verify_request.body.readUtf8()) as JsonObject)["code"]
                .let { (it as JsonPrimitive).content },
        )

        val confirm_request = server.takeRequest()
        assertEquals("/api/core/v1/auth/device/code/confirm", confirm_request.path)
        val confirm_body = json.parseToJsonElement(confirm_request.body.readUtf8()) as JsonObject
        assertEquals("QEME77ET", (confirm_body["code"] as JsonPrimitive).content)
        val sent_envelope = (confirm_body["sealed_envelope"] as JsonPrimitive).content
        assertTrue(
            DeviceEnvelope.base64url_decode(sent_envelope).size >=
                DeviceEnvelope.MIN_ENVELOPE_BYTES,
        )
        assertArrayEquals(passphrase, device.open(sent_envelope))
    }

    @Test
    fun the_passphrase_never_travels_in_the_clear() = runBlocking {
        val device = FakeDevice()
        val passphrase = "hunter2-secret-passphrase".toByteArray(Charsets.UTF_8)
        enqueue_pending(device, "adam-laptop", "desktop")
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"device_id":"dev_9","machine_name":"adam-laptop"}"""),
        )

        val pending = api.verify_code("QEME-77ET")
        api.confirm_code(
            "QEME-77ET",
            DeviceEnvelope.base64url_encode(
                DeviceEnvelope.seal_secret_for_device(
                    passphrase,
                    DeviceEnvelope.base64url_decode(pending.mlkem_pk),
                    DeviceEnvelope.base64url_decode(pending.x25519_pk),
                ),
            ),
        )

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(!body.contains("hunter2"))
        assertTrue(!body.contains(String(passphrase, Charsets.UTF_8)))
    }

    @Test
    fun expired_code_surfaces_as_code_not_found() = runBlocking {
        enqueue_error(404, """{"error":"invalid or expired code","code":"NOT_FOUND"}""")
        val error = runCatching { api.verify_code("AAAA-BBBB") }.exceptionOrNull()
        assertEquals(DeviceLinkError.CodeNotFound, error)
    }

    @Test
    fun device_enrolled_elsewhere_surfaces_as_already_linked() = runBlocking {
        enqueue_error(
            409,
            """{"error":"device key already enrolled to another account","code":"CONFLICT"}""",
        )
        val error = runCatching { api.confirm_code("AAAA-BBBB", "envelope") }.exceptionOrNull()
        assertEquals(DeviceLinkError.AlreadyLinked, error)
    }

    @Test
    fun bridge_without_a_plan_surfaces_as_upgrade_required() = runBlocking {
        enqueue_error(403, """{"error":"plan_upgrade_required","required_tier":"star"}""")
        val error = runCatching { api.verify_code("AAAA-BBBB") }.exceptionOrNull()
        assertEquals(DeviceLinkError.PlanUpgradeRequired, error)
    }

    @Test
    fun redis_outage_surfaces_as_service_unavailable() = runBlocking {
        enqueue_error(503, """{"error":"service unavailable","code":"SERVICE_UNAVAILABLE"}""")
        val error = runCatching { api.verify_code("AAAA-BBBB") }.exceptionOrNull()
        assertEquals(DeviceLinkError.ServiceUnavailable, error)
    }

    @Test
    fun rate_limits_still_map_to_the_shared_api_error() = runBlocking {
        enqueue_error(429, """{"error":"too many requests","code":"RATE_LIMITED"}""")
        val error = runCatching { api.verify_code("AAAA-BBBB") }.exceptionOrNull()
        assertTrue(error is ApiError.RateLimited)
    }

    @Test
    fun short_codes_never_reach_the_network() = runBlocking {
        val error = runCatching { api.verify_code("QEME") }.exceptionOrNull()
        assertEquals(DeviceLinkError.CodeNotFound, error)
        assertEquals(0, server.requestCount)
    }
}
