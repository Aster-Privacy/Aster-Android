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
        var refuse_level2 = false
        val put_iterations = mutableListOf<Int>()
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
            val sent = obj.long("vault_kdf_iterations")?.toInt() ?: VaultKdf.DEFAULT_ITERATIONS
            put_iterations.add(sent)
            if (refuse_level2 && sent == VaultKdf.LEVEL2_ITERATIONS) {
                return ApiReply(409, null, VAULT_LEVEL2_NOT_ADMITTED_CODE)
            }
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

    private class OrderedCodec(val events: MutableList<String>) : StorageVaultCodec {
        private val inner = Codec()
        override suspend fun before_write() {
            events.add("refresh")
        }
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            inner.decrypt(encrypted_vault, vault_nonce)
        override fun encrypt(vault: JSONObject): SealedVault {
            events.add("seal")
            return inner.encrypt(vault)
        }
        override fun password_keys(vault: JSONObject): List<ByteArray> = emptyList()
        override fun generate_key(): ByteArray = inner.generate_key()
        override fun fingerprints(vault: JSONObject): List<String> = emptyList()
    }

    @Test
    fun every_vault_write_refreshes_the_bound_write_switch_before_sealing() = runBlocking {
        val server = Server(rotated_vault())
        val events = mutableListOf<String>()
        val updater = VaultUpdater(
            api = server,
            codec = OrderedCodec(events),
            user_id = { "user-1" },
            lock = Mutex(),
            on_vault_changed = { _, _ -> },
            now = { now },
        )
        updater.update { draft ->
            draft.vault.put("identity_key", "next")
            VaultEdit.CHANGED
        }
        updater.replace_through(
            edit = { draft ->
                draft.vault.put("identity_key", "replaced")
                VaultEdit.CHANGED
            },
            send = { _, _, _ -> true },
        )
        assertEquals(listOf("refresh", "seal", "refresh", "seal"), events)
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
    fun a_retired_key_from_the_server_is_not_carried_into_the_next_vault() {
        val retired_key = ByteArray(32) { 13 }
        val retired_kek = encode_b64(retired_key)
        val server = rotated_vault()
        server.put(
            "legacy_keks",
            JSONArray()
                .put(JSONObject().put("k", retired_kek).put("added_at", now))
                .put(JSONObject().put("k", older_kek).put("added_at", now)),
        )
        val next = JSONObject(server.toString())
        next.remove("legacy_keks")
        val hashes = listOf(org.astermail.android.storage.RetiredStorageKeys.hash(retired_key))

        preserve_vault_fields(server, next, LocalStorageKeys(random_kek, listOf(retired_kek), hashes), false, now)

        assertEquals(listOf(older_kek), legacy_values(next))
    }

    @Test
    fun local_storage_keys_carry_the_retired_hashes_of_the_device() {
        val retired_key = ByteArray(32) { 13 }
        val store = org.astermail.android.storage.SessionKeyStore(null)
        store.retire_legacy_storage_keys(listOf(org.astermail.android.storage.RetiredStorageKeys.hash(retired_key)))
        store.put_legacy_keks(listOf(encode_b64(retired_key), older_kek))

        val local = local_storage_keys(store)

        assertEquals(listOf(older_kek), local.legacy_keks)
        assertEquals(store.retired_storage_key_hashes(), local.retired_hashes.toList())
        val server = rotated_vault().put(
            "legacy_keks",
            JSONArray().put(JSONObject().put("k", encode_b64(retired_key)).put("added_at", now)),
        )
        val next = JSONObject(server.toString())
        preserve_vault_fields(server, next, local, false, now)
        assertEquals(listOf(older_kek), legacy_values(next))
    }

    @Test
    fun a_vault_write_keeps_lane_replay_records_from_both_sides() {
        val server = JSONObject().put("identity_key", "identity").put(
            LANE_REPLAY_FIELD,
            JSONObject()
                .put("a@aster.cx", record(5, "k1" to 7L))
                .put("b@aster.cx", record(0, "k3" to 9L)),
        )
        val next = JSONObject().put("identity_key", "identity").put(
            LANE_REPLAY_FIELD,
            JSONObject().put("a@aster.cx", record(3, "k2" to 8L)),
        )

        preserve_vault_fields(server, next, LocalStorageKeys(null, emptyList()), false, now)

        val merged = next.getJSONObject(LANE_REPLAY_FIELD)
        val first = merged.getJSONObject("a@aster.cx")
        assertEquals(5L, first.getLong("f"))
        assertEquals(setOf("k1", "k2"), keys_of(first))
        assertEquals(setOf("k3"), keys_of(merged.getJSONObject("b@aster.cx")))
    }

    @Test
    fun a_vault_write_without_lane_replay_records_adds_none() {
        val server = JSONObject().put("identity_key", "identity")
        val next = JSONObject(server.toString())

        preserve_vault_fields(server, next, LocalStorageKeys(null, emptyList()), false, now)

        assertFalse(next.has(LANE_REPLAY_FIELD))
    }

    private fun record(floor: Long, vararg entries: Pair<String, Long>): JSONObject = JSONObject()
        .put("f", floor)
        .put(
            "e",
            JSONArray().apply {
                entries.forEach { (k, t) -> put(JSONObject().put("k", k).put("m", "m_$k").put("t", t)) }
            },
        )

    private fun keys_of(record: JSONObject): Set<String> {
        val entries = record.getJSONArray("e")
        return (0 until entries.length()).map { entries.getJSONObject(it).getString("k") }.toSet()
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

    @Test
    fun a_refused_write_never_reseals_a_level2_vault_at_the_lower_work_factor() = runBlocking {
        val server = Server(rotated_vault(), stored_iterations = VaultKdf.LEVEL2_ITERATIONS)
        server.refuse_level2 = true

        val outcome = iteration_updater(server).update { draft ->
            draft.vault.put("recovery_codes", JSONArray().put("rotated"))
            VaultEdit.CHANGED
        }

        assertTrue(outcome is VaultUpdateOutcome.Failed)
        assertEquals(listOf(VaultKdf.LEVEL2_ITERATIONS), server.put_iterations)
        assertEquals(0, server.puts)
    }

    private fun retired_hash(fill: Int): String =
        org.astermail.android.storage.RetiredStorageKeys.hash(ByteArray(32) { fill.toByte() })

    @Test
    fun a_device_retirement_is_written_into_the_next_vault() {
        val server = rotated_vault()
        val next = JSONObject(server.toString())

        preserve_vault_fields(server, next, LocalStorageKeys(random_kek, emptyList(), listOf(retired_hash(13))), false, now)

        assertEquals(listOf(retired_hash(13)), vault_retired_hashes(next))
    }

    @Test
    fun the_retirement_marker_survives_a_write_that_drops_it() {
        val server = rotated_vault().put(RETIRED_STORAGE_KEYS_FIELD, JSONArray().put(retired_hash(13)))
        val next = JSONObject(server.toString()).put(RETIRED_STORAGE_KEYS_FIELD, JSONArray().put(retired_hash(14)))
        val dropped = JSONObject(server.toString())
        dropped.remove(RETIRED_STORAGE_KEYS_FIELD)

        preserve_vault_fields(server, next, LocalStorageKeys.NONE, false, now)
        preserve_vault_fields(server, dropped, LocalStorageKeys.NONE, false, now)

        assertEquals(setOf(retired_hash(13), retired_hash(14)), vault_retired_hashes(next).toSet())
        assertEquals(listOf(retired_hash(13)), vault_retired_hashes(dropped))
    }

    @Test
    fun a_fresh_sign_in_keeps_the_keys_retired_by_another_session() {
        val retired_key = ByteArray(32) { 13 }
        val store = org.astermail.android.storage.SessionKeyStore(null)
        store.put_legacy_keks(listOf(encode_b64(retired_key), older_kek))
        val vault = rotated_vault().put(RETIRED_STORAGE_KEYS_FIELD, JSONArray().put(retired_hash(13)))

        absorb_rotated_vault(store, vault, SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U="))

        assertTrue(store.legacy_storage_keys_retired())
        assertEquals(listOf(retired_hash(13)), store.retired_storage_key_hashes())
        val kept = store.get_legacy_keks().orEmpty()
        assertFalse(encode_b64(retired_key) in kept)
        assertTrue(older_kek in kept)
        assertEquals(emptyList<ByteArray>(), org.astermail.android.crypto.StorageWriteKey.sweep_keys(store))
        assertFalse(vault_lacks_retired_marker(vault, store.retired_storage_key_hashes()))
    }

    @Test
    fun malformed_retirement_entries_are_ignored() {
        val store = org.astermail.android.storage.SessionKeyStore(null)
        val vault = rotated_vault().put(
            RETIRED_STORAGE_KEYS_FIELD,
            JSONArray().put("short").put(7).put(encode_b64(ByteArray(16))),
        )

        assertEquals(emptyList<String>(), vault_retired_hashes(vault))
        assertFalse(absorb_retired_marker(store, vault))
        assertFalse(store.legacy_storage_keys_retired())
        assertTrue(vault_lacks_retired_marker(vault, listOf(retired_hash(13))))
    }
}
