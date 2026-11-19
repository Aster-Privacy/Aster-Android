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

package org.astermail.android.mail.ratchet

import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.astermail.android.crypto.storage_rotation.ApiReply
import org.astermail.android.crypto.storage_rotation.LocalStorageKeys
import org.astermail.android.crypto.storage_rotation.ResealApi
import org.astermail.android.crypto.storage_rotation.SealedVault
import org.astermail.android.crypto.storage_rotation.StorageVaultCodec
import org.astermail.android.crypto.storage_rotation.VAULT_CHANGED_CODE
import org.astermail.android.crypto.storage_rotation.VaultUpdater
import org.astermail.android.crypto.storage_rotation.absorb_rotated_vault
import org.astermail.android.crypto.storage_rotation.decode_b64
import org.astermail.android.crypto.storage_rotation.encode_b64
import org.astermail.android.crypto.storage_rotation.json_object
import org.astermail.android.crypto.storage_rotation.str
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PrekeyVaultCodec : StorageVaultCodec {
    override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
        decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
    override fun encrypt(vault: JSONObject): SealedVault =
        SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=")
    override fun password_keys(vault: JSONObject): List<ByteArray> = emptyList()
    override fun generate_key(): ByteArray = ByteArray(32) { 3 }
    override fun fingerprints(vault: JSONObject): List<String> = emptyList()
}

internal class PrekeyVaultServer(initial: JSONObject) : ResealApi {
    var vault: JSONObject = initial
    var puts = 0
    var conflicts = 0
    var on_put: (PrekeyVaultServer) -> Unit = {}

    private fun sealed(): String = encode_b64(vault.toString().toByteArray(Charsets.UTF_8))

    private fun digest(sealed: String): String =
        MessageDigest.getInstance("SHA-256").digest(sealed.toByteArray()).joinToString("") { "%02x".format(it) }

    override suspend fun get(path: String): ApiReply {
        val sealed = sealed()
        return ApiReply(
            200,
            json_object("encrypted_vault" to sealed, "vault_nonce" to "bm9uY2U=", "vault_sha256" to digest(sealed)),
        )
    }

    override suspend fun post(path: String, body: JsonElement): ApiReply = ApiReply(404)

    override suspend fun put(path: String, body: JsonElement): ApiReply {
        on_put(this)
        val obj = body as JsonObject
        if (obj.str("expected_vault_sha256") != digest(sealed())) {
            conflicts += 1
            return ApiReply(409, null, VAULT_CHANGED_CODE)
        }
        puts += 1
        vault = JSONObject(String(decode_b64(obj.str("encrypted_vault"))!!, Charsets.UTF_8))
        return ApiReply(200, json_object("ok" to true))
    }

    fun updater(changed: MutableList<JSONObject> = mutableListOf()) = VaultUpdater(
        api = this,
        codec = PrekeyVaultCodec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { vault, _ -> changed.add(vault) },
        now = { "2026-11-17T00:00:00Z" },
        local_keys = { LocalStorageKeys.NONE },
    )
}

class PrekeySealVaultTest {

    private val ours = ByteArray(32) { (it + 1).toByte() }
    private val theirs = ByteArray(32) { (it + 90).toByte() }

    private fun base_vault(): JSONObject = JSONObject()
        .put("identity_key", "identity")
        .put("data_kek", encode_b64(ByteArray(32) { 7 }))

    @Test
    fun a_device_without_a_seal_key_creates_one_through_the_vault() = runBlocking {
        val server = PrekeyVaultServer(base_vault())

        val adopted = ensure_vault_prekey_seal(server.updater()) { ours.copyOf() }

        assertNotNull(adopted)
        assertArrayEquals(ours, adopted!!.key)
        assertFalse(adopted.migrated)
        assertEquals(1, server.puts)
        assertArrayEquals(ours, vault_prekey_seal_key(server.vault))
        assertEquals("identity", server.vault.getString("identity_key"))
    }

    @Test
    fun a_key_written_by_another_device_during_the_write_is_adopted() = runBlocking {
        val server = PrekeyVaultServer(base_vault())
        var raced = false
        server.on_put = {
            if (!raced) {
                raced = true
                it.vault = base_vault()
                    .put(VAULT_PREKEY_SEAL_FIELD, encode_b64(theirs))
                    .put(VAULT_PREKEY_SEAL_MIGRATED_FIELD, true)
            }
        }

        val adopted = ensure_vault_prekey_seal(server.updater()) { ours.copyOf() }

        assertNotNull(adopted)
        assertArrayEquals(theirs, adopted!!.key)
        assertTrue(adopted.migrated)
        assertEquals(1, server.conflicts)
        assertEquals(0, server.puts)
        assertArrayEquals(theirs, vault_prekey_seal_key(server.vault))
    }

    @Test
    fun an_existing_vault_key_is_adopted_without_a_write() = runBlocking {
        val server = PrekeyVaultServer(base_vault().put(VAULT_PREKEY_SEAL_FIELD, encode_b64(theirs)))

        val adopted = ensure_vault_prekey_seal(server.updater()) { ours.copyOf() }

        assertArrayEquals(theirs, adopted?.key)
        assertEquals(0, server.puts)
    }

    @Test
    fun a_vault_read_on_this_device_carries_the_seal_key_into_local_storage() {
        val store = SessionKeyStore()
        val vault = base_vault()
            .put(VAULT_PREKEY_SEAL_FIELD, encode_b64(theirs))
            .put(VAULT_PREKEY_SEAL_MIGRATED_FIELD, true)

        absorb_rotated_vault(store, vault, SealedVault("ZQ==", "bm9uY2U=", 600_000))

        assertArrayEquals(theirs, store.get_prekey_seal_key())
        assertTrue(store.prekey_seal_migrated())
    }
}
