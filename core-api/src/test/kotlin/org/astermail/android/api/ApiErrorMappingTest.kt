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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.ktor.client.plugins.auth.providers.BearerTokens
import org.junit.Test

class ApiErrorMappingTest {

    private val client = ApiClient(
        base_url = "http://127.0.0.1:1",
        token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? = null
            override suspend fun refresh(): BearerTokens? = null
            override suspend fun clear() {}
        },
        allow_cleartext_for_test = true,
    )

    @Test
    fun guard_required_403_maps_with_token() {
        val body = """{"code":"GUARD_REQUIRED","details":{"request_id":"r1","guard_token":"t1","expires_at":"2026-11-14T10:00:00Z","methods":["approve","code","rescue"]}}"""
        val err = client.map_http_status(403, body) as ApiError.GuardRequired
        assertEquals("r1", err.request_id)
        assertEquals("t1", err.guard_token)
        assertEquals("2026-11-14T10:00:00Z", err.expires_at)
        assertEquals(listOf("approve", "code", "rescue"), err.methods)
    }

    @Test
    fun guard_required_403_carries_the_account_id() {
        val id = "0b9f1c2e-6a8d-4c35-9e21-4f7b8d2a1c90"
        val body = """{"code":"GUARD_REQUIRED","details":{"request_id":"r1","guard_token":"t1","expires_at":"2026-11-14T10:00:00Z","methods":[],"user_id":"$id"}}"""
        assertEquals(id, (client.map_http_status(403, body) as ApiError.GuardRequired).user_id)
        val bad = """{"code":"GUARD_REQUIRED","details":{"request_id":"r1","guard_token":"t1","user_id":"nope"}}"""
        assertEquals(null, (client.map_http_status(403, bad) as ApiError.GuardRequired).user_id)
    }

    @Test
    fun guard_held_409_maps_to_guard_held() {
        val err = client.map_http_status(409, """{"code":"GUARD_HELD","details":{"id":"c1","kind":"forwarding_change","apply_after":"2026-11-14T11:00:00Z"}}""")
        assertEquals(ApiError.GuardHeld("c1", "forwarding_change", "2026-11-14T11:00:00Z"), err)
    }

    @Test
    fun guard_device_pending_403_maps() {
        val err = client.map_http_status(403, """{"code":"GUARD_DEVICE_PENDING","details":{"device_id":"d1","activates_at":"2026-11-14T11:00:00Z","change_id":"c1"}}""")
        assertEquals(ApiError.GuardDevicePending("d1", "2026-11-14T11:00:00Z", "c1"), err)
    }

    @Test
    fun guard_paused_429_maps_with_retry() {
        val err = client.map_http_status(429, """{"code":"GUARD_PAUSED","details":{"retry_after_secs":3600}}""")
        assertEquals(ApiError.GuardRetry(GUARD_PAUSED_CODE, 3600), err)
    }

    @Test
    fun guard_too_many_429_falls_back_to_retry_after_header() {
        val err = client.map_http_status(429, """{"code":"GUARD_TOO_MANY_REQUESTS"}""", retry_after = "120")
        assertEquals(ApiError.GuardRetry(GUARD_TOO_MANY_REQUESTS_CODE, 120), err)
    }

    @Test
    fun plain_409_still_conflict() {
        assertTrue(client.map_http_status(409, """{"code":"X"}""") is ApiError.Conflict)
    }

    @Test
    fun plain_429_still_rate_limited() {
        assertTrue(client.map_http_status(429, """{"code":"RATE_LIMITED"}""") is ApiError.RateLimited)
    }

    @Test
    fun guard_device_required_stays_forbidden_with_code() {
        val err = client.map_http_status(403, """{"code":"GUARD_DEVICE_REQUIRED"}""") as ApiError.ForbiddenError
        assertEquals(GUARD_DEVICE_REQUIRED_CODE, err.code)
    }

    @Test
    fun guard_request_gone_410_keeps_its_code() {
        val err = client.map_http_status(410, """{"code":"GUARD_REQUEST_GONE"}""")
        assertEquals(GUARD_REQUEST_GONE_CODE, api_error_code(err))
    }

    @Test
    fun guard_required_without_token_stays_forbidden() {
        assertTrue(client.map_http_status(403, """{"code":"GUARD_REQUIRED"}""") is ApiError.ForbiddenError)
    }

    @Test
    fun fingerprint_mismatch_surfaces_as_step_up_instead_of_sign_out() {
        val err = map_unauthorized("FINGERPRINT_MISMATCH", "device changed")
        assertTrue(err is ApiError.StepUpRequired)
        assertEquals("device changed", (err as ApiError.StepUpRequired).detail)
        assertFalse(should_emit_unauthorized("FINGERPRINT_MISMATCH"))
    }

    @Test
    fun invalid_credentials_do_not_sign_out_either() {
        assertEquals(ApiError.InvalidCredentials, map_unauthorized("INVALID_CREDENTIALS", ""))
        assertFalse(should_emit_unauthorized("INVALID_CREDENTIALS"))
    }

    @Test
    fun other_unauthorized_responses_still_sign_out() {
        assertEquals(ApiError.UnauthorizedError, map_unauthorized(null, ""))
        assertTrue(should_emit_unauthorized(null))
        assertTrue(should_emit_unauthorized("SESSION_EXPIRED"))
    }
}
