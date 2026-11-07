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

import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.crypto.VaultKdf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultWorkFactorTest {

    private class Codec : StorageVaultCodec {
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
        override fun encrypt(vault: JSONObject): SealedVault = encrypt_at(vault, VaultKdf.DEFAULT_ITERATIONS)
        override fun encrypt_at(vault: JSONObject, iterations: Int): SealedVault =
            SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=", iterations)
        override fun password_keys(vault: JSONObject): List<ByteArray> = emptyList()
        override fun generate_key(): ByteArray = ByteArray(32) { 3 }
        override fun fingerprints(vault: JSONObject): List<String> = emptyList()
    }

    private class Server(var vault: JSONObject, var stored_iterations: Int?, val admits_level2: Boolean) : ResealApi {
        val sent_iterations = mutableListOf<Int?>()

        fun sealed(): String = encode_b64(vault.toString().toByteArray(Charsets.UTF_8))

        fun digest(sealed: String): String =
            MessageDigest.getInstance("SHA-256").digest(sealed.toByteArray()).joinToString("") { "%02x".format(it) }

        override suspend fun get(path: String): ApiReply {
            val sealed = sealed()
            val fields = mutableMapOf<String, JsonElement>(
                "encrypted_vault" to JsonPrimitive(sealed),
                "vault_nonce" to JsonPrimitive("bm9uY2U="),
                "vault_sha256" to JsonPrimitive(digest(sealed)),
                "vault_kdf_iterations" to (stored_iterations?.let { JsonPrimitive(it) } ?: JsonNull),
            )
            return ApiReply(200, JsonObject(fields))
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply = ApiReply(404)

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            val obj = body as JsonObject
            val iterations = obj.long("vault_kdf_iterations")?.toInt()
            sent_iterations.add(iterations)
            if (iterations == VaultKdf.LEVEL2_ITERATIONS && !admits_level2) {
                return ApiReply(409, null, VAULT_LEVEL2_NOT_ADMITTED_CODE)
            }
            if (obj.str("expected_vault_sha256") != digest(sealed())) return ApiReply(409, null, VAULT_CHANGED_CODE)
            vault = JSONObject(String(decode_b64(obj.str("encrypted_vault"))!!, Charsets.UTF_8))
            stored_iterations = iterations
            return ApiReply(200, json_object("ok" to true))
        }
    }

    private fun updater(server: Server, upgrade: Boolean) = VaultUpdater(
        api = server,
        codec = Codec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { _, _ -> },
        now = { "2026-11-07T00:00:00Z" },
        upgrade_on_rotation = upgrade,
    )

    private fun edit(draft: VaultDraft): VaultEdit {
        draft.vault.put("note", "changed")
        return VaultEdit.CHANGED
    }

    @Test
    fun a_missing_or_null_work_factor_means_the_default() {
        assertEquals(VaultKdf.DEFAULT_ITERATIONS, parse_vault_kdf_iterations(JsonObject(emptyMap())))
        assertEquals(
            VaultKdf.DEFAULT_ITERATIONS,
            parse_vault_kdf_iterations(JsonObject(mapOf("vault_kdf_iterations" to JsonNull))),
        )
        assertEquals(
            VaultKdf.LEVEL2_ITERATIONS,
            parse_vault_kdf_iterations(JsonObject(mapOf("vault_kdf_iterations" to JsonPrimitive(600000)))),
        )
        assertNull(parse_vault_kdf_iterations(JsonObject(mapOf("vault_kdf_iterations" to JsonPrimitive(1000)))))
    }

    @Test
    fun a_rotation_writes_the_higher_work_factor_when_admitted() = runBlocking {
        val server = Server(JSONObject().put("identity_key", "id"), null, admits_level2 = true)

        val outcome = updater(server, upgrade = true).update(allow_storage_key_change = true, edit = { edit(it) })

        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(listOf<Int?>(VaultKdf.LEVEL2_ITERATIONS), server.sent_iterations)
        assertEquals(VaultKdf.LEVEL2_ITERATIONS, server.stored_iterations)
    }

    @Test
    fun a_refused_higher_work_factor_retries_silently_at_the_default() = runBlocking {
        val server = Server(JSONObject().put("identity_key", "id"), null, admits_level2 = false)

        val outcome = updater(server, upgrade = true).update(allow_storage_key_change = true, edit = { edit(it) })

        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(listOf(VaultKdf.LEVEL2_ITERATIONS, null), server.sent_iterations)
        assertNull(server.stored_iterations)
        assertEquals("changed", server.vault.getString("note"))
    }

    @Test
    fun an_ordinary_write_keeps_the_stored_work_factor() = runBlocking {
        val server = Server(JSONObject().put("identity_key", "id"), VaultKdf.LEVEL2_ITERATIONS, admits_level2 = true)

        val outcome = updater(server, upgrade = false).update(edit = { edit(it) })

        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(listOf<Int?>(VaultKdf.LEVEL2_ITERATIONS), server.sent_iterations)
        assertEquals(VaultKdf.LEVEL2_ITERATIONS, server.stored_iterations)
    }

    @Test
    fun an_ordinary_write_never_raises_the_work_factor() = runBlocking {
        val server = Server(JSONObject().put("identity_key", "id"), null, admits_level2 = true)

        updater(server, upgrade = true).update(edit = { edit(it) })

        assertEquals(listOf<Int?>(null), server.sent_iterations)
    }
}
