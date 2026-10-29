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
}
