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

package org.astermail.android.settings

import io.ktor.client.plugins.auth.providers.BearerTokens
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.astermail.android.api.ApiClient
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.settings.SettingsApiImpl
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class DomainAddressDeleteTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private val deletes = CopyOnWriteArrayList<Pair<RecordedRequest, String>>()

    private val local_part = "billing"
    private val domain_name = "custom.example"

    private fun address_list_json(): String {
        val bytes = local_part.toByteArray(Charsets.UTF_8)
        val encoder = java.util.Base64.getEncoder()
        val encoded = encoder.encodeToString(bytes)
        val hash = encoder.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        return """{"addresses":[{"id":"addr-1","domain_name":"$domain_name","encrypted_local_part":"$encoded","local_part_hash":"$hash"}]}"""
    }

    private fun json_response(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "DELETE") {
                    deletes.add(request to request.body.readUtf8())
                    return json_response("""{"success":true}""")
                }
                return when (request.path.orEmpty()) {
                    "/api/addresses/v1/domains" ->
                        json_response("""{"domains":[{"id":"domain-1","domain_name":"$domain_name","status":"active"}]}""")
                    "/api/addresses/v1/domains/addresses" -> json_response(address_list_json())
                    else -> json_response("{}")
                }
            }
        }
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(android.util.Base64::class)
        Dispatchers.resetMain()
    }

    private fun build_client(): ApiClient = ApiClient(
        base_url = server.url("/").toString().trimEnd('/'),
        token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? = BearerTokens("access", "refresh")
            override suspend fun refresh(): BearerTokens? = BearerTokens("access", "refresh")
            override suspend fun clear() {}
        },
        initial_csrf = "session-A:1799999999.signature",
        allow_cleartext_for_test = true,
    )

    private fun build_vm(): SettingsViewModel {
        val auth_repository = mockk<AuthRepository>(relaxed = true)
        every { auth_repository.is_signed_in } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        val session_key_store = mockk<SessionKeyStore>(relaxed = true)
        every { session_key_store.get_data_kek() } answers { ByteArray(32) { (it + 1).toByte() } }
        return SettingsViewModel(
            auth_api = mockk(relaxed = true),
            user_api = mockk(relaxed = true),
            settings_api = SettingsApiImpl(build_client()),
            labels_api = mockk(relaxed = true),
            family_api = mockk(relaxed = true),
            tags_api = mockk(relaxed = true),
            preferences_api = mockk(relaxed = true),
            signatures_api = mockk(relaxed = true),
            ghost_alias_api = mockk(relaxed = true),
            auto_forward_api = mockk(relaxed = true),
            developer_api = mockk(relaxed = true),
            subscriptions_api = mockk(relaxed = true),
            recovery_email_api = mockk(relaxed = true),
            security_api = mockk(relaxed = true),
            encryption_api = mockk(relaxed = true),
            alias_detail_api = mockk(relaxed = true),
            recovery_api = mockk(relaxed = true),
            mail_rules_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = mockk(relaxed = true),
            account_store = mockk(relaxed = true),
            preferences_cache = mockk(relaxed = true),
            theme_store = mockk(relaxed = true),
            context = mockk(relaxed = true),
        ).also {
            it.default_dispatcher = dispatcher
            it.io_dispatcher = dispatcher
        }
    }

    private fun TestScope.settle(until: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!until() && System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            Thread.sleep(20)
        }
        advanceUntilIdle()
    }

    @Test
    fun `deleting a domain address names the plaintext address in the request body`() = runTest(dispatcher) {
        val vm = build_vm()
        vm.load_domains(prefetch_dns = false)
        vm.load_custom_domain_addresses(force = true)
        settle { vm.state.value.domains.isNotEmpty() && vm.state.value.custom_domain_addresses.isNotEmpty() }
        assertEquals("$local_part@$domain_name", vm.state.value.custom_domain_addresses.single().address)

        vm.delete_domain_address("addr-1", domain_name)
        settle { deletes.isNotEmpty() }

        val (request, body) = deletes.firstOrNull() ?: error("no delete request was sent")
        assertEquals("/api/addresses/v1/domains/domain-1/addresses/addr-1", request.path)
        assertTrue("body was <$body>", body.contains(""""address":"$local_part@$domain_name""""))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
    }
}
