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

package org.astermail.android.api.guard

import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_TOKEN_HEADER
import org.astermail.android.api.TokenProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GuardApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient
    private lateinit var api: GuardApi

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = ApiClient(
            base_url = server.url("/").toString().trimEnd('/'),
            token_provider = object : TokenProvider {
                override suspend fun load(): BearerTokens? = null
                override suspend fun refresh(): BearerTokens? = null
                override suspend fun clear() {}
            },
            allow_cleartext_for_test = true,
        )
        api = GuardApiImpl(client)
    }

    @After
    fun teardown() {
        client.close()
        server.shutdown()
    }

    private fun ok(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    private val action = SignedAction(nonce = "n1", signature = "s1")

    @Test
    fun status_decodes_full_fixture() = runBlocking {
        server.enqueue(
            ok(
                """{"switches":3,"hold_scopes":5,"version":7,"enrolled_at":"2026-11-14T10:00:00Z",
                "recovery_wait_effective":true,"paused_until":null,"has_rescue_code":true,
                "this_device":{"id":"d1","long_trusted":true,"approved_at":"2026-11-14T10:00:00Z"},
                "devices":[{"id":"d1","name":"Pixel 9","device_type":"mobile","approved_at":"2026-11-14T10:00:00Z","long_trusted":true}],
                "pending_changes":[{"id":"c1","kind":"forwarding_change","apply_after":"2026-11-15T10:00:00Z","created_at":"2026-11-14T10:00:00Z"}]}""",
            ),
        )
        val status = api.status()
        assertEquals(3, status.switches)
        assertEquals(5, status.hold_scopes)
        assertEquals(7L, status.version)
        assertTrue(status.recovery_wait_effective)
        assertTrue(status.has_rescue_code)
        assertEquals("d1", status.this_device?.id)
        assertEquals("Pixel 9", status.devices.single().name)
        assertEquals("forwarding_change", status.pending_changes.single().kind)
        assertEquals("/api/core/v1/guard", server.takeRequest().path)
    }

    @Test
    fun status_without_settings_decodes_defaults() = runBlocking {
        server.enqueue(ok("""{"switches":0,"hold_scopes":0,"version":0,"devices":[],"pending_changes":[]}"""))
        val status = api.status()
        assertEquals(0, status.switches)
        assertNull(status.this_device)
    }

    @Test
    fun sign_in_status_sends_guard_token_and_no_authorization() = runBlocking {
        server.enqueue(ok("""{"status":"pending","expires_at":"2026-11-14T10:05:00Z"}"""))
        val status = api.sign_in_status("t1")
        assertEquals("pending", status.status)
        val req = server.takeRequest()
        assertEquals("/api/core/v1/guard/sign-in/status", req.path)
        assertEquals("t1", req.getHeader(GUARD_TOKEN_HEADER))
        assertNull(req.getHeader("Authorization"))
    }

    @Test
    fun approve_posts_number_and_action() = runBlocking {
        server.enqueue(ok("{}"))
        api.approve("r1", 42, null, action)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/core/v1/guard/requests/r1/approve", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(42, body["number"]!!.jsonPrimitive.int)
        assertEquals("n1", body["action"]!!.jsonObject["nonce"]!!.jsonPrimitive.content)
    }

    @Test
    fun sign_in_complete_sets_csrf_and_reads_guard() = runBlocking {
        server.enqueue(
            ok(
                """{"user_id":"u1","username":"alex","email":"alex@example.org","csrf_token":"csrf-9",
                "encrypted_vault":"v","vault_nonce":"vn",
                "guard":{"device_id":"d2","code_secret_sealed":"sealed","approval_envelope":null}}""",
            ).addHeader("Set-Cookie", "aster_refresh=rf1; Path=/; HttpOnly"),
        )
        val done = api.sign_in_complete("t1")
        assertEquals("csrf-9", client.get_csrf())
        assertEquals("u1", done.login.user_id)
        assertEquals("rf1", done.login.refresh_token)
        assertEquals("d2", done.guard?.device_id)
        assertEquals("sealed", done.guard?.code_secret_sealed)
        assertEquals("t1", server.takeRequest().getHeader(GUARD_TOKEN_HEADER))
    }

    @Test
    fun sign_in_complete_without_guard_field() = runBlocking {
        server.enqueue(
            ok(
                """{"user_id":"u1","username":"alex","email":"alex@example.org","csrf_token":"c",
                "encrypted_vault":"v","vault_nonce":"vn"}""",
            ),
        )
        assertNull(api.sign_in_complete("t1").guard)
    }

    @Test
    fun password_proof_encodes_with_type_tag() {
        val proof: StepUpProof = StepUpProof.Password(password_hash = "x", totp_code = null)
        val encoded = Json.encodeToString(StepUpProof.serializer(), proof)
        assertEquals("""{"type":"password","password_hash":"x","totp_code":null}""", encoded)
    }

    @Test
    fun sign_in_begin_sends_envelope_versions_and_mobile_type() = runBlocking {
        server.enqueue(ok("""{"status":"pending","expires_at":"2026-11-14T10:05:00Z"}"""))
        api.sign_in_begin(
            "t1",
            GuardSignInBegin(
                device = GuardDeviceKeys("ed", "mk", "xk", "ak", name = "Pixel 9"),
                number_hash = "nh",
            ),
        )
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(listOf(2), body["envelope_versions"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals("mobile", body["device"]!!.jsonObject["device_type"]!!.jsonPrimitive.content)
        assertEquals("nh", body["number_hash"]!!.jsonPrimitive.content)
    }

    @Test
    fun veto_with_token_posts_id_and_t() = runBlocking {
        server.enqueue(ok("""{"status":"vetoed"}"""))
        assertEquals("vetoed", api.veto_with_token("c1", "tok").status)
        val req = server.takeRequest()
        assertEquals("/api/core/v1/guard/veto", req.path)
        assertEquals("""{"id":"c1","t":"tok"}""", req.body.readUtf8())
    }

    @Test
    fun join_request_item_decodes() = runBlocking {
        server.enqueue(
            ok(
                """{"requests":[{"id":"r2","kind":"join","created_at":"a","expires_at":"b","context":"ctx",
                "new_device":{"device_type":"mobile","name":"Pixel 9","ed25519_pk":"e","mlkem_pk":"m",
                "x25519_pk":"x","action_pk":"p","keys_digest":"k"},"envelope_versions":[2]}]}""",
            ),
        )
        val item = api.requests().requests.single()
        assertEquals("join", item.kind)
        assertEquals("p", item.new_device.action_pk)
        assertEquals("k", item.new_device.keys_digest)
        assertEquals(listOf(2), item.envelope_versions)
    }

    @Test
    fun rescue_status_uses_query_id() = runBlocking {
        server.enqueue(ok("""{"status":"pending","apply_after":"x"}"""))
        api.rescue_status("abc-1")
        assertEquals("/api/core/v1/guard/sign-in/rescue/status?id=abc-1", server.takeRequest().path)
    }

    @Test
    fun too_many_requests_maps_to_retry_with_header() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "4")
                .setBody("""{"error":"slow down","code":"GUARD_TOO_MANY_REQUESTS"}"""),
        )
        try {
            api.sign_in_status("t1")
            fail("expected GuardRetry")
        } catch (e: ApiError.GuardRetry) {
            assertEquals(4L, e.retry_after_secs)
        }
    }

    @Test
    fun cancel_and_skip_read_the_server_result() = runBlocking {
        server.enqueue(ok("""{"cancelled":false}"""))
        server.enqueue(ok("""{"status":"failed_stale"}"""))
        assertFalse(api.cancel_change("c1", action).cancelled)
        assertTrue(api.skip_change("c1", action).stale)
        assertEquals("/api/core/v1/guard/changes/c1/cancel", server.takeRequest().path)
        assertEquals("/api/core/v1/guard/changes/c1/skip", server.takeRequest().path)
    }

    @Test
    fun pending_change_reads_server_flags() = runBlocking {
        server.enqueue(
            ok(
                """{"changes":[{"id":"c1","kind":"rescue_code_rotation","apply_after":"x","created_at":"y",
                "skippable":false,"cancellable":true,"subject_key":"k","created_by_device":null}]}""",
            ),
        )
        val change = api.changes().changes.single()
        assertEquals("rescue_code_rotation", change.kind)
        assertEquals(false, change.skippable)
        assertEquals(true, change.cancellable)
    }

    @Test
    fun unsafe_id_is_rejected_without_a_request() = runBlocking {
        try {
            api.deny("../x", action)
            fail("expected NotFoundError")
        } catch (_: ApiError.NotFoundError) {
        }
        assertEquals(0, server.requestCount)
    }
}
