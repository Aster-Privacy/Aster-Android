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

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetKeyRetentionTest {

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun jwk_of(kp: RatchetCrypto.EcKeyPair): String = RatchetCrypto.p256_private_to_jwk(kp.private_key)

    private fun store_with(identity: String, spk: String, pq: String? = null): SessionKeyStore {
        val store = SessionKeyStore(null)
        store.put_ratchet_keys(identity, "${identity}_public", spk, "${spk}_public")
        pq?.let { store.put_ratchet_pq_identity(it, "${it}_public") }
        return store
    }

    private fun previous(store: SessionKeyStore): JSONArray = JSONArray(store.get_ratchet_previous_keys_json() ?: "[]")

    private fun identity_tags(store: SessionKeyStore): List<String> {
        val entries = previous(store)
        return (0 until entries.length()).map { entries.getJSONObject(it).getString("ratchet_identity_key") }
    }

    @Test
    fun `replacing the identity keeps the full local key set`() {
        val store = store_with("id_a", "spk_a", "pq_a")
        retain_local_ratchet_keys(store, "id_b", "spk_b", "pq_b_public")
        val entry = previous(store).getJSONObject(0)
        assertEquals("id_a", entry.getString("ratchet_identity_key"))
        assertEquals("id_a_public", entry.getString("ratchet_identity_public"))
        assertEquals("spk_a", entry.getString("ratchet_signed_prekey"))
        assertEquals("spk_a_public", entry.getString("ratchet_signed_prekey_public"))
        assertEquals("pq_a", entry.getString("ratchet_pq_identity_key"))
        assertEquals("pq_a_public", entry.getString("ratchet_pq_identity_public"))
    }

    @Test
    fun `same keys retain nothing`() {
        val store = store_with("id_a", "spk_a", "pq_a")
        retain_local_ratchet_keys(store, "id_a", "spk_a", "pq_a_public")
        assertNull(store.get_ratchet_previous_keys_json())
    }

    @Test
    fun `absent incoming values retain nothing`() {
        val store = store_with("id_a", "spk_a")
        retain_local_ratchet_keys(store, null, null, null)
        assertNull(store.get_ratchet_previous_keys_json())
    }

    @Test
    fun `replacing only the signed prekey keeps the old one`() {
        val store = store_with("id_a", "spk_a")
        retain_local_ratchet_keys(store, "id_a", "spk_b", null)
        assertEquals("spk_a", previous(store).getJSONObject(0).getString("ratchet_signed_prekey"))
    }

    @Test
    fun `replacing only the pq identity keeps the old one`() {
        val store = store_with("id_a", "spk_a", "pq_a")
        retain_local_ratchet_keys(store, null, null, "pq_b_public")
        assertEquals("pq_a", previous(store).getJSONObject(0).getString("ratchet_pq_identity_key"))
    }

    @Test
    fun `retaining twice does not duplicate`() {
        val store = store_with("id_a", "spk_a")
        retain_local_ratchet_keys(store, "id_b", "spk_b", null)
        retain_local_ratchet_keys(store, "id_b", "spk_b", null)
        assertEquals(1, previous(store).length())
    }

    @Test
    fun `existing previous keys are kept`() {
        val store = store_with("id_b", "spk_b")
        store.put_ratchet_previous_keys("[{\"ratchet_identity_key\":\"id_a\"}]")
        retain_local_ratchet_keys(store, "id_c", "spk_c", null)
        assertEquals(listOf("id_b", "id_a"), identity_tags(store))
    }

    @Test
    fun `merge fills missing fields from a duplicate`() {
        val merged = merge_retained_ratchet_keys(
            "[{\"ratchet_identity_key\":\"id_a\"}]",
            "[{\"ratchet_identity_key\":\"id_a\",\"ratchet_signed_prekey\":\"spk_a\",\"ratchet_signed_prekey_public\":\"spk_a_public\"}]",
        )
        val entries = JSONArray(merged)
        assertEquals(1, entries.length())
        assertEquals("spk_a", entries.getJSONObject(0).getString("ratchet_signed_prekey"))
    }

    @Test
    fun `merge keeps entries from every source`() {
        val merged = merge_retained_ratchet_keys(
            "[{\"ratchet_identity_key\":\"id_a\"}]",
            null,
            "not json",
            "[{\"ratchet_identity_key\":\"id_b\"},{\"ratchet_pq_identity_seed\":\"seed_c\"}]",
        )
        assertEquals(3, JSONArray(merged).length())
    }

    @Test
    fun `merge of nothing is null`() {
        assertNull(merge_retained_ratchet_keys(null, "[]", "  "))
    }

    @Test
    fun `merge caps the number of retained sets`() {
        val many = JSONArray()
        repeat(max_retained_ratchet_key_sets + 10) { many.put(JSONObject().put("ratchet_identity_key", "id_$it")) }
        assertEquals(max_retained_ratchet_key_sets, JSONArray(merge_retained_ratchet_keys(many.toString())).length())
    }

    private fun vault_for(identity: RatchetCrypto.EcKeyPair, spk: RatchetCrypto.EcKeyPair): JSONObject = JSONObject()
        .put("ratchet_identity_key", jwk_of(identity))
        .put("ratchet_signed_prekey", jwk_of(spk))
        .put("ratchet_signed_prekey_public", RatchetCrypto.b64_encode(spk.public_raw))

    private fun holds(vault: JSONObject, identity: RatchetCrypto.EcKeyPair, spk: RatchetCrypto.EcKeyPair, pq: String? = null) =
        vault_holds_ratchet_keys(
            vault,
            RatchetCrypto.b64_encode(identity.public_raw),
            RatchetCrypto.b64_encode(spk.public_raw),
            pq,
        )

    @Test
    fun `vault holding the keys allows the upload`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        assertTrue(holds(vault_for(identity, spk), identity, spk))
    }

    @Test
    fun `vault with another identity blocks the upload`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        assertFalse(holds(vault_for(RatchetCrypto.generate_p256_keypair(), spk), identity, spk))
    }

    @Test
    fun `vault with no ratchet keys blocks the upload`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        assertFalse(holds(JSONObject(), identity, spk))
    }

    @Test
    fun `vault with another signed prekey blocks the upload`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        assertFalse(holds(vault_for(identity, RatchetCrypto.generate_p256_keypair()), identity, spk))
    }

    @Test
    fun `vault missing the pq identity blocks a pq upload`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        val vault = vault_for(identity, spk)
        assertFalse(holds(vault, identity, spk, "pq_public"))
        vault.put("ratchet_pq_identity_public", "pq_public")
        assertFalse(holds(vault, identity, spk, "pq_public"))
        vault.put("ratchet_pq_identity_seed", "seed")
        assertTrue(holds(vault, identity, spk, "pq_public"))
    }

    @Test
    fun `applying a vault with new keys keeps the replaced local keys`() {
        val store = store_with("id_a", "spk_a", "pq_a")
        store.put_ratchet_previous_keys("[{\"ratchet_identity_key\":\"id_old\"}]")
        val vault = JSONObject()
            .put("ratchet_identity_key", "id_b")
            .put("ratchet_signed_prekey", "spk_b")
            .put("ratchet_signed_prekey_public", "spk_b_public")
            .put("ratchet_pq_identity_key", "pq_b")
            .put("ratchet_pq_identity_public", "pq_b_public")
            .put("ratchet_previous_keys", JSONArray().put(JSONObject().put("ratchet_identity_key", "id_vault_old")))
        apply_vault_ratchet_keys(parse_vault_ratchet_keys(vault), store)
        assertEquals("id_b", store.get_ratchet_identity_jwk())
        assertEquals(setOf("id_a", "id_old", "id_vault_old"), identity_tags(store).toSet())
    }
    private fun vault_identity_tags(vault: JSONObject): Set<String> {
        val entries = vault.optJSONArray("ratchet_previous_keys") ?: JSONArray()
        return (0 until entries.length()).map { entries.getJSONObject(it).getString("ratchet_identity_key") }.toSet()
    }

    @Test
    fun `a password change keeps device keys the vault does not hold`() {
        val store = store_with("id_local", "spk_local", "pq_local")
        store.put_ratchet_previous_keys("[{\"ratchet_identity_key\":\"id_local_old\"}]")
        val vault = JSONObject()
            .put("ratchet_identity_key", "id_vault")
            .put("ratchet_previous_keys", JSONArray().put(JSONObject().put("ratchet_identity_key", "id_vault_old")))
        fold_local_ratchet_keys_into_vault(vault, store)
        assertEquals(setOf("id_vault_old", "id_local", "id_local_old"), vault_identity_tags(vault))
        val local_entry = (0 until vault.getJSONArray("ratchet_previous_keys").length())
            .map { vault.getJSONArray("ratchet_previous_keys").getJSONObject(it) }
            .first { it.getString("ratchet_identity_key") == "id_local" }
        assertEquals("spk_local", local_entry.getString("ratchet_signed_prekey"))
        assertEquals("pq_local", local_entry.getString("ratchet_pq_identity_key"))
    }

    @Test
    fun `a password change does not copy the current vault keys into previous keys`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        val store = SessionKeyStore(null)
        store.put_ratchet_keys(
            jwk_of(identity),
            RatchetCrypto.b64_encode(identity.public_raw),
            jwk_of(spk),
            RatchetCrypto.b64_encode(spk.public_raw),
        )
        val vault = vault_for(identity, spk)
        fold_local_ratchet_keys_into_vault(vault, store)
        assertFalse(vault.has("ratchet_previous_keys"))
    }

    private fun real_store(identity: RatchetCrypto.EcKeyPair, spk: RatchetCrypto.EcKeyPair): SessionKeyStore {
        val store = SessionKeyStore(null)
        store.put_ratchet_keys(
            jwk_of(identity),
            RatchetCrypto.b64_encode(identity.public_raw),
            jwk_of(spk),
            RatchetCrypto.b64_encode(spk.public_raw),
        )
        return store
    }

    private fun entries_of(array: JSONArray?): List<JSONObject> =
        (0 until (array?.length() ?: 0)).map { array!!.getJSONObject(it) }

    @Test
    fun `a vault signed prekey for the same identity is adopted and the local one is kept`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val local_spk = RatchetCrypto.generate_p256_keypair()
        val vault_spk = RatchetCrypto.generate_p256_keypair()
        val store = real_store(identity, local_spk)
        val vault = vault_for(identity, vault_spk)

        val adopted = vault_signed_prekey_to_adopt(vault, store)
        assertEquals(jwk_of(vault_spk), adopted?.jwk)
        assertEquals(RatchetCrypto.b64_encode(vault_spk.public_raw), adopted?.public_b64)

        fold_local_ratchet_keys_into_vault(vault, store)
        assertTrue(adopt_vault_signed_prekey(store, adopted!!))

        assertEquals(jwk_of(vault_spk), vault.getString("ratchet_signed_prekey"))
        assertTrue(entries_of(vault.optJSONArray("ratchet_previous_keys")).any {
            it.optString("ratchet_signed_prekey") == jwk_of(local_spk)
        })
        assertEquals(jwk_of(vault_spk), store.get_ratchet_signed_prekey_jwk())
        assertEquals(RatchetCrypto.b64_encode(vault_spk.public_raw), store.get_ratchet_signed_prekey_public_b64())
        assertEquals(jwk_of(identity), store.get_ratchet_identity_jwk())
        assertEquals(jwk_of(local_spk), previous(store).getJSONObject(0).getString("ratchet_signed_prekey"))
        assertTrue(holds(vault, identity, vault_spk))
    }

    @Test
    fun `a vault signed prekey without its public key is derived before adoption`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val vault_spk = RatchetCrypto.generate_p256_keypair()
        val store = real_store(identity, RatchetCrypto.generate_p256_keypair())
        val vault = vault_for(identity, vault_spk)
        vault.remove("ratchet_signed_prekey_public")
        assertEquals(
            RatchetCrypto.b64_encode(vault_spk.public_raw),
            vault_signed_prekey_to_adopt(vault, store)?.public_b64,
        )
    }

    @Test
    fun `a matching vault signed prekey is not adopted`() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        assertNull(vault_signed_prekey_to_adopt(vault_for(identity, spk), real_store(identity, spk)))
    }

    @Test
    fun `a vault signed prekey for another identity is not adopted`() {
        val store = real_store(RatchetCrypto.generate_p256_keypair(), RatchetCrypto.generate_p256_keypair())
        val vault = vault_for(RatchetCrypto.generate_p256_keypair(), RatchetCrypto.generate_p256_keypair())
        assertNull(vault_signed_prekey_to_adopt(vault, store))
    }

    @Test
    fun `merge keeps every signed prekey of one identity`() {
        val merged = merge_retained_ratchet_keys(
            "[{\"ratchet_identity_key\":\"id_a\",\"ratchet_signed_prekey\":\"spk_a\",\"ratchet_signed_prekey_public\":\"spk_a_public\"}]",
            "[{\"ratchet_identity_key\":\"id_a\",\"ratchet_signed_prekey\":\"spk_b\",\"ratchet_signed_prekey_public\":\"spk_b_public\"}]",
        )
        val prekeys = entries_of(JSONArray(merged)).map { it.getString("ratchet_signed_prekey") }
        assertEquals(listOf("spk_a", "spk_b"), prekeys)
    }

    @Test
    fun `retaining a replaced signed prekey for one identity keeps every earlier one`() {
        val store = store_with("id_a", "spk_a")
        retain_local_ratchet_keys(store, "id_a", "spk_b", null)
        store.put_ratchet_keys("id_a", "id_a_public", "spk_b", "spk_b_public")
        retain_local_ratchet_keys(store, "id_a", "spk_c", null)
        val prekeys = entries_of(previous(store)).map { it.getString("ratchet_signed_prekey") }
        assertEquals(listOf("spk_b", "spk_a"), prekeys)
    }

    @Test
    fun `receiver key set tags tell signed prekeys of one identity apart`() {
        val first = receiver_key_set_tag("id_public", "id_jwk", "spk_a_public")
        val second = receiver_key_set_tag("id_public", "id_jwk", "spk_b_public")
        assertFalse(first == second)
        assertEquals(first, receiver_key_set_tag("id_public", "other_jwk", "spk_a_public"))
        assertEquals(receiver_key_set_tag("", "id_jwk", "spk"), receiver_key_set_tag("", "id_jwk", "spk"))
    }

    @Test
    fun `replacing the vault signed prekey for the same identity keeps the old one in the vault`() {
        val vault = JSONObject()
            .put("ratchet_identity_key", "id_a")
            .put("ratchet_signed_prekey", "spk_a")
            .put("ratchet_signed_prekey_public", "spk_a_public")
            .put("ratchet_previous_keys", JSONArray().put(
                JSONObject().put("ratchet_identity_key", "id_a")
                    .put("ratchet_signed_prekey", "spk_old")
                    .put("ratchet_signed_prekey_public", "spk_old_public"),
            ))
        retain_vault_key_set(vault, "id_a", "spk_b")
        val prekeys = entries_of(vault.getJSONArray("ratchet_previous_keys")).map { it.getString("ratchet_signed_prekey") }
        assertEquals(listOf("spk_a", "spk_old"), prekeys)
    }

    @Test
    fun `an unchanged vault key set is not retained`() {
        val vault = JSONObject()
            .put("ratchet_identity_key", "id_a")
            .put("ratchet_signed_prekey", "spk_a")
        retain_vault_key_set(vault, "id_a", "spk_a")
        assertFalse(vault.has("ratchet_previous_keys"))
    }

    @Test
    fun `a local pq pair is kept in the vault before a fresh pair replaces it`() {
        val store = store_with("id_a", "spk_a", "pq_a")
        val vault = JSONObject()
            .put("ratchet_identity_key", "id_a")
            .put("ratchet_pq_identity_seed", "seed_vault")
        retain_vault_pq_identity(vault)
        retain_local_pq_identity_in_vault(vault, store)
        val kept = entries_of(vault.getJSONArray("ratchet_previous_keys"))
        assertTrue(kept.any { it.optString("ratchet_pq_identity_seed") == "seed_vault" })
        assertTrue(
            kept.any {
                it.optString("ratchet_pq_identity_key") == "pq_a" &&
                    it.optString("ratchet_pq_identity_public") == "pq_a_public"
            },
        )
    }

    @Test
    fun `a local pq secret without its public key is kept before a fresh pair replaces it`() {
        val store = store_with("id_a", "spk_a")
        store.put_ratchet_pq_identity("pq_a", null)
        val vault = JSONObject().put("ratchet_identity_key", "id_a")
        retain_local_pq_identity_in_vault(vault, store)
        val kept = entries_of(vault.getJSONArray("ratchet_previous_keys"))
        assertEquals(listOf("pq_a"), kept.map { it.getString("ratchet_pq_identity_key") })
    }

    @Test
    fun `the retention cap matches the other clients`() {
        assertEquals(32, max_retained_ratchet_key_sets)
    }

    @Test
    fun `a password change keeps the local set first when the vault list is full`() {
        val store = store_with("id_local", "spk_local", "pq_local")
        val full = JSONArray()
        repeat(max_retained_ratchet_key_sets) { full.put(JSONObject().put("ratchet_identity_key", "id_vault_$it")) }
        val vault = JSONObject()
            .put("ratchet_identity_key", "id_vault")
            .put("ratchet_previous_keys", full)
        fold_local_ratchet_keys_into_vault(vault, store)
        val kept = vault.getJSONArray("ratchet_previous_keys")
        assertEquals(max_retained_ratchet_key_sets, kept.length())
        assertEquals("id_local", kept.getJSONObject(0).getString("ratchet_identity_key"))
    }

    @Test
    fun `retained key set comparison sees a new entry`() {
        val before = JSONArray().put(JSONObject().put("ratchet_identity_key", "id_a"))
        assertTrue(retained_key_sets_equal(before, JSONArray(before.toString())))
        assertTrue(retained_key_sets_equal(null, JSONArray()))
        val after = JSONArray(merge_retained_ratchet_keys(
            "[{\"ratchet_identity_key\":\"id_b\"}]",
            before.toString(),
        ))
        assertFalse(retained_key_sets_equal(before, after))
    }
}
