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

import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.astermail.android.api.auth.AuthApiImpl
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionRefresher
import org.astermail.android.api.auth.SessionTokenProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class refresh_loop_regression_test {

    private lateinit var server: MockWebServer
    private var client: ApiClient? = null
    private lateinit var refresher: SessionRefresher
    private val refresh_hits = AtomicInteger(0)
    private val listener_hits = AtomicInteger(0)

    @Volatile
    private var access: String? = "access_0"

    @Volatile
    private var refresh: String? = "refresh_0"

    @Before
    fun set_up() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tear_down() {
        runCatching { client?.close() }
        server.shutdown()
    }

    private fun unauthorized(): MockResponse = MockResponse()
        .setResponseCode(401)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"error\":\"unauthorized\",\"code\":\"UNAUTHORIZED\"}")

    private fun wire(respond: (RecordedRequest) -> MockResponse): ApiClient {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path.orEmpty().contains("/auth/refresh")) refresh_hits.incrementAndGet()
                return respond(request)
            }
        }
        val provider = SessionTokenProvider(
            read_access_token = { access },
            read_refresh_token = { refresh },
            refresh_session = { refresher.refresh() },
        )
        val api_client = ApiClient(
            base_url = server.url("/").toString().trimEnd('/'),
            token_provider = provider,
            allow_cleartext_for_test = true,
        )
        refresher = SessionRefresher(
            read_refresh_token = { refresh },
            perform_refresh = { current ->
                try {
                    val response = AuthApiImpl(api_client).refresh(current)
                    access = response.access_token
                    refresh = response.refresh_token ?: current
                    RefreshOutcome.Success
                } catch (e: CancellationException) {
                    throw e
                } catch (_: ApiError.UnauthorizedError) {
                    RefreshOutcome.AuthFailed
                } catch (_: Throwable) {
                    RefreshOutcome.Transient
                }
            },
            notify_scope = CoroutineScope(Dispatchers.Unconfined),
        )
        refresher.on_auth_failure { listener_hits.incrementAndGet() }
        client = api_client
        return api_client
    }

    private suspend fun get_messages(api_client: ApiClient): Int? = runCatching {
        withTimeout(5_000) {
            api_client.http.get("${api_client.base_url}/api/mail/v1/messages").status.value
        }
    }.getOrNull()

    @Test
    fun a_revoked_session_refreshes_once_and_then_stops() = runBlocking {
        val api_client = wire { unauthorized() }

        repeat(10) { assertEquals(401, get_messages(api_client)) }
        (1..8).map { async(Dispatchers.IO) { get_messages(api_client) } }.awaitAll()

        assertEquals(1, refresh_hits.get())
        assertEquals(1, listener_hits.get())
    }

    @Test
    fun a_burst_of_expired_requests_rotates_the_token_once() = runBlocking {
        val api_client = wire { request ->
            val path = request.path.orEmpty()
            when {
                path.contains("/auth/refresh") -> {
                    if (request.body.readUtf8().contains("refresh_0")) {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"csrf_token\":\"csrf_1\",\"access_token\":\"access_1\",\"refresh_token\":\"refresh_1\"}")
                    } else {
                        unauthorized()
                    }
                }
                request.getHeader("Authorization") == "Bearer access_1" ->
                    MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{}")
                else -> unauthorized()
            }
        }

        val statuses = (1..12).map { async(Dispatchers.IO) { get_messages(api_client) } }.awaitAll()
        repeat(5) { assertEquals(200, get_messages(api_client)) }

        assertEquals(List(12) { 200 }, statuses)
        assertEquals(1, refresh_hits.get())
        assertEquals(0, listener_hits.get())
        assertEquals("refresh_1", refresh)
    }
}
