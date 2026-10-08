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
import org.astermail.android.api.auth.AuthApiImpl
import org.astermail.android.api.auth.TotpLoginVerifyRequest
import org.astermail.android.api.auth.WebAuthnAssertionInitiateRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SecondFactorBearerTest {
    private lateinit var server: MockWebServer
    private val recorded = mutableMapOf<String, RecordedRequest>()

    @Before
    fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                recorded[path] = request
                return when {
                    path.endsWith("/hardware-keys/assert/initiate") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"challenge":"c","challenge_token":"t","rpId":"app.astermail.org"}""")
                    path.endsWith("/auth/me") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"user_id":"u1"}""")
                    else -> MockResponse().setResponseCode(400).setBody("""{"error":"no"}""")
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
            override suspend fun load(): BearerTokens? = BearerTokens("first-account-access", "first-account-refresh")
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
    fun second_factor_calls_do_not_carry_the_active_accounts_bearer() = runBlocking {
        val auth = AuthApiImpl(build_client())
        auth.initiate_webauthn_assertion(WebAuthnAssertionInitiateRequest("pending"))
        runCatching { auth.verify_totp_login(TotpLoginVerifyRequest("123456", "pending")) }
        runCatching { auth.verify_backup_code_login(TotpLoginVerifyRequest("abcd-efgh", "pending")) }

        listOf(
            "/api/core/v1/auth/hardware-keys/assert/initiate",
            "/api/core/v1/auth/totp/verify",
            "/api/core/v1/auth/totp/backup-code",
        ).forEach { path ->
            val request = requireNotNull(recorded[path]) { "no request recorded for $path" }
            assertNull(path, request.getHeader("Authorization"))
        }
    }

    @Test
    fun signed_in_calls_still_carry_the_bearer() = runBlocking {
        val auth = AuthApiImpl(build_client())
        auth.me()
        val request = requireNotNull(recorded["/api/core/v1/auth/me"])
        assertEquals("Bearer first-account-access", request.getHeader("Authorization"))
    }
}
