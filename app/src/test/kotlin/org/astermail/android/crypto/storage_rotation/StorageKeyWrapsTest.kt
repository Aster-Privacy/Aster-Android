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
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.crypto.AccountKey
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.mail.ratchet.collect_vault_key_fingerprints
import org.astermail.android.mail.ratchet.vault_identity_fingerprint
import org.astermail.android.mail.ratchet.vault_key_fingerprint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StorageKeyWrapsTest {

    private val now = "2026-11-06T00:00:00Z"
    private val newest_kek = ByteArray(32) { 21 }
    private val stale_kek = ByteArray(32) { 22 }
    private val derived_kek = ByteArray(32) { 23 }
    private val later_kek = ByteArray(32) { 24 }

    @Before
    fun mock_android_base64() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun unmock_android_base64() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun ratchet_public(identity: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        return ByteArray(65) { if (it == 0) 4 else digest[(it - 1) % digest.size] }
    }

    private fun identity_fingerprint(identity: String): String =
        vault_identity_fingerprint(JSONObject().put("ratchet_identity_public", encode_b64(ratchet_public(identity))))!!

    private class Codec : StorageVaultCodec {
        override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
            decode_b64(encrypted_vault)?.let { JSONObject(String(it, Charsets.UTF_8)) }
        override fun encrypt(vault: JSONObject): SealedVault =
            SealedVault(encode_b64(vault.toString().toByteArray(Charsets.UTF_8)), "bm9uY2U=")
        override fun password_keys(vault: JSONObject): List<ByteArray> = emptyList()
        override fun generate_key(): ByteArray = ByteArray(32) { 3 }
        override fun fingerprints(vault: JSONObject): List<String> = emptyList()
    }

    private class FakeCrypto : StorageKeyWrapCrypto {
        override fun seal(data_kek: ByteArray, identity_private_key: String): ByteArray =
            ("$identity_private_key|" + encode_b64(data_kek)).toByteArray(Charsets.UTF_8)

        override fun open(wrap: ByteArray, private_keys: List<String>): ByteArray? {
            val (owner, payload) = String(wrap, Charsets.UTF_8).split("|", limit = 2)
            if (owner !in private_keys) return null
            return decode_b64(payload)
        }
    }

    private class Server(initial: JSONObject) : ResealApi {
        var vault: JSONObject = initial
        val wraps = ArrayList<JsonObject>()
        val posted = ArrayList<JsonObject>()
        var vault_puts = 0
        var wraps_status = 200

        fun sealed(): String = encode_b64(vault.toString().toByteArray(Charsets.UTF_8))

        fun digest(sealed: String): String =
            MessageDigest.getInstance("SHA-256").digest(sealed.toByteArray()).joinToString("") { "%02x".format(it) }

        override suspend fun get(path: String): ApiReply {
            if (path == STORAGE_KEY_WRAPS_PATH) {
                if (wraps_status != 200) return ApiReply(wraps_status, null, "RATE_LIMIT_EXCEEDED")
                return ApiReply(200, JsonObject(mapOf("wraps" to JsonArray(wraps.toList()))))
            }
            val sealed = sealed()
            return ApiReply(
                200,
                json_object("encrypted_vault" to sealed, "vault_nonce" to "bm9uY2U=", "vault_sha256" to digest(sealed)),
            )
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply {
            if (path != STORAGE_KEY_WRAPS_PATH) return ApiReply(404)
            val obj = body as JsonObject
            posted.add(obj)
            wraps.add(0, JsonObject(obj + ("created_at" to JsonPrimitive(now_stamp()))))
            return ApiReply(200, json_object("ok" to true))
        }

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            val obj = body as JsonObject
            if (obj.str("expected_vault_sha256") != digest(sealed())) return ApiReply(409, null, VAULT_CHANGED_CODE)
            vault_puts += 1
            vault = JSONObject(String(decode_b64(obj.str("encrypted_vault"))!!, Charsets.UTF_8))
            return ApiReply(200, json_object("ok" to true))
        }

        private fun now_stamp(): String = "2026-11-06T00:00:0${wraps.size}Z"
    }

    private fun updater(server: Server) = VaultUpdater(
        api = server,
        codec = Codec(),
        user_id = { "user-1" },
        lock = Mutex(),
        on_vault_changed = { _, _ -> },
        now = { now },
    )

    private fun wrap_entry(kek: ByteArray, identity: String, crypto: StorageKeyWrapCrypto = FakeCrypto()): JsonObject =
        json_object(
            "key_id" to wrap_key_id(kek),
            "wrap" to encode_b64(crypto.seal(kek, identity)!!),
            "identity_fingerprint" to identity_fingerprint(identity),
            "created_at" to now,
        )

    private fun legacy_values(vault: JSONObject): List<String> {
        val array = vault.optJSONArray("legacy_keks") ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it).getString("k") }
    }

    private fun random_vault(kek: ByteArray, identity: String = "id-current"): JSONObject = JSONObject()
        .put("identity_key", identity)
        .put("ratchet_identity_public", encode_b64(ratchet_public(identity)))
        .put("data_kek", encode_b64(kek))
        .put("data_kek_random", true)

    @Test
    fun key_id_is_the_first_sixteen_bytes_of_the_key_digest() {
        val expected = MessageDigest.getInstance("SHA-256").digest(newest_kek).copyOf(16)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, wrap_key_id(newest_kek))
        assertEquals(32, wrap_key_id(newest_kek).length)
    }

    @Test
    fun the_wrap_fingerprint_is_the_vault_key_fingerprint_of_the_identity() = runBlocking {
        val vault = random_vault(newest_kek)
        val server = Server(vault)
        store_side_copy(server, FakeCrypto(), vault, emptyList())
        val sent = server.posted.single().str("identity_fingerprint")
        val identity_public = ratchet_public("id-current")
        val expected = Base64.getEncoder()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(identity_public))
        assertEquals(expected, sent)
        assertEquals(vault_key_fingerprint(identity_public), sent)
        assertEquals(collect_vault_key_fingerprints(vault).first(), sent)
        assertEquals(32, Base64.getDecoder().decode(sent).size)
    }

    @Test
    fun the_wrap_fingerprint_is_not_derived_from_the_openpgp_fingerprint() = runBlocking {
        val vault = random_vault(newest_kek, WEB_PRIVATE_KEY)
        val server = Server(vault)
        store_side_copy(server, FakeCrypto(), vault, emptyList())
        val sent = server.posted.single().str("identity_fingerprint")
        val pgp_hash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                WEB_FINGERPRINT.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            ),
        )
        assertNotEquals(pgp_hash, sent)
        assertEquals(collect_vault_key_fingerprints(vault).first(), sent)
    }

    @Test
    fun no_side_copy_is_sent_without_a_vault_key_fingerprint() = runBlocking {
        val vault = random_vault(newest_kek).apply { remove("ratchet_identity_public") }
        val server = Server(vault)
        assertEquals(SideCopyStore.SKIPPED, store_side_copy(server, FakeCrypto(), vault, emptyList()))
        assertTrue(server.posted.isEmpty())
    }

    @Test
    fun rotating_the_identity_uploads_a_fresh_side_copy_at_once() = runBlocking {
        val server = Server(random_vault(newest_kek))
        server.wraps.add(wrap_entry(newest_kek, "id-current"))
        val crypto = FakeCrypto()

        val outcome = update_identity_with_side_copy(updater(server), server, crypto) { draft ->
            draft.vault.put("identity_key", "id-rotated")
            draft.vault.put("ratchet_identity_public", encode_b64(ratchet_public("id-rotated")))
            VaultEdit.CHANGED
        }

        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertEquals(1, server.vault_puts)
        val body = server.posted.single()
        assertEquals(wrap_key_id(newest_kek), body.str("key_id"))
        assertEquals(identity_fingerprint("id-rotated"), body.str("identity_fingerprint"))
        assertEquals(collect_vault_key_fingerprints(server.vault).first(), body.str("identity_fingerprint"))
        assertArrayEquals(newest_kek, crypto.open(decode_b64(body.str("wrap"))!!, listOf("id-rotated")))
    }

    @Test
    fun a_new_identity_key_with_the_same_ratchet_key_still_gets_a_fresh_copy() = runBlocking {
        val server = Server(random_vault(newest_kek))
        server.wraps.add(wrap_entry(newest_kek, "id-current"))
        val crypto = FakeCrypto()

        update_identity_with_side_copy(updater(server), server, crypto) { draft ->
            draft.vault.put("identity_key", "id-current-with-new-address")
            VaultEdit.CHANGED
        }

        val body = server.posted.single()
        assertArrayEquals(
            newest_kek,
            crypto.open(decode_b64(body.str("wrap"))!!, listOf("id-current-with-new-address")),
        )
    }

    @Test
    fun a_vault_write_that_keeps_the_identity_uploads_nothing() = runBlocking {
        val server = Server(random_vault(newest_kek))
        val outcome = update_identity_with_side_copy(updater(server), server, FakeCrypto()) { draft ->
            draft.vault.put("note", "unrelated")
            VaultEdit.CHANGED
        }
        assertTrue(outcome is VaultUpdateOutcome.Written)
        assertTrue(server.posted.isEmpty())
    }

    @Test
    fun an_aborted_identity_write_uploads_nothing() = runBlocking {
        val server = Server(random_vault(newest_kek))
        val outcome = update_identity_with_side_copy(updater(server), server, FakeCrypto()) { draft ->
            draft.vault.put("identity_key", "id-rotated")
            VaultEdit.ABORT
        }
        assertFalse(outcome is VaultUpdateOutcome.Written)
        assertTrue(server.posted.isEmpty())
    }

    @Test
    fun a_real_wrap_round_trips_through_the_identity_key() {
        val pass = "wrap passphrase"
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", pass.toCharArray())
        val crypto = PgpStorageKeyWrapCrypto { pass.toByteArray(Charsets.UTF_8) }
        val sealed = crypto.seal(newest_kek, key.armored_private_key)
        assertNotNull(sealed)
        assertFalse(String(sealed!!, Charsets.ISO_8859_1).contains("BEGIN PGP"))
        val opened = crypto.open(sealed, listOf(key.armored_private_key))
        assertArrayEquals(newest_kek, opened)
        val other = PgpKeyGenerator.generate("Other", "other@astermail.org", pass.toCharArray())
        assertNull(crypto.open(sealed, listOf(other.armored_private_key)))
    }

    @Test
    fun a_real_wrap_opens_with_a_previous_identity_key() {
        val pass = "wrap passphrase"
        val old_key = PgpKeyGenerator.generate("User", "user@astermail.org", pass.toCharArray())
        val new_key = PgpKeyGenerator.generate("User", "user@astermail.org", pass.toCharArray())
        val crypto = PgpStorageKeyWrapCrypto { pass.toByteArray(Charsets.UTF_8) }
        val sealed = crypto.seal(newest_kek, old_key.armored_private_key)!!
        val vault = JSONObject()
            .put("identity_key", new_key.armored_private_key)
            .put("previous_keys", JSONArray().put(old_key.armored_private_key))
            .put("data_kek", encode_b64(stale_kek))
            .put("data_kek_random", true)
        val wraps = listOf(StorageKeyWrap(wrap_key_id(newest_kek), encode_b64(sealed), "", now))
        assertArrayEquals(newest_kek, open_side_copy(wraps, vault, crypto))
    }

    @Test
    fun a_wrap_made_by_the_web_client_opens_here() {
        val opened = PgpDecryptor.decrypt_bytes_with_own_keys(
            Base64.getDecoder().decode(WEB_WRAP),
            listOf(WEB_PRIVATE_KEY),
            WEB_PASSPHRASE.toCharArray(),
        )
        assertNotNull(opened)
        assertArrayEquals(Base64.getDecoder().decode(WEB_DATA_KEK), opened)
        assertEquals(WEB_KEY_ID, wrap_key_id(opened!!))
        assertEquals(WEB_FINGERPRINT, AccountKey.primary_fingerprint(WEB_PRIVATE_KEY)?.lowercase())
        val crypto = PgpStorageKeyWrapCrypto { WEB_PASSPHRASE.toByteArray(Charsets.UTF_8) }
        val vault = JSONObject().put("identity_key", WEB_PRIVATE_KEY)
        val wraps = listOf(StorageKeyWrap(WEB_KEY_ID, WEB_WRAP, "", now))
        assertArrayEquals(Base64.getDecoder().decode(WEB_DATA_KEK), open_side_copy(wraps, vault, crypto))
    }

    @Test
    fun heal_restores_the_newest_key_after_a_stale_overwrite() = runBlocking {
        val stale = random_vault(stale_kek)
            .put("legacy_keks", JSONArray().put(JSONObject().put("k", encode_b64(derived_kek)).put("added_at", "x")))
        val server = Server(stale)
        server.wraps.add(wrap_entry(newest_kek, "id-current"))

        val result = heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())

        assertTrue(result is StorageKeyHeal.Healed)
        val vault = server.vault
        assertEquals(encode_b64(newest_kek), vault.getString("data_kek"))
        assertEquals(true, vault.get("data_kek_random"))
        assertTrue(encode_b64(stale_kek) in legacy_values(vault))
        assertTrue(encode_b64(derived_kek) in legacy_values(vault))
        val rekey = vault.getJSONObject("storage_rekey")
        assertEquals("pending", rekey.getString("state"))
        assertEquals(encode_b64(stale_kek), rekey.getJSONArray("prior_keys").getString(0))
        assertEquals(1, server.vault_puts)
    }

    @Test
    fun heal_runs_once_and_then_leaves_the_vault_alone() = runBlocking {
        val server = Server(random_vault(stale_kek))
        server.wraps.add(wrap_entry(newest_kek, "id-current"))
        heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())
        val second = heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())
        assertTrue(second is StorageKeyHeal.Current)
        assertEquals(1, server.vault_puts)
    }

    @Test
    fun heal_restores_the_key_after_a_recovery_restore_from_an_old_backup() = runBlocking {
        val restored = JSONObject()
            .put("identity_key", "id-current")
            .put("data_kek", encode_b64(derived_kek))
            .put("recovery_codes", JSONArray().put("code-1"))
        val server = Server(restored)
        server.wraps.add(wrap_entry(newest_kek, "id-current"))

        val result = heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())

        assertTrue(result is StorageKeyHeal.Healed)
        val vault = server.vault
        assertEquals(encode_b64(newest_kek), vault.getString("data_kek"))
        assertEquals(true, vault.get("data_kek_random"))
        assertEquals(listOf(encode_b64(derived_kek)), legacy_values(vault))
        assertEquals("pending", vault.getJSONObject("storage_rekey").getString("state"))
        assertEquals("code-1", vault.getJSONArray("recovery_codes").getString(0))
    }

    @Test
    fun heal_opens_a_wrap_made_for_a_legacy_identity_key() = runBlocking {
        val vault = random_vault(stale_kek, "id-new")
            .put("legacy_identity_keys", JSONArray().put("id-retired"))
        val server = Server(vault)
        server.wraps.add(wrap_entry(newest_kek, "id-retired"))
        val result = heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())
        assertTrue(result is StorageKeyHeal.Healed)
        assertEquals(encode_b64(newest_kek), server.vault.getString("data_kek"))
    }

    @Test
    fun heal_never_rolls_back_a_vault_that_already_moved_past_the_wrap() = runBlocking {
        val vault = random_vault(later_kek)
            .put("legacy_keks", JSONArray().put(JSONObject().put("k", encode_b64(newest_kek)).put("added_at", "x")))
        val server = Server(vault)
        server.wraps.add(wrap_entry(newest_kek, "id-current"))
        val result = heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())
        assertTrue(result is StorageKeyHeal.Current)
        assertEquals(0, server.vault_puts)
        assertEquals(encode_b64(later_kek), server.vault.getString("data_kek"))
    }

    @Test
    fun heal_rejects_a_wrap_whose_contents_do_not_match_its_key_id() = runBlocking {
        val server = Server(random_vault(stale_kek))
        val forged = JsonObject(wrap_entry(newest_kek, "id-current") + ("key_id" to JsonPrimitive(wrap_key_id(later_kek))))
        server.wraps.add(forged)
        val result = heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())
        assertTrue(result is StorageKeyHeal.Current)
        assertEquals(0, server.vault_puts)
    }

    @Test
    fun heal_is_skipped_quietly_when_the_wraps_cannot_be_listed() = runBlocking {
        val server = Server(random_vault(stale_kek))
        server.wraps_status = 429
        val result = heal_storage_key_from_side_copy(server, updater(server), FakeCrypto())
        assertTrue(result is StorageKeyHeal.Skipped)
        assertEquals(0, server.vault_puts)
    }

    @Test
    fun a_side_copy_is_stored_once_without_a_nonce() = runBlocking {
        val vault = random_vault(newest_kek)
        val server = Server(vault)
        val crypto = FakeCrypto()

        val first = store_side_copy(server, crypto, vault, list_storage_key_wraps(server)!!)
        val second = store_side_copy(server, crypto, vault, list_storage_key_wraps(server)!!)

        assertEquals(SideCopyStore.STORED, first)
        assertEquals(SideCopyStore.CURRENT, second)
        assertEquals(1, server.posted.size)
        val body = server.posted.single()
        assertEquals(setOf("key_id", "wrap", "identity_fingerprint"), body.keys)
        assertEquals(wrap_key_id(newest_kek), body.str("key_id"))
        assertEquals(32, Base64.getDecoder().decode(body.str("identity_fingerprint")).size)
        assertArrayEquals(newest_kek, crypto.open(decode_b64(body.str("wrap"))!!, listOf("id-current")))
    }

    @Test
    fun a_new_identity_key_gets_its_own_side_copy() = runBlocking {
        val server = Server(random_vault(newest_kek))
        store_side_copy(server, FakeCrypto(), random_vault(newest_kek), list_storage_key_wraps(server)!!)
        val rotated = random_vault(newest_kek, "id-rotated")
        val result = store_side_copy(server, FakeCrypto(), rotated, list_storage_key_wraps(server)!!)
        assertEquals(SideCopyStore.STORED, result)
        assertEquals(2, server.posted.size)
    }

    @Test
    fun no_side_copy_is_made_for_a_derived_storage_key() = runBlocking {
        val vault = JSONObject()
            .put("identity_key", "id-current")
            .put("ratchet_identity_public", encode_b64(ratchet_public("id-current")))
            .put("data_kek", encode_b64(derived_kek))
        val server = Server(vault)
        assertEquals(SideCopyStore.CURRENT, store_side_copy(server, FakeCrypto(), vault, emptyList()))
        assertTrue(server.posted.isEmpty())
    }

    private companion object {
        const val WEB_PASSPHRASE = "fixture-passphrase"
        const val WEB_DATA_KEK = "7d5T6DFMUKJxkohJFYPmO8WiHNnFLyEBdIFIMJUYzYc="
        const val WEB_KEY_ID = "55ded959843718936b21561128b97778"
        const val WEB_FINGERPRINT = "51318f173a068ce9344b2ccee62ebcf9b25bb033"
        const val WEB_WRAP = "wV4DN2eoEAVWU3MSAQdA+sPmzGawFLO7w1POZf70R5WiuEHEvRm9t+R6ywLT81wwiVbSsiwbfs54N5zARMMzQ8e2kw0FronylyrXcByXSurSLL45Zb0cS2LzCO4E4RI30lEBGF1KOf4+pwdgp1T49AD1qp1isvxnMjJTk0ymRFiuLwg1E+CZ5ZK8q9gUSVdWORsjRdvHxh7HR4/yYVO7ox6+Zr7kvg8104hpSg6cBxtS9vo="
        val WEB_PRIVATE_KEY = listOf(
            "-----BEGIN PGP PRIVATE KEY BLOCK-----",
            "",
            "xYYEau4/rRYJKwYBBAHaRw8BAQdAgiTOtjYY9OQk4B0/yfI5yzwnl3nVJrrA",
            "pQ7RkDSNd+r+CQMIQvfAyVa6uHjgUyfGg/8Ed+dGDDTgnfejkldeTQADgymi",
            "bAVhMpe4NVtQ4YVC9KeTnRIVrhHlhs2LR5jJ+KQFJZ+yyOUKcKLZ2rGjtfPv",
            "9c0hZml4dHVyZSA8Zml4dHVyZUBleGFtcGxlLmludmFsaWQ+wsATBBMWCgCF",
            "BYJq7j+tAwsJBwkQ5i68+bJbsDNFFAAAAAAAHAAgc2FsdEBub3RhdGlvbnMu",
            "b3BlbnBncGpzLm9yZ7rdEpw4OL1XWiUL6wtGBG4ZyW/lXgVB4TFSo98+VlPs",
            "BRUKCA4MBBYAAgECGQECmwMCHgEWIQRRMY8XOgaM6TRLLM7mLrz5sluwMwAA",
            "mvwA/1AzaHSPX+/gTjZN5Z0Og8mLWkcY/M5x5k9xP9rX9o9ZAQDxnoLVBIu6",
            "wrSFnU1isCG4ja7HKtkv9zeN7iiOwY1aA8eLBGruP60SCisGAQQBl1UBBQEB",
            "B0BPe1CD5VxZ3Z7+cR23vusHgLjJS2uN6kI1v4Y5THTcPgMBCAf+CQMIX9jA",
            "UsiyfZDgYHuCmtxRfCndN/M7Na8Vt+PDxLhvf0bGjgEZ6WKP5oTtlRsiVWsa",
            "gAm1NpfqxhwKmDtyJtnk1JGVO49mcFtCdVAmvTFv0cK+BBgWCgBwBYJq7j+t",
            "CRDmLrz5sluwM0UUAAAAAAAcACBzYWx0QG5vdGF0aW9ucy5vcGVucGdwanMu",
            "b3JnGnpBcOPJX2d0CqO9R9dXlBJlf2h3pSSeW0/KBCT8umUCmwwWIQRRMY8X",
            "OgaM6TRLLM7mLrz5sluwMwAAmfABALzkf0/VwEkeWnJ4c33Mj4EFcVw5/Fho",
            "7JUnKUXMmOAqAQD0zjgNu59FMWaW1f3VGVPgIznby0OaW/1DBy5HySjUAg==",
            "=AuL+",
            "-----END PGP PRIVATE KEY BLOCK-----",
            "",
        ).joinToString("\n")
    }
}
