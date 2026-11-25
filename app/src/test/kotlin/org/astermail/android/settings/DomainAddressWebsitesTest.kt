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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.astermail.android.api.ApiClient
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.settings.SettingsApiImpl
import org.astermail.android.api.settings.domain_address_websites_patch_body
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.AesGcm
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class DomainAddressWebsitesTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private val patches = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile private var per_domain_status = 200

    private val domain_name = "custom.example"
    private val storage_key = ByteArray(32) { (it + 1).toByte() }
    private val encoder = java.util.Base64.getEncoder()
    private val websites_plaintext = """["https://shop.example.com","https://news.example.org"]"""

    private fun address_json(id: String, local_part: String): String {
        val bytes = local_part.toByteArray(Charsets.UTF_8)
        val encoded = encoder.encodeToString(bytes)
        val hash = encoder.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        return """{"id":"$id","domain_name":"$domain_name","encrypted_local_part":"$encoded","local_part_hash":"$hash","is_enabled":true}"""
    }

    private fun seal(plaintext: String): Pair<String, String> {
        val nonce = ByteArray(12) { (it + 7).toByte() }
        val ct = AesGcm.encrypt(storage_key, nonce, plaintext.toByteArray(Charsets.UTF_8))
        return encoder.encodeToString(ct) to encoder.encodeToString(nonce)
    }

    private fun per_domain_json(): String {
        val (ct, nonce) = seal(websites_plaintext)
        val garbage = encoder.encodeToString(ByteArray(40) { 3 })
        val garbage_nonce = encoder.encodeToString(ByteArray(12) { 5 })
        return """{"addresses":[""" +
            """{"id":"addr-1","encrypted_websites":"$ct","websites_nonce":"$nonce"},""" +
            """{"id":"addr-2","encrypted_websites":"$garbage","websites_nonce":"$garbage_nonce"},""" +
            """{"id":"addr-3","encrypted_websites":null,"websites_nonce":null}]}"""
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
                val path = request.path.orEmpty()
                if (request.method == "PATCH") {
                    patches.add(path to request.body.readUtf8())
                    return json_response("""{"success":true}""")
                }
                return when (path) {
                    "/api/addresses/v1/domains" ->
                        json_response("""{"domains":[{"id":"domain-1","domain_name":"$domain_name","status":"active"}]}""")
                    "/api/addresses/v1/domains/addresses" -> json_response(
                        """{"addresses":[${address_json("addr-1", "billing")},${address_json("addr-2", "orders")},${address_json("addr-3", "hello")}]}""",
                    )
                    "/api/addresses/v1/domains/domain-1/addresses" ->
                        if (per_domain_status == 200) {
                            json_response(per_domain_json())
                        } else {
                            MockResponse().setResponseCode(per_domain_status).setBody("{}")
                        }
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

    private fun TestScope.loaded_vm(): SettingsViewModel {
        val vm = build_vm()
        vm.load_domains(prefetch_dns = false)
        settle { vm.state.value.domains.isNotEmpty() }
        vm.load_custom_domain_addresses(force = true)
        settle { vm.state.value.custom_domain_addresses.size == 3 }
        return vm
    }

    private fun body_of(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `websites payload round trips through serialize and parse`() {
        val list = listOf("https://shop.example.com", "https://news.example.org")
        val raw = serialize_alias_websites_payload(list)
        assertEquals(websites_plaintext, raw)
        assertEquals(list, parse_alias_websites_payload(raw))
    }

    @Test
    fun `websites payload parse dedupes and caps at ten`() {
        val many = (1..12).joinToString(",", "[", "]") { "\"https://site$it.example.com\"" }
        val parsed = parse_alias_websites_payload(many)
        assertEquals(ALIAS_WEBSITES_MAX_COUNT, parsed.size)
        assertEquals(parsed.distinct(), parsed)
    }

    @Test
    fun `patch body carries both website fields`() {
        val body = domain_address_websites_patch_body("Y2lwaGVy", "bm9uY2U=")
        assertEquals(setOf("encrypted_websites", "websites_nonce"), body.keys)
        assertEquals(JsonPrimitive("Y2lwaGVy"), body["encrypted_websites"])
        assertEquals(JsonPrimitive("bm9uY2U="), body["websites_nonce"])
    }

    @Test
    fun `patch body clears both website fields with explicit nulls`() {
        val body = domain_address_websites_patch_body(null, null)
        assertEquals(setOf("encrypted_websites", "websites_nonce"), body.keys)
        assertEquals(JsonNull, body["encrypted_websites"])
        assertEquals(JsonNull, body["websites_nonce"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patch body refuses a half filled pair`() {
        domain_address_websites_patch_body("Y2lwaGVy", null)
    }

    @Test
    fun `decrypt failure yields no websites`() {
        val result = decrypt_domain_address_websites("YWJj", "bm9uY2U=") { _, _ -> error("bad key") }
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `loading merges websites from the per domain list and tolerates bad ciphertext`() = runTest(dispatcher) {
        val vm = loaded_vm()
        settle { vm.state.value.custom_domain_addresses.firstOrNull { it.id == "addr-1" }?.websites?.isNotEmpty() == true }
        val by_id = vm.state.value.custom_domain_addresses.associateBy { it.id }
        assertEquals(listOf("https://shop.example.com", "https://news.example.org"), by_id.getValue("addr-1").websites)
        assertEquals("orders@$domain_name", by_id.getValue("addr-2").address)
        assertFalse(by_id.getValue("addr-2").decryption_failed)
        assertEquals(emptyList<String>(), by_id.getValue("addr-2").websites)
        assertEquals(emptyList<String>(), by_id.getValue("addr-3").websites)
    }

    @Test
    fun `failed per domain fetch still loads every address`() = runTest(dispatcher) {
        per_domain_status = 500
        val vm = loaded_vm()
        assertEquals(3, vm.state.value.custom_domain_addresses.size)
        assertTrue(vm.state.value.custom_domain_addresses.all { it.websites.isEmpty() })
        assertTrue(vm.state.value.custom_domain_addresses.none { it.websites_loaded })
    }

    @Test
    fun `websites stay read only until the per domain list loads`() = runTest(dispatcher) {
        per_domain_status = 500
        val vm = loaded_vm()
        vm.add_domain_address_website("addr-1", "https://example.com/signup")
        vm.update_domain_address_websites("addr-1", "")
        settle { false }
        assertTrue(patches.isEmpty())
    }

    @Test
    fun `adding a website patches both encrypted fields`() = runTest(dispatcher) {
        val vm = loaded_vm()
        vm.add_domain_address_website("addr-3", "https://example.com/signup")
        settle { patches.isNotEmpty() }
        val (path, raw) = patches.firstOrNull() ?: error("no patch request was sent")
        assertEquals("/api/addresses/v1/domains/domain-1/addresses/addr-3", path)
        val body = body_of(raw)
        assertEquals(setOf("encrypted_websites", "websites_nonce"), body.keys)
        val ct = java.util.Base64.getDecoder().decode((body["encrypted_websites"] as JsonPrimitive).content)
        val nonce = java.util.Base64.getDecoder().decode((body["websites_nonce"] as JsonPrimitive).content)
        val opened = String(AesGcm.decrypt(storage_key, nonce, ct), Charsets.UTF_8)
        val expected = vm.state.value.custom_domain_addresses.first { it.id == "addr-3" }.websites
        assertEquals(1, expected.size)
        assertEquals(expected, parse_alias_websites_payload(opened))
    }

    @Test
    fun `removing the last website clears both fields with nulls`() = runTest(dispatcher) {
        val vm = loaded_vm()
        settle { vm.state.value.custom_domain_addresses.firstOrNull { it.id == "addr-1" }?.websites?.size == 2 }
        vm.remove_domain_address_website("addr-1", "https://shop.example.com")
        settle { patches.isNotEmpty() }
        vm.remove_domain_address_website("addr-1", "https://news.example.org")
        settle { patches.size >= 2 }
        val body = body_of(patches[1].second)
        assertEquals(setOf("encrypted_websites", "websites_nonce"), body.keys)
        assertEquals(JsonNull, body["encrypted_websites"])
        assertEquals(JsonNull, body["websites_nonce"])
    }

    @Test
    fun `toggling an address does not send website fields`() = runTest(dispatcher) {
        val vm = loaded_vm()
        vm.toggle_domain_address("addr-1", domain_name)
        settle { patches.isNotEmpty() }
        val body = body_of(patches.first().second)
        assertFalse(body.containsKey("encrypted_websites"))
        assertFalse(body.containsKey("websites_nonce"))
        assertEquals(JsonPrimitive(false), body["is_enabled"])
    }
}
