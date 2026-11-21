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

import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import io.ktor.client.plugins.auth.providers.BearerTokens
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GuardHeldInterceptTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = ApiClient(
            base_url = server.url("/").toString().trimEnd('/'),
            token_provider = object : TokenProvider {
                override suspend fun load(): BearerTokens? = BearerTokens("a", "r")
                override suspend fun refresh(): BearerTokens? = null
                override suspend fun clear() {}
            },
            allow_cleartext_for_test = true,
        )
    }

    @After
    fun teardown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun held_202_throws_guard_held() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setHeader(GUARD_HEADER, "1")
                .setBody("""{"error":"held","code":"GUARD_HELD","details":{"id":"c1","kind":"forwarding_change","apply_after":"2026-11-14T11:00:00Z"}}"""),
        )
        try {
            client.http.post("${client.base_url}/api/core/v1/autoforward")
            fail("expected GuardHeld")
        } catch (held: ApiError.GuardHeld) {
            assertEquals(ApiError.GuardHeld("c1", "forwarding_change", "2026-11-14T11:00:00Z"), held)
        }
    }

    @Test
    fun plain_202_returns_normally() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"queued"}"""),
        )
        val response = client.http.post("${client.base_url}/api/core/v1/imports")
        assertEquals(202, response.status.value)
        assertEquals("""{"status":"queued"}""", response.bodyAsText())
    }

    @Test
    fun every_request_carries_the_guard_header() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.http.post("${client.base_url}/api/core/v1/anything")
        assertEquals("1", server.takeRequest().getHeader(GUARD_HEADER))
    }
}
