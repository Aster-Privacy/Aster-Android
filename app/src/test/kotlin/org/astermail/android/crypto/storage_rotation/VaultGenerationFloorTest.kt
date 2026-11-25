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
import org.astermail.android.auth.is_weak_storage_vault
import org.astermail.android.auth.prepare_password_change_vault
import org.astermail.android.crypto.StorageWriteKey
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultGenerationFloorTest {

    private val random_kek = ByteArray(32) { (it * 5 + 3).toByte() }
    private val other_kek = ByteArray(32) { (it * 11 + 1).toByte() }

    private class Codec : StorageVaultCodec {
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
        override fun encrypt(vault: JSONObject): SealedVault =
            SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=")
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

    private fun vault_at(generation: Long?): JSONObject = JSONObject()
        .put("identity_key", "identity")
        .put("data_kek", encode_b64(random_kek))
        .put("data_kek_random", true)
        .also { if (generation != null) it.put(VAULT_GENERATION_FIELD, generation) }

    private fun updater(
        server: Server,
        floor: Long,
        changed: MutableList<JSONObject> = mutableListOf(),
    ) = VaultUpdater(
        api = server,
        codec = Codec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { vault, _ -> changed.add(vault) },
        now = { "2026-11-25T00:00:00Z" },
        generation_floor = { floor },
    )

    private fun account(id: String = "user-1"): SessionKeyStore = SessionKeyStore(null).also { it.put_user_id(id) }

    private fun from_server(): SealedVault = SealedVault("dmF1bHQ=", "bm9uY2U=", from_server = true)

    @Test
    fun a_vault_below_the_floor_is_rolled_back() {
        assertTrue(is_rolled_back_vault(vault_at(4), 5))
        assertFalse(is_rolled_back_vault(vault_at(5), 5))
        assertFalse(is_rolled_back_vault(vault_at(6), 5))
    }

    @Test
    fun an_untracked_vault_and_a_device_without_a_floor_accept_any_generation() {
        assertFalse(is_rolled_back_vault(vault_at(null), 9))
        assertFalse(is_rolled_back_vault(vault_at(1), 0))
        assertFalse(is_rolled_back_vault(vault_at(null), 0))
    }

    @Test
    fun the_floor_only_rises_and_is_kept_per_account() {
        val store = account()
        assertEquals(0L, store.vault_generation_floor())
        store.raise_vault_generation_floor(5)
        store.raise_vault_generation_floor(3)
        store.raise_vault_generation_floor(0)
        assertEquals(5L, store.vault_generation_floor())
        store.put_user_id("user-2")
        assertEquals(0L, store.vault_generation_floor())
        store.put_user_id("user-1")
        assertEquals(5L, store.vault_generation_floor())
    }

    @Test
    fun the_floor_survives_signing_out() {
        val store = account()
        store.raise_vault_generation_floor(7)
        store.clear()
        store.put_user_id("user-1")
        assertEquals(7L, store.vault_generation_floor())
    }

    @Test
    fun an_update_refuses_a_vault_below_the_floor() = runBlocking {
        val server = Server(vault_at(3))
        val changed = mutableListOf<JSONObject>()
        var edited = false
        val outcome = updater(server, 5, changed).update {
            edited = true
            VaultEdit.CHANGED
        }
        assertEquals(VAULT_ROLLED_BACK, (outcome as VaultUpdateOutcome.Failed).reason)
        assertFalse(edited)
        assertEquals(0, server.puts)
        assertTrue(changed.isEmpty())
    }

    @Test
    fun a_replacement_refuses_a_vault_below_the_floor() = runBlocking {
        val server = Server(vault_at(3))
        var sent = false
        val outcome = updater(server, 5).replace_through(
            edit = { VaultEdit.CHANGED },
            send = { _, _, _ ->
                sent = true
                true
            },
        )
        assertEquals(VAULT_ROLLED_BACK, (outcome as VaultUpdateOutcome.Failed).reason)
        assertFalse(sent)
    }

    @Test
    fun the_latest_vault_is_withheld_when_it_is_below_the_floor() = runBlocking {
        assertNull(updater(Server(vault_at(3)), 5).latest())
        assertEquals(5L, vault_generation(updater(Server(vault_at(5)), 5).latest()?.vault))
    }

    @Test
    fun a_write_stamps_above_the_floor() = runBlocking {
        val server = Server(vault_at(null))
        val outcome = updater(server, 8).update { draft ->
            draft.vault.put("identity_key", "next")
            VaultEdit.CHANGED
        }
        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(9L, vault_generation(server.vault))
    }

    @Test
    fun a_write_without_a_floor_stamps_above_the_server_vault() = runBlocking {
        val server = Server(vault_at(4))
        updater(server, 0).update { draft ->
            draft.vault.put("identity_key", "next")
            VaultEdit.CHANGED
        }
        assertEquals(5L, vault_generation(server.vault))
    }

    @Test
    fun an_accepted_vault_raises_the_floor() {
        val store = account()
        absorb_rotated_vault(store, vault_at(6), from_server())
        assertEquals(6L, store.vault_generation_floor())
        assertArrayEquals(random_kek, store.get_data_kek())
    }

    @Test
    fun a_vault_below_the_floor_never_replaces_the_storage_key() {
        val store = account()
        store.raise_vault_generation_floor(6)
        store.put_data_kek(other_kek.copyOf())
        absorb_rotated_vault(store, vault_at(4), from_server())
        assertArrayEquals(other_kek, store.get_data_kek())
        assertEquals(6L, store.vault_generation_floor())
    }

    @Test
    fun a_password_change_stamps_above_the_floor() {
        val vault = prepare_password_change_vault(
            vault_at(null),
            LocalStorageKeys(null, emptyList()),
            "2026-11-25T00:00:00Z",
            10,
        )
        assertEquals(11L, vault_generation(vault))
    }

    @Test
    fun a_public_constant_storage_key_counts_as_weak_even_when_flagged_random() {
        val constant = StorageWriteKey.public_mail_kek_raws().first()
        val vault = JSONObject()
            .put("data_kek", encode_b64(constant))
            .put("data_kek_random", true)
        assertTrue(is_weak_storage_vault(vault, emptyList()) { decode_b64(it)!! })
        assertFalse(is_weak_storage_vault(vault_at(2), emptyList()) { decode_b64(it)!! })
    }

    @Test
    fun a_public_constant_is_never_selected_as_the_write_key() {
        assertNull(StorageWriteKey.select_write_key(StorageWriteKey.public_mail_kek_raws().first()))
        assertArrayEquals(random_kek, StorageWriteKey.select_write_key(random_kek.copyOf()))
    }

    @Test
    fun the_write_key_refuses_a_public_constant() {
        val store = account()
        store.put_data_kek(StorageWriteKey.public_mail_kek_raws().first())
        assertNull(StorageWriteKey.write_key(store))
    }

    @Test
    fun the_write_key_refuses_the_login_proof() {
        val store = account()
        store.put(random_kek.copyOf())
        store.put_data_kek(random_kek.copyOf())
        assertNull(StorageWriteKey.write_key(store))
    }

    @Test
    fun the_write_key_refuses_a_retired_key() {
        val store = account()
        store.put_data_kek(random_kek.copyOf())
        store.retire_legacy_storage_keys(listOf(StorageWriteKey.retired_key_hash(random_kek)))
        assertNull(StorageWriteKey.write_key(store))
    }

    @Test
    fun the_write_key_keeps_a_random_key() {
        val store = account()
        store.put(other_kek.copyOf())
        store.put_data_kek(random_kek.copyOf())
        assertArrayEquals(random_kek, StorageWriteKey.write_key(store))
    }
}
