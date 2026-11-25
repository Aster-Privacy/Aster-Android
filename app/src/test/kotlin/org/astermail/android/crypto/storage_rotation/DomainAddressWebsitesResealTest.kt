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

package org.astermail.android.crypto.storage_rotation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.astermail.android.auth.STORAGE_REKEY_PENDING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAddressWebsitesResealTest {

    private fun key(seed: Int): ByteArray = ByteArray(32) { (seed + it).toByte() }

    private val old_key = key(10)
    private val fresh_key = key(130)
    private val websites_json = "[\"https://shop.example.com\",\"https://news.example.org\"]"

    private fun view(): VaultKeyView = VaultKeyView(
        data_kek = encode_b64(fresh_key),
        data_kek_random = true,
        storage_rekey_state = STORAGE_REKEY_PENDING,
        prior_keys = listOf(encode_b64(old_key)),
        legacy_keks = emptyList(),
    )

    private class DomainServer(private val address: JsonObject) : ResealApi {
        val rekey_bodies = mutableListOf<JsonObject>()

        override suspend fun get(path: String): ApiReply = when (path) {
            "/addresses/v1/domains" -> ApiReply(
                200,
                json_object("domains" to JsonArray(listOf(json_object("id" to "d1", "domain_name" to "example.com")))),
            )
            "/addresses/v1/domains/d1/addresses" -> ApiReply(200, json_object("addresses" to JsonArray(listOf(address))))
            else -> ApiReply(404)
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply {
            if (path == USER_REKEY_PATH) rekey_bodies.add(body as JsonObject)
            return ApiReply(200, json_object("ok" to true))
        }

        override suspend fun put(path: String, body: JsonElement): ApiReply = ApiReply(200, json_object("ok" to true))
    }

    private fun current_address(websites_key: ByteArray?): JsonObject {
        val local = gcm_seal(fresh_key, "shop".toByteArray(Charsets.UTF_8))
        val hash = sign_with(derive_hmac_key(fresh_key, DOMAIN_ADDRESS_HMAC_INFO), "shop@example.com")
        val pairs = mutableListOf<Pair<String, Any?>>(
            "id" to "a1",
            "encrypted_local_part" to local.ciphertext,
            "local_part_nonce" to local.nonce,
            "local_part_hash" to hash,
        )
        if (websites_key != null) {
            val sealed = gcm_seal(websites_key, websites_json.toByteArray(Charsets.UTF_8))
            pairs.add("encrypted_websites" to sealed.ciphertext)
            pairs.add("websites_nonce" to sealed.nonce)
        }
        return json_object(*pairs.toTypedArray())
    }

    private fun run(server: DomainServer) = runBlocking {
        run_storage_reseal(
            view = view(),
            api = server,
            store = MemoryProgressStore(),
            finish = { StorageRekeyFinish.DONE },
            still_current = { true },
            sleep = {},
            steps = listOf(domain_addresses_step),
        )
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    @Test
    fun stale_websites_are_resealed_under_the_new_key() {
        val server = DomainServer(current_address(old_key))
        run(server)
        val entries = server.rekey_bodies.flatMap { (it["re_encrypted_domain_addresses"] as JsonArray).map { e -> e as JsonObject } }
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("a1", entry.text("id"))
        val ct = decode_b64(entry.text("encrypted_websites"))
        val iv = decode_b64(entry.text("websites_nonce"))
        assertNotNull(ct)
        assertNotNull(iv)
        val opened = gcm_open(fresh_key, ct!!, iv!!)
        assertNotNull(opened)
        assertEquals(websites_json, String(opened!!, Charsets.UTF_8))
    }

    @Test
    fun current_websites_need_no_rekey_entry() {
        val server = DomainServer(current_address(fresh_key))
        run(server)
        val entries = server.rekey_bodies.flatMap { (it["re_encrypted_domain_addresses"] as JsonArray).toList() }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun address_without_websites_sends_no_websites_fields() {
        val stale_local = gcm_seal(old_key, "shop".toByteArray(Charsets.UTF_8))
        val base = current_address(null)
        val address = JsonObject(
            base + mapOf(
                "encrypted_local_part" to JsonPrimitive(stale_local.ciphertext),
                "local_part_nonce" to JsonPrimitive(stale_local.nonce),
            ),
        )
        val server = DomainServer(address)
        run(server)
        val entry = server.rekey_bodies
            .flatMap { (it["re_encrypted_domain_addresses"] as JsonArray).map { e -> e as JsonObject } }
            .single()
        assertNull(entry["encrypted_websites"])
        assertNull(entry["websites_nonce"])
    }
}
