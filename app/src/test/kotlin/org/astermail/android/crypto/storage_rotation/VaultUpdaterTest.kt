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
import org.astermail.android.api.ApiError
import org.astermail.android.crypto.VaultKdf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultUpdaterTest {

    private val now = "2026-11-06T00:00:00Z"
    private val random_kek = encode_b64(ByteArray(32) { 7 })
    private val older_kek = encode_b64(ByteArray(32) { 9 })
    private val local_kek = encode_b64(ByteArray(32) { 11 })

    private class Codec : StorageVaultCodec {
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
        override fun encrypt(vault: JSONObject): SealedVault =
            SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=")
        override fun password_keys(vault: JSONObject): List<ByteArray> = emptyList()
        override fun generate_key(): ByteArray = ByteArray(32) { 3 }
        override fun fingerprints(vault: JSONObject): List<String> = emptyList()
    }

    private class Server(initial: JSONObject, val stored_iterations: Int? = null) : ResealApi {
        var vault: JSONObject = initial
        var puts = 0
        var conflicts = 0
        var on_put: (Server) -> Unit = {}

        fun sealed(): String = encode_b64(vault.toString().toByteArray(Charsets.UTF_8))

        fun digest(sealed: String): String =
            MessageDigest.getInstance("SHA-256").digest(sealed.toByteArray()).joinToString("") { "%02x".format(it) }

        override suspend fun get(path: String): ApiReply {
            val sealed = sealed()
            val fields = mutableListOf<Pair<String, Any?>>(
                "encrypted_vault" to sealed,
                "vault_nonce" to "bm9uY2U=",
                "vault_sha256" to digest(sealed),
            )
            stored_iterations?.let { fields.add("vault_kdf_iterations" to it) }
            return ApiReply(200, json_object(*fields.toTypedArray()))
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
    }

    private fun rotated_vault(): JSONObject = JSONObject()
        .put("identity_key", "identity")
        .put("data_kek", random_kek)
        .put("data_kek_random", true)
        .put("storage_rekey", JSONObject().put("state", "pending"))
        .put("legacy_keks", JSONArray().put(JSONObject().put("k", older_kek).put("added_at", "x")))
        .put("future_field", "kept")

    private fun updater(
        server: Server,
        local: LocalStorageKeys = LocalStorageKeys.NONE,
        changed: MutableList<JSONObject> = mutableListOf(),
    ) = VaultUpdater(
        api = server,
        codec = Codec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { vault, _ -> changed.add(vault) },
        now = { now },
        local_keys = { local },
    )

    private fun legacy_values(vault: JSONObject): List<String> {
        val array = vault.optJSONArray("legacy_keks") ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it).getString("k") }
    }

    @Test
    fun an_old_format_vault_from_another_device_keeps_the_server_random_storage_key() = runBlocking {
        val server = Server(rotated_vault())
        val old_format = JSONObject()
            .put("identity_key", "identity-from-other-device")
            .put("recovery_codes", JSONArray().put("code"))

        val outcome = updater(server).update { draft ->
            draft.vault.keys().asSequence().toList().forEach { draft.vault.remove(it) }
            old_format.keys().forEach { draft.vault.put(it, old_format.get(it)) }
            VaultEdit.CHANGED
        }

        assertTrue(outcome is VaultUpdateOutcome.Written)
        val stored = server.vault
        assertEquals(random_kek, stored.getString("data_kek"))
        assertEquals(true, stored.get("data_kek_random"))
        assertEquals("pending", stored.getJSONObject("storage_rekey").getString("state"))
        assertEquals(listOf(older_kek), legacy_values(stored))
        assertEquals("kept", stored.getString("future_field"))
        assertEquals("identity-from-other-device", stored.getString("identity_key"))
        assertEquals("code", stored.getJSONArray("recovery_codes").getString(0))
    }

    @Test
    fun an_edit_that_swaps_the_storage_key_moves_its_key_to_legacy_and_keeps_the_server_key() = runBlocking {
        val server = Server(rotated_vault())
        val stale_kek = encode_b64(ByteArray(32) { 21 })

        updater(server).update { draft ->
            draft.vault.put("data_kek", stale_kek)
            draft.vault.remove("data_kek_random")
            VaultEdit.CHANGED
        }

        assertEquals(random_kek, server.vault.getString("data_kek"))
        assertEquals(true, server.vault.get("data_kek_random"))
        assertTrue(stale_kek in legacy_values(server.vault))
        assertTrue(older_kek in legacy_values(server.vault))
    }

    @Test
    fun a_local_storage_key_missing_from_the_server_lands_in_legacy_keks() {
        val server = JSONObject().put("identity_key", "identity")
        val next = JSONObject(server.toString()).put("recovery_codes", JSONArray())

        preserve_vault_fields(server, next, LocalStorageKeys(local_kek, listOf(older_kek)), false, now)

        assertFalse(next.has("data_kek"))
        assertEquals(listOf(local_kek, older_kek), legacy_values(next))
    }

    @Test
    fun the_current_storage_key_is_never_duplicated_into_legacy_keks() {
        val server = rotated_vault()
        val next = JSONObject(server.toString())

        preserve_vault_fields(server, next, LocalStorageKeys(random_kek, listOf(random_kek, older_kek)), false, now)

        assertEquals(random_kek, next.getString("data_kek"))
        assertEquals(listOf(older_kek), legacy_values(next))
    }

    @Test
    fun a_conflict_refetches_and_reapplies_the_edit_on_the_latest_vault() = runBlocking {
        val server = Server(rotated_vault())
        var raced = false
        server.on_put = { s ->
            if (!raced) {
                raced = true
                s.vault = JSONObject(s.vault.toString()).put("other_device", "written")
            }
        }
        var edits = 0

        val outcome = updater(server).update { draft ->
            edits += 1
            draft.vault.put("recovery_codes", JSONArray().put("new"))
            VaultEdit.CHANGED
        }

        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(2, edits)
        assertEquals(1, server.conflicts)
        assertEquals(1, server.puts)
        assertEquals("written", server.vault.getString("other_device"))
        assertEquals("new", server.vault.getJSONArray("recovery_codes").getString(0))
        assertEquals(random_kek, server.vault.getString("data_kek"))
    }

    @Test
    fun endless_conflicts_stop_after_the_retry_bound() = runBlocking {
        val server = Server(rotated_vault())
        var counter = 0
        server.on_put = { s -> counter += 1; s.vault = JSONObject(s.vault.toString()).put("tick", counter) }

        val outcome = updater(server).update { VaultEdit.CHANGED }

        assertTrue(outcome is VaultUpdateOutcome.Failed)
        assertEquals("conflict_retries_exhausted", (outcome as VaultUpdateOutcome.Failed).reason)
        assertEquals(MAX_VAULT_CONFLICT_RETRIES + 1, server.conflicts)
        assertEquals(0, server.puts)
    }

    @Test
    fun abort_and_unchanged_never_write() = runBlocking {
        val server = Server(rotated_vault())
        val changed = mutableListOf<JSONObject>()

        assertTrue(updater(server).update { VaultEdit.ABORT } is VaultUpdateOutcome.Aborted)
        assertTrue(updater(server, changed = changed).update { VaultEdit.UNCHANGED } is VaultUpdateOutcome.Unchanged)

        assertEquals(0, server.puts)
        assertEquals(1, changed.size)
        assertEquals(random_kek, changed.single().getString("data_kek"))
    }

    @Test
    fun replace_through_sends_the_latest_vault_with_storage_keys_preserved() = runBlocking {
        val server = Server(rotated_vault())
        var sent: JSONObject? = null

        val outcome = updater(server, LocalStorageKeys(local_kek, emptyList())).replace_through(
            edit = { draft ->
                draft.vault.remove("data_kek")
                draft.vault.put("recovery_codes", JSONArray().put("rotated"))
                VaultEdit.CHANGED
            },
            send = { vault, _, _ ->
                sent = vault
                true
            },
        )

        assertTrue(outcome is VaultUpdateOutcome.Written)
        val written = sent!!
        assertEquals(random_kek, written.getString("data_kek"))
        assertEquals("rotated", written.getJSONArray("recovery_codes").getString(0))
        assertTrue(local_kek in legacy_values(written))
        assertEquals("kept", written.getString("future_field"))
    }

    private class SingleUseStepUpBackend(val level2_admitted: Boolean) {
        private var token_used = false
        var calls = 0
        val sent_iterations = mutableListOf<Int>()

        fun backup(token: String, sealed: SealedVault): Boolean {
            calls += 1
            sent_iterations.add(sealed.kdf_iterations)
            if (token != "step-up" || token_used) throw ApiError.StepUpRequired()
            token_used = true
            if (sealed.kdf_iterations == VaultKdf.LEVEL2_ITERATIONS && !level2_admitted) {
                throw ApiError.Conflict("not admitted", VAULT_LEVEL2_NOT_ADMITTED_CODE)
            }
            return true
        }
    }

    private class IterationCodec : StorageVaultCodec {
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
        override fun encrypt(vault: JSONObject): SealedVault = encrypt_at(vault, VaultKdf.DEFAULT_ITERATIONS)
        override fun encrypt_at(vault: JSONObject, iterations: Int): SealedVault =
            SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=", iterations)
        override fun password_keys(vault: JSONObject): List<ByteArray> = emptyList()
        override fun generate_key(): ByteArray = ByteArray(32) { 3 }
        override fun fingerprints(vault: JSONObject): List<String> = emptyList()
    }

    private fun iteration_updater(server: Server) = VaultUpdater(
        api = server,
        codec = IterationCodec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { _, _ -> },
        now = { now },
        local_keys = { LocalStorageKeys.NONE },
    )

    private suspend fun rotate_codes(updater: VaultUpdater, backend: SingleUseStepUpBackend): VaultUpdateOutcome =
        updater.replace_through(
            edit = { draft ->
                draft.vault.put("recovery_codes", JSONArray().put("rotated"))
                VaultEdit.CHANGED
            },
            send = { _, sealed, _ -> backend.backup("step-up", sealed) },
        )

    @Test
    fun rotating_codes_on_an_account_outside_level2_spends_the_step_up_token_once_and_writes() = runBlocking {
        val backend = SingleUseStepUpBackend(level2_admitted = false)

        val outcome = rotate_codes(iteration_updater(Server(rotated_vault())), backend)

        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(1, backend.calls)
        assertEquals(listOf(VaultKdf.DEFAULT_ITERATIONS), backend.sent_iterations)
    }

    @Test
    fun rotating_codes_keeps_a_level2_vault_at_level2() = runBlocking {
        val backend = SingleUseStepUpBackend(level2_admitted = true)
        val server = Server(rotated_vault(), stored_iterations = VaultKdf.LEVEL2_ITERATIONS)

        val outcome = rotate_codes(iteration_updater(server), backend)

        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(listOf(VaultKdf.LEVEL2_ITERATIONS), backend.sent_iterations)
    }

    @Test
    fun a_refused_rotation_never_resends_the_step_up_token() = runBlocking {
        val backend = SingleUseStepUpBackend(level2_admitted = false)
        val server = Server(rotated_vault(), stored_iterations = VaultKdf.LEVEL2_ITERATIONS)

        try {
            rotate_codes(iteration_updater(server), backend)
            fail("expected the refusal to reach the caller")
        } catch (error: ApiError.Conflict) {
            assertTrue(is_level2_not_admitted(error))
        }
        assertEquals(1, backend.calls)
    }
}
