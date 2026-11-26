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
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultHashMissingTest {

    private fun key(seed: Int): ByteArray = ByteArray(32) { (seed + it).toByte() }

    private val password_key = key(90)

    private inner class FakeCodec : StorageVaultCodec {
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
        override fun encrypt(vault: JSONObject): SealedVault =
            SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=")
        override fun password_keys(vault: JSONObject): List<ByteArray> = listOf(password_key.copyOf())
        override fun generate_key(): ByteArray = key(130)
        override fun fingerprints(vault: JSONObject): List<String> = emptyList()
    }

    private class UnhashedServer(var vault: JSONObject) : ResealApi {
        val put_bodies = mutableListOf<JsonObject>()

        override suspend fun get(path: String): ApiReply = ApiReply(
            200,
            json_object(
                "encrypted_vault" to encode_b64(vault.toString().toByteArray(Charsets.UTF_8)),
                "vault_nonce" to "bm9uY2U=",
            ),
        )

        override suspend fun post(path: String, body: JsonElement): ApiReply = ApiReply(404)

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            val obj = body as JsonObject
            put_bodies.add(obj)
            vault = JSONObject(String(decode_b64(obj.str("encrypted_vault"))!!, Charsets.UTF_8))
            return ApiReply(200, json_object("ok" to true))
        }
    }

    private fun weak_vault(): JSONObject =
        JSONObject().put("identity_key", "identity").put("data_kek", encode_b64(password_key))

    private fun updater(server: UnhashedServer) = VaultUpdater(
        api = server,
        codec = FakeCodec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { _, _ -> },
    )

    private fun rotator(server: UnhashedServer) = StorageVaultRotator(
        api = server,
        codec = FakeCodec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { _, _ -> },
    )

    @After
    fun tear_down() {
        StorageKeyPendingNotice.reset()
    }

    @Test
    fun a_vault_without_a_hash_still_loads() = runBlocking {
        val snapshot = updater(UnhashedServer(weak_vault())).fetch_snapshot()
        assertNull(snapshot!!.vault_sha256)
    }

    @Test
    fun other_changes_still_save_without_a_hash() = runBlocking {
        val server = UnhashedServer(weak_vault())
        val outcome = updater(server).update { draft ->
            draft.vault.put("signature", "kept")
            VaultEdit.CHANGED
        }
        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(1, server.put_bodies.size)
        assertFalse(server.put_bodies[0].containsKey("expected_vault_sha256"))
        assertEquals("kept", server.vault.getString("signature"))
        assertEquals(encode_b64(password_key), server.vault.getString("data_kek"))
    }

    @Test
    fun the_storage_key_is_not_replaced_without_a_hash() = runBlocking {
        val server = UnhashedServer(weak_vault())
        val outcome = rotator(server).ensure_random_storage_key()
        assertTrue(is_storage_key_pending(outcome))
        assertTrue(server.put_bodies.isEmpty())
        assertEquals(encode_b64(password_key), server.vault.getString("data_kek"))
    }

    @Test
    fun a_current_storage_key_is_not_reported_as_pending_without_a_hash() = runBlocking {
        val server = UnhashedServer(weak_vault().put("data_kek", encode_b64(key(7))).put("data_kek_random", true))
        val outcome = rotator(server).ensure_random_storage_key()
        assertTrue(outcome is StorageVaultOutcome.Current)
        assertTrue(server.put_bodies.isEmpty())
    }

    @Test
    fun the_work_factor_waits_for_a_hash() = runBlocking {
        val server = UnhashedServer(weak_vault())
        assertEquals(VaultWorkFactor.SKIPPED, updater(server).raise_work_factor())
        assertTrue(server.put_bodies.isEmpty())
    }

    @Test
    fun a_backup_refresh_omits_a_missing_hash() {
        val built = json_object("encrypted_vault_backup" to "abc")
        assertFalse(refresh_backup_body(built, null).containsKey("expected_vault_sha256"))
        assertEquals(JsonPrimitive("sha"), refresh_backup_body(built, "sha")["expected_vault_sha256"])
    }

    @Test
    fun a_pending_storage_key_retries_on_the_longer_schedule() {
        val pending = ResealRunResult(ResealRunStatus.INTERRUPTED, StepCounts(), VAULT_CAS_UNSUPPORTED)
        val delays = (0..4).map { storage_rekey_next_run(pending, it)?.delay_secs }
        assertEquals(listOf(60L, 300L, 900L, 1800L, null), delays)
    }

    @Test
    fun the_notice_shows_once_until_the_key_is_updated() {
        val pending = StorageVaultOutcome.Skipped(VAULT_CAS_UNSUPPORTED)
        StorageKeyPendingNotice.observe(pending)
        assertTrue(StorageKeyPendingNotice.pending.value)
        StorageKeyPendingNotice.consume()
        StorageKeyPendingNotice.observe(pending)
        assertFalse(StorageKeyPendingNotice.pending.value)
        StorageKeyPendingNotice.observe(StorageVaultOutcome.Skipped("locked"))
        assertFalse(StorageKeyPendingNotice.pending.value)
        StorageKeyPendingNotice.observe(StorageVaultOutcome.Current(JSONObject()))
        StorageKeyPendingNotice.observe(pending)
        assertTrue(StorageKeyPendingNotice.pending.value)
    }
}
