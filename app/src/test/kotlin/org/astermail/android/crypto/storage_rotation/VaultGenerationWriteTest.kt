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
import kotlinx.serialization.json.JsonObject
import org.astermail.android.auth.build_signup_vault
import org.astermail.android.auth.prepare_password_change_vault
import org.astermail.android.auth.reseal_recovery_vault
import org.astermail.android.crypto.VaultKdf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultGenerationWriteTest {

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

    private class Server(var vault: JSONObject) : ResealApi {
        var puts = 0

        fun sealed(): String = encode_b64(vault.toString().toByteArray(Charsets.UTF_8))

        fun digest(sealed: String): String =
            MessageDigest.getInstance("SHA-256").digest(sealed.toByteArray()).joinToString("") { "%02x".format(it) }

        override suspend fun get(path: String): ApiReply {
            val sealed = sealed()
            return ApiReply(
                200,
                json_object(
                    "encrypted_vault" to sealed,
                    "vault_nonce" to "bm9uY2U=",
                    "vault_sha256" to digest(sealed),
                    "vault_kdf_iterations" to VaultKdf.to_server(VaultKdf.DEFAULT_ITERATIONS),
                ),
            )
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply = ApiReply(404)

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            val obj = body as JsonObject
            if (obj.str("expected_vault_sha256") != digest(sealed())) return ApiReply(409, null, VAULT_CHANGED_CODE)
            puts += 1
            vault = JSONObject(String(decode_b64(obj.str("encrypted_vault"))!!, Charsets.UTF_8))
            return ApiReply(200, json_object("ok" to true))
        }
    }

    private fun updater(server: Server) = VaultUpdater(
        api = server,
        codec = Codec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { _, _ -> },
        now = { "2026-11-17T00:00:00Z" },
    )

    private fun stored(generation: Int?): JSONObject {
        val vault = JSONObject()
            .put("identity_key", "identity")
            .put("data_kek", encode_b64(ByteArray(32) { 7 }))
            .put("data_kek_random", true)
        generation?.let { vault.put(VAULT_GENERATION_FIELD, it) }
        return vault
    }

    @Test
    fun an_edit_raises_the_generation_by_one() = runBlocking {
        val server = Server(stored(4))
        updater(server).update { draft ->
            draft.vault.put("recovery_codes", JSONArray().put("code"))
            VaultEdit.CHANGED
        }
        assertEquals(1, server.puts)
        assertEquals(5L, vault_generation(server.vault))
    }

    @Test
    fun a_vault_without_a_generation_starts_counting_at_one() = runBlocking {
        val server = Server(stored(null))
        updater(server).update { draft ->
            draft.vault.put("recovery_codes", JSONArray().put("code"))
            VaultEdit.CHANGED
        }
        assertEquals(1L, vault_generation(server.vault))
    }

    @Test
    fun a_full_rewrite_still_raises_the_generation() = runBlocking {
        val server = Server(stored(9))
        updater(server).update { draft ->
            draft.vault.keys().asSequence().toList().forEach { draft.vault.remove(it) }
            draft.vault.put("identity_key", "identity").put(VAULT_GENERATION_FIELD, 2)
            VaultEdit.CHANGED
        }
        assertEquals(10L, vault_generation(server.vault))
    }

    @Test
    fun a_replacement_sent_elsewhere_raises_the_generation() = runBlocking {
        val server = Server(stored(3))
        var sent: JSONObject? = null
        updater(server).replace_through(
            edit = { draft ->
                draft.vault.put("recovery_codes", JSONArray().put("rotated"))
                VaultEdit.CHANGED
            },
            send = { vault, _, _ ->
                sent = vault
                true
            },
        )
        assertEquals(4L, vault_generation(sent))
    }

    @Test
    fun raising_the_work_factor_raises_the_generation() = runBlocking {
        val server = Server(stored(6))
        updater(server).raise_work_factor()
        assertEquals(1, server.puts)
        assertEquals(7L, vault_generation(server.vault))
    }

    @Test
    fun a_recovery_reseal_raises_the_backup_generation() {
        val backup = JSONObject()
            .put("identity_private_key", "raw-native-identity")
            .put(VAULT_GENERATION_FIELD, 8)
        val result = reseal_recovery_vault(backup, listOf("AAAA-BBBB"), "new password".toCharArray(), null)
        assertEquals(9L, vault_generation(result.vault))
    }

    @Test
    fun a_vault_recovered_from_an_older_backup_moves_past_every_device_pin() {
        val pinned = 10L
        val backup = JSONObject()
            .put("identity_private_key", "raw-native-identity")
            .put(VAULT_GENERATION_FIELD, pinned - 3)
        val result = reseal_recovery_vault(
            backup,
            listOf("AAAA-BBBB"),
            "new password".toCharArray(),
            null,
            server_write_count = pinned,
        )
        assertEquals(pinned + 1, vault_generation(result.vault))
        assertTrue(vault_generation(result.vault) > pinned)
    }

    @Test
    fun a_write_count_outside_the_trusted_range_is_ignored() {
        val backup = JSONObject()
            .put("identity_private_key", "raw-native-identity")
            .put(VAULT_GENERATION_FIELD, 7)
        val result = reseal_recovery_vault(
            backup,
            listOf("AAAA-BBBB"),
            "new password".toCharArray(),
            null,
            server_write_count = 1L shl 50,
        )
        assertEquals(8L, vault_generation(result.vault))
    }

    @Test
    fun malformed_generations_count_as_zero() {
        assertEquals(0L, vault_generation(JSONObject().put(VAULT_GENERATION_FIELD, "7")))
        assertEquals(0L, vault_generation(JSONObject().put(VAULT_GENERATION_FIELD, -3)))
        assertEquals(0L, vault_generation(JSONObject().put(VAULT_GENERATION_FIELD, 2.5)))
        assertEquals(12L, vault_generation(JSONObject().put(VAULT_GENERATION_FIELD, 12)))
    }

    @Test
    fun a_signup_vault_starts_at_generation_one() {
        val vault = build_signup_vault("identity", "prekey", listOf("code"), null, encode_b64(ByteArray(32) { 1 }), 1L)
        assertEquals(1L, vault_generation(vault))
    }

    @Test
    fun a_password_change_raises_the_generation_by_one() {
        val vault = prepare_password_change_vault(stored(6), LocalStorageKeys(null, emptyList()), "2026-11-17T00:00:00Z")
        assertEquals(7L, vault_generation(vault))
    }

    @Test
    fun a_password_change_on_an_untracked_vault_starts_at_one() {
        val vault = prepare_password_change_vault(stored(null), LocalStorageKeys(null, emptyList()), "2026-11-17T00:00:00Z")
        assertEquals(1L, vault_generation(vault))
    }
}
