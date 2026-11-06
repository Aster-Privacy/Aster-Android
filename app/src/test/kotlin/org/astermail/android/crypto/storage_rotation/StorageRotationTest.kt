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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.auth.STORAGE_REKEY_DONE
import org.astermail.android.auth.STORAGE_REKEY_PENDING
import org.astermail.android.auth.is_weak_storage_vault
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRotationTest {

    private fun key(seed: Int): ByteArray = ByteArray(32) { (seed + it).toByte() }

    private val v1_key = key(10)
    private val v2_key = key(50)
    private val key_material = key(90)
    private val fresh_key = key(130)

    private fun password_keys(): List<ByteArray> = listOf(v1_key.copyOf(), v2_key.copyOf(), key_material.copyOf())

    private fun decode(value: String): ByteArray = decode_b64(value)!!

    private inner class FakeCodec : StorageVaultCodec {
        var generated = 0
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
        override fun encrypt(vault: JSONObject): SealedVault =
            SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=")
        override fun password_keys(vault: JSONObject): List<ByteArray> = this@StorageRotationTest.password_keys()
        override fun generate_key(): ByteArray {
            generated += 1
            return fresh_key.copyOf()
        }
        override fun fingerprints(vault: JSONObject): List<String> = emptyList()
    }

    private class VaultServer(initial: JSONObject) : ResealApi {
        var vault: JSONObject = initial
        var puts = 0
        var before_put: (() -> Unit)? = null

        fun sealed(): String = encode_b64(vault.toString().toByteArray(Charsets.UTF_8))

        fun digest(sealed: String): String =
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
            before_put?.invoke()
            before_put = null
            val obj = body as JsonObject
            if (obj.str("expected_vault_sha256") != digest(sealed())) return ApiReply(409, null, VAULT_CHANGED_CODE)
            puts += 1
            vault = JSONObject(String(decode_b64(obj.str("encrypted_vault"))!!, Charsets.UTF_8))
            return ApiReply(200, json_object("ok" to true))
        }
    }

    private fun weak_vault(data_kek: ByteArray?): JSONObject {
        val vault = JSONObject()
            .put("identity_key", "identity")
            .put("unknown_field", "kept")
            .put("legacy_keks", JSONArray().put(JSONObject().put("k", encode_b64(key(200))).put("added_at", "x")))
        if (data_kek != null) vault.put("data_kek", encode_b64(data_kek))
        return vault
    }

    private fun rotator(
        server: VaultServer,
        codec: FakeCodec = FakeCodec(),
        adopted: MutableList<JSONObject> = mutableListOf(),
    ) = StorageVaultRotator(
        api = server,
        codec = codec,
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { vault, _ -> adopted.add(vault) },
        now = { "2026-11-06T00:00:00Z" },
    )

    @Test
    fun weak_vault_detection_covers_every_password_derived_key() {
        assertTrue(is_weak_storage_vault(weak_vault(null), password_keys(), ::decode))
        assertTrue(is_weak_storage_vault(weak_vault(v1_key), password_keys(), ::decode))
        assertTrue(is_weak_storage_vault(weak_vault(v2_key), password_keys(), ::decode))
        assertTrue(is_weak_storage_vault(weak_vault(key_material), password_keys(), ::decode))
        assertFalse(is_weak_storage_vault(weak_vault(key(7)), password_keys(), ::decode))
        assertFalse(is_weak_storage_vault(weak_vault(v1_key).put("data_kek_random", true), password_keys(), ::decode))
    }

    @Test
    fun storage_password_keys_derives_v1_v2_and_key_material_per_salt() {
        val salts = listOf(ByteArray(16) { 1 }, ByteArray(0), ByteArray(16) { 2 })
        val keys = storage_password_keys(
            ByteArray(4),
            salts,
            derive_v1 = { v1_key.copyOf() },
            derive_v2 = { v2_key.copyOf() },
            derive_key_material = { _, salt -> ByteArray(32) { salt[0] } },
        )
        assertEquals(4, keys.size)
        assertArrayEquals(v1_key, keys[0])
        assertArrayEquals(v2_key, keys[1])
        assertEquals(1.toByte(), keys[2][0])
        assertEquals(2.toByte(), keys[3][0])
        val none = storage_password_keys(
            ByteArray(4),
            emptyList(),
            derive_v1 = { ByteArray(16) },
            derive_v2 = { throw IllegalStateException() },
        )
        assertTrue(none.isEmpty())
    }

    @Test
    fun weak_vault_rotates_to_random_key_and_keeps_old_keys() = runBlocking {
        val server = VaultServer(weak_vault(key_material))
        val outcome = rotator(server).ensure_random_storage_key()
        assertTrue(outcome is StorageVaultOutcome.Rotated)
        assertEquals(1, server.puts)
        val vault = server.vault
        assertEquals(encode_b64(fresh_key), vault.getString("data_kek"))
        assertEquals(true, vault.opt("data_kek_random"))
        assertEquals("kept", vault.getString("unknown_field"))
        assertEquals("identity", vault.getString("identity_key"))
        val rekey = vault.getJSONObject("storage_rekey")
        assertEquals(STORAGE_REKEY_PENDING, rekey.getString("state"))
        val prior_array = rekey.getJSONArray("prior_keys")
        val prior = (0 until prior_array.length()).map { prior_array.getString(it) }
        assertTrue(encode_b64(key_material) in prior)
        assertTrue(encode_b64(v1_key) in prior)
        assertTrue(encode_b64(v2_key) in prior)
        val legacy_array = vault.getJSONArray("legacy_keks")
        val legacy = (0 until legacy_array.length()).map { legacy_array.getJSONObject(it).getString("k") }
        assertTrue(encode_b64(key_material) in legacy)
        assertTrue(encode_b64(key(200)) in legacy)
        assertFalse(encode_b64(fresh_key) in legacy)
        for (old in password_keys()) {
            assertFalse(is_weak_storage_vault(vault, listOf(old), ::decode))
            assertFalse(MessageDigest.isEqual(old, decode(vault.getString("data_kek"))))
        }
    }

    @Test
    fun random_vault_is_left_untouched() = runBlocking {
        val vault = weak_vault(key(7)).put("data_kek_random", true)
        val before = vault.toString()
        val server = VaultServer(vault)
        val codec = FakeCodec()
        val outcome = rotator(server, codec).ensure_random_storage_key()
        assertTrue(outcome is StorageVaultOutcome.Current)
        assertEquals(0, server.puts)
        assertEquals(0, codec.generated)
        assertEquals(before, server.vault.toString())
    }

    @Test
    fun conflicting_write_adopts_the_newer_random_key() = runBlocking {
        val server = VaultServer(weak_vault(v1_key))
        val other = weak_vault(key(160)).put("data_kek_random", true)
            .put("storage_rekey", JSONObject().put("state", STORAGE_REKEY_PENDING))
        server.before_put = { server.vault = other }
        val adopted = mutableListOf<JSONObject>()
        val outcome = rotator(server, adopted = adopted).ensure_random_storage_key()
        assertTrue(outcome is StorageVaultOutcome.Adopted)
        assertEquals(0, server.puts)
        assertEquals(encode_b64(key(160)), (outcome as StorageVaultOutcome.Adopted).vault.getString("data_kek"))
        assertEquals(encode_b64(key(160)), adopted.last().getString("data_kek"))
    }

    @Test
    fun finish_marks_done_once_and_detects_a_newer_key() = runBlocking {
        val server = VaultServer(weak_vault(v1_key))
        val rot = rotator(server)
        rot.ensure_random_storage_key()
        val expected = server.vault.getString("data_kek")
        assertTrue(rot.server_storage_key_matches(expected))
        assertEquals(StorageRekeyFinish.DONE, rot.mark_storage_rekey_done(expected))
        assertEquals(STORAGE_REKEY_DONE, server.vault.getJSONObject("storage_rekey").getString("state"))
        val puts = server.puts
        assertEquals(StorageRekeyFinish.DONE, rot.mark_storage_rekey_done(expected))
        assertTrue(rot.ensure_random_storage_key() is StorageVaultOutcome.Current)
        assertEquals(puts, server.puts)
        assertEquals(StorageRekeyFinish.SUPERSEDED, rot.mark_storage_rekey_done(encode_b64(key(3))))
        assertFalse(rot.server_storage_key_matches(encode_b64(key(3))))
    }

    private fun pending_view(): VaultKeyView = VaultKeyView(
        data_kek = encode_b64(fresh_key),
        data_kek_random = true,
        storage_rekey_state = STORAGE_REKEY_PENDING,
        prior_keys = listOf(encode_b64(v1_key), encode_b64(v2_key), encode_b64(key_material)),
        legacy_keks = emptyList(),
    )

    private class EscrowServer : ResealApi {
        val rows = linkedMapOf<String, Pair<String, String>>()
        var upserts = 0
        val deleted = mutableListOf<String>()

        override suspend fun get(path: String): ApiReply {
            val entries = JsonArray(rows.map { (id, sealed) ->
                json_object("message_id" to id, "encrypted_plaintext" to sealed.first, "plaintext_nonce" to sealed.second)
            })
            return ApiReply(200, json_object("entries" to entries, "next_cursor" to JsonNull))
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply {
            val obj = body as JsonObject
            if (path == ESCROW_DELETE_PATH) {
                obj["message_ids"].as_array().forEach { id ->
                    val value = (id as JsonPrimitive).content
                    deleted.add(value)
                    rows.remove(value)
                }
                return ApiReply(200, json_object("ok" to true))
            }
            upserts += 1
            rows[obj.str("message_id")!!] = obj.str("encrypted_plaintext")!! to obj.str("plaintext_nonce")!!
            return ApiReply(200, json_object("ok" to true))
        }

        override suspend fun put(path: String, body: JsonElement): ApiReply = ApiReply(404)
    }

    private fun seal_escrow(server: EscrowServer, raw: ByteArray, id: String, text: String) {
        val sealed = gcm_seal(derive_escrow_key(raw), text.toByteArray(), escrow_aad_modes(id).first())
        server.rows[id] = sealed.ciphertext to sealed.nonce
    }

    private fun opens(raw: ByteArray, id: String, sealed: Pair<String, String>): String? {
        val escrow_key = derive_escrow_key(raw)
        for (aad in escrow_aad_modes(id)) {
            gcm_open(escrow_key, decode(sealed.first), decode(sealed.second), aad)?.let { return String(it) }
        }
        return null
    }

    @Test
    fun escrow_reseals_under_new_key_and_rerun_writes_nothing() = runBlocking {
        val server = EscrowServer()
        seal_escrow(server, v1_key, "m1", "one")
        seal_escrow(server, v2_key, "v3:m2", "two")
        seal_escrow(server, key_material, "m3", "three")
        seal_escrow(server, fresh_key, "m4", "four")
        seal_escrow(server, key(240), "m5", "lost")
        val store = MemoryProgressStore()
        var finished = 0
        val first = run_storage_reseal(
            view = pending_view(),
            api = server,
            store = store,
            finish = {
                finished += 1
                StorageRekeyFinish.DONE
            },
            still_current = { true },
            sleep = {},
            steps = listOf(escrow_step),
        )
        assertEquals(ResealRunStatus.DONE, first.status)
        assertEquals(1, finished)
        assertEquals(3, server.upserts)
        assertEquals(listOf("m5"), server.deleted)
        assertNull(store.snapshot())
        assertEquals("one", opens(fresh_key, "m1", server.rows.getValue("m1")))
        assertEquals("two", opens(fresh_key, "v3:m2", server.rows.getValue("v3:m2")))
        assertEquals("three", opens(fresh_key, "m3", server.rows.getValue("m3")))
        for ((id, sealed) in server.rows) {
            for (old in listOf(v1_key, v2_key, key_material)) assertNull(opens(old, id, sealed))
        }
        val second = run_storage_reseal(
            view = pending_view(),
            api = server,
            store = store,
            finish = { StorageRekeyFinish.DONE },
            still_current = { true },
            sleep = {},
            steps = listOf(escrow_step),
        )
        assertEquals(ResealRunStatus.DONE, second.status)
        assertEquals(3, server.upserts)
        assertEquals(4, second.counts.current)
        val third = run_storage_reseal(
            view = pending_view().copy(storage_rekey_state = STORAGE_REKEY_DONE),
            api = server,
            store = store,
            finish = { StorageRekeyFinish.DONE },
            sleep = {},
            steps = listOf(escrow_step),
        )
        assertEquals(ResealRunStatus.NOT_NEEDED, third.status)
    }

    @Test
    fun unreadable_escrow_is_kept_when_key_is_no_longer_current() = runBlocking {
        val server = EscrowServer()
        seal_escrow(server, key(240), "m5", "lost")
        val result = run_storage_reseal(
            view = pending_view(),
            api = server,
            store = MemoryProgressStore(),
            finish = { StorageRekeyFinish.SUPERSEDED },
            still_current = { false },
            sleep = {},
            steps = listOf(escrow_step),
        )
        assertEquals(ResealRunStatus.SUPERSEDED, result.status)
        assertTrue(server.deleted.isEmpty())
        assertNotNull(server.rows["m5"])
    }

    private class CountingStep(override val name: String, val stop_once: Boolean) : ResealStep {
        var runs = 0
        override suspend fun run(ctx: ResealContext): StepResult {
            val progress = step_progress(ctx, name)
            if (progress.done) return StepResult.DONE
            runs += 1
            if (stop_once && progress.cursor == null) {
                progress.cursor = "page-2"
                ctx.persist()
                return StepResult.INTERRUPTED
            }
            return mark_step_done(ctx, progress)
        }
    }

    @Test
    fun interrupted_run_resumes_from_saved_progress() = runBlocking {
        val store = MemoryProgressStore()
        val a = CountingStep("a", false)
        val b = CountingStep("b", true)
        val api = EscrowServer()
        val first = run_storage_reseal(pending_view(), api, store, finish = { StorageRekeyFinish.DONE }, sleep = {}, steps = listOf(a, b))
        assertEquals(ResealRunStatus.INTERRUPTED, first.status)
        assertEquals("b", first.reason)
        val saved = store.snapshot()!!
        assertEquals(storage_key_id(fresh_key), saved.key_id)
        assertTrue(saved.steps.getValue("a").done)
        assertEquals("page-2", saved.steps.getValue("b").cursor)
        val second = run_storage_reseal(pending_view(), api, store, finish = { StorageRekeyFinish.DONE }, sleep = {}, steps = listOf(a, b))
        assertEquals(ResealRunStatus.DONE, second.status)
        assertEquals(1, a.runs)
        assertEquals(2, b.runs)
        assertNull(store.snapshot())
    }

    @Test
    fun progress_from_another_key_is_discarded() = runBlocking {
        val store = MemoryProgressStore(ResealProgress("other", mutableMapOf("a" to StepProgress(done = true))))
        val a = CountingStep("a", false)
        val result = run_storage_reseal(pending_view(), EscrowServer(), store, finish = { StorageRekeyFinish.DONE }, sleep = {}, steps = listOf(a))
        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(1, a.runs)
    }

    @Test
    fun cancelled_run_stops_before_any_step() = runBlocking {
        val a = CountingStep("a", false)
        val result = run_storage_reseal(
            pending_view(),
            EscrowServer(),
            MemoryProgressStore(),
            finish = { StorageRekeyFinish.DONE },
            sleep = {},
            is_cancelled = { true },
            steps = listOf(a),
        )
        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertEquals(0, a.runs)
    }

    @Test
    fun only_pending_random_vaults_need_a_reseal() {
        assertFalse(needs_storage_reseal(null))
        assertFalse(needs_storage_reseal(pending_view().copy(data_kek_random = false)))
        assertTrue(needs_storage_reseal(pending_view()))
    }

    @Test
    fun key_derivations_match_web_vectors() {
        val raw = ByteArray(32) { (it + 1).toByte() }
        assertEquals("riFsLvUkejeCwTXv", storage_key_id(raw))
        assertEquals("3Ola430IlLF43/ftx0FE6MT4ZsUe/m+X4ISq/It5sQQ=", encode_b64(derive_escrow_key(raw)))
        assertEquals("aj8Pcg3PFKMuMwKltn3fJHD1DzISk2YNv6Rq5NwgMCk=", encode_b64(derive_ratchet_key(raw)))
        assertEquals("pVHk5Z8KgZy62w+qV4zXI3vilSctAH3+9jHU0ZQBgU0=", encode_b64(derive_metadata_key(raw, "mail-item-metadata")))
        assertEquals("tN0mtExFtJsuEXl8aPhf0Dy3y7WaKMozG0rdWCLzXI8=", sign_with(derive_hmac_key(raw, "contact-search"), "alice@example.com"))
        assertArrayEquals("aster.escrow.v2\u0000m1".toByteArray(), escrow_aad_modes("m1").first())
        assertArrayEquals("aster.escrow.v3\u0000m1".toByteArray(), escrow_aad_modes("v3:m1").single())
    }

    @Test
    fun retry_schedule_is_bounded() {
        assertEquals(60L, storage_rekey_retry_delay(0))
        assertEquals(900L, storage_rekey_retry_delay(2))
        assertNull(storage_rekey_retry_delay(3))
        assertTrue(should_retry_storage_rekey(ResealRunResult(ResealRunStatus.INTERRUPTED, StepCounts())))
        assertFalse(should_retry_storage_rekey(ResealRunResult(ResealRunStatus.NOT_NEEDED, StepCounts())))
        assertFalse(should_retry_storage_rekey(ResealRunResult(ResealRunStatus.DONE, StepCounts())))
    }
}
