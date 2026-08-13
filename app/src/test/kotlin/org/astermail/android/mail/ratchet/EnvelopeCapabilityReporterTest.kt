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

package org.astermail.android.mail.ratchet

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ratchet.EnvelopeCapabilityResponse
import org.astermail.android.api.ratchet.PostStateOutcome
import org.astermail.android.api.ratchet.PqSecretResponse
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.PutStateOutcome
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.RatchetStateResponse
import org.astermail.android.api.ratchet.ReportEnvelopeCapabilityRequest
import org.astermail.android.api.ratchet.UploadPrekeyBundleRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore : EnvelopeCapabilityStore {
    var client_id: String? = null
    val last_reported = mutableMapOf<String, EnvelopeCapabilityReport>()

    override fun get_client_id(): String? = client_id

    override fun put_client_id(client_id: String) {
        this.client_id = client_id
    }

    override fun get_last_report(user_id: String): EnvelopeCapabilityReport? = last_reported[user_id]

    override fun put_last_report(user_id: String, report: EnvelopeCapabilityReport) {
        last_reported[user_id] = report
    }

    fun last_reported_at_ms(user_id: String): Long = last_reported[user_id]?.at_ms ?: 0L
}

private class FakeRatchetApi(
    private val response: EnvelopeCapabilityResponse? = EnvelopeCapabilityResponse(
        success = true,
        min_supported_marker = 4,
        pq_hybrid_enabled = true,
        identity_verified = true,
    ),
    private val thrown: Throwable? = null,
) : RatchetApi {
    val requests = mutableListOf<ReportEnvelopeCapabilityRequest>()

    override suspend fun report_envelope_capability(
        request: ReportEnvelopeCapabilityRequest,
    ): EnvelopeCapabilityResponse? {
        requests.add(request)
        thrown?.let { throw it }
        return response
    }

    override suspend fun fetch_state(conversation_id_b64: String): RatchetStateResponse? = null
    override suspend fun post_state(
        conversation_id_b64: String,
        encrypted_state: String,
        state_nonce: String,
    ): PostStateOutcome = PostStateOutcome.Failure(500)

    override suspend fun put_state(
        conversation_id_b64: String,
        encrypted_state: String,
        state_nonce: String,
        expected_version: Int,
    ): PutStateOutcome = PutStateOutcome.Failure(500)

    override suspend fun fetch_pq_secret(key_id: Int): PqSecretResponse? = null
    override suspend fun fetch_prekey_bundle(username: String, email: String): PrekeyBundleResponse? = null
    override suspend fun delete_state(conversation_id_b64: String): Boolean = false
    override suspend fun upload_prekey_bundle(request: UploadPrekeyBundleRequest): Boolean = false
}

class EnvelopeCapabilityReporterTest {

    private val user_id = "11111111-2222-3333-4444-555555555555"

    private fun identity_point(fill: Byte): String =
        Base64.getEncoder().encodeToString(ByteArray(65) { if (it == 0) 0x04 else fill })

    private fun expected_fingerprint(point_b64: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(point_b64)),
        )

    private val identity = identity_point(0x11)

    private fun reporter(
        store: EnvelopeCapabilityStore,
        api: RatchetApi,
        now: () -> Long,
        client_id: String = "fixed-client-id",
    ) = EnvelopeCapabilityReporter(
        store = store,
        ratchet_api = api,
        new_client_id = { client_id },
        now_ms = now,
    )

    @Test
    fun reports_marker_four_because_android_decapsulates_ml_kem() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        val response = reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity)

        assertEquals(1, api.requests.size)
        assertEquals(4, api.requests[0].max_envelope_marker)
        assertEquals("android", api.requests[0].platform)
        assertEquals("fixed-client-id", api.requests[0].client_id)
        assertEquals(true, response?.pq_hybrid_enabled)
    }

    @Test
    fun persists_and_reuses_one_client_id_across_reports() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        reporter(store, api, now = { 1_000L }, client_id = "first").report_if_due(user_id, identity)
        reporter(store, api, now = { 1_000L }, client_id = "second").report_if_due(user_id, identity, force = true)

        assertEquals("first", store.client_id)
        assertEquals(listOf("first", "first"), api.requests.map { it.client_id })
    }

    @Test
    fun skips_a_second_report_inside_the_interval() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity)
        val second = reporter(store, api, now = { 1_000L + 60_000L }).report_if_due(user_id, identity)

        assertNull(second)
        assertEquals(1, api.requests.size)
    }

    @Test
    fun reports_again_once_the_interval_elapses() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()
        val start = 1_000L

        reporter(store, api, now = { start }).report_if_due(user_id, identity)
        reporter(store, api, now = { start + EnvelopeCapabilityReporter.REPORT_INTERVAL_MS })
            .report_if_due(user_id, identity)

        assertEquals(2, api.requests.size)
        assertEquals(
            start + EnvelopeCapabilityReporter.REPORT_INTERVAL_MS,
            store.last_reported_at_ms(user_id),
        )
    }

    @Test
    fun re_reports_after_the_server_ttl_is_shorter_than_the_client_interval() {
        assertTrue(
            "clients must re-report well inside the server's 90 day capability ttl",
            EnvelopeCapabilityReporter.REPORT_INTERVAL_MS < 90L * 24 * 60 * 60 * 1000,
        )
    }

    @Test
    fun a_failed_report_is_retried_on_the_next_bootstrap() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi(response = null)

        assertNull(reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity))
        assertEquals(0L, store.last_reported_at_ms(user_id))

        reporter(store, api, now = { 2_000L }).report_if_due(user_id, identity)
        assertEquals(2, api.requests.size)
    }

    @Test
    fun an_unsuccessful_response_does_not_count_as_reported() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi(
            response = EnvelopeCapabilityResponse(success = false, min_supported_marker = null),
        )

        reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity)

        assertEquals(0L, store.last_reported_at_ms(user_id))
    }

    @Test
    fun a_throwing_api_does_not_crash_the_caller() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi(thrown = IllegalStateException("offline"))

        assertNull(reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity))
        assertEquals(0L, store.last_reported_at_ms(user_id))
    }

    @Test
    fun a_blank_user_id_is_never_reported() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        assertNull(reporter(store, api, now = { 1_000L }).report_if_due("  ", identity))

        assertEquals(0, api.requests.size)
        assertNull(store.client_id)
    }

    @Test
    fun reports_the_fingerprint_of_the_identity_key_it_holds() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity)

        assertEquals(expected_fingerprint(identity), api.requests[0].identity_fingerprint)
    }

    @Test
    fun the_fingerprint_matches_the_shared_cross_client_vector() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity)

        assertEquals(
            "8LkxWgRZ2rerX6aQPnM8kXdhFUIWzZDl2XnabIUsYCo=",
            api.requests[0].identity_fingerprint,
        )
    }

    @Test
    fun a_missing_identity_key_reports_no_fingerprint() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        reporter(store, api, now = { 1_000L }).report_if_due(user_id, null)

        assertNull(api.requests[0].identity_fingerprint)
    }

    @Test
    fun a_malformed_identity_key_reports_no_fingerprint() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        reporter(store, api, now = { 1_000L }).report_if_due(user_id, "not-a-point")

        assertNull(api.requests[0].identity_fingerprint)
    }

    @Test
    fun a_rotated_identity_key_reports_immediately() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()
        val rotated = identity_point(0x22)

        reporter(store, api, now = { 1_000L }).report_if_due(user_id, identity)
        reporter(store, api, now = { 2_000L }).report_if_due(user_id, rotated)

        assertEquals(2, api.requests.size)
        assertEquals(expected_fingerprint(rotated), api.requests[1].identity_fingerprint)
    }

    @Test
    fun a_clock_that_moved_backwards_reports_instead_of_going_silent() = runTest {
        val store = FakeStore()
        val api = FakeRatchetApi()

        reporter(store, api, now = { 10_000_000L }).report_if_due(user_id, identity)
        reporter(store, api, now = { 5_000L }).report_if_due(user_id, identity)

        assertEquals(2, api.requests.size)
    }
}
