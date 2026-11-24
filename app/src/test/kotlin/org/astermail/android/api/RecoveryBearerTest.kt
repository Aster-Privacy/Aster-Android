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
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.astermail.android.api.recovery.RecoveryApiImpl
import org.astermail.android.api.recovery.VerifierSaltsRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RecoveryBearerTest {
    private lateinit var server: MockWebServer
    private val sent = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())

    @Before
    fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                sent += request
                val authorized = request.getHeader("Authorization") == "Bearer access"
                val is_public = request.path.orEmpty().contains("/verifier-salts")
                return if (authorized || is_public) {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"inactive_key_sets":[],"salts":[]}""")
                } else {
                    MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"error":"unauthorized","code":"UNAUTHORIZED"}""")
                }
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
        )
    }

    @Test
    fun signed_in_recovery_call_sends_the_bearer_on_the_first_request() = runBlocking {
        RecoveryApiImpl(build_client()).list_inactive_key_sets()
        assertEquals(1, sent.size)
        assertEquals("Bearer access", sent.first().getHeader("Authorization"))
    }

    @Test
    fun public_recovery_call_never_carries_a_bearer() = runBlocking {
        runCatching { RecoveryApiImpl(build_client()).verifier_salts(VerifierSaltsRequest(email = "someone@example.test")) }
        assertEquals(1, sent.size)
        assertNull(sent.first().getHeader("Authorization"))
    }
}
