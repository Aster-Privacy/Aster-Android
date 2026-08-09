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

import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultRatchetKeysTest {

    private fun vault(vararg fields: Pair<String, String>): JSONObject {
        val obj = JSONObject()
        fields.forEach { (key, value) -> obj.put(key, value) }
        return obj
    }

    private fun store_for(vault_obj: JSONObject): SessionKeyStore {
        val store = SessionKeyStore(null)
        apply_vault_ratchet_keys(parse_vault_ratchet_keys(vault_obj), store)
        return store
    }

    @Test
    fun `identity only vault still stores the ratchet identity`() {
        val store = store_for(
            vault(
                "ratchet_identity_key" to "identity_jwk_value",
                "ratchet_identity_public" to "identity_public_value",
            ),
        )
        assertEquals("identity_jwk_value", store.get_ratchet_identity_jwk())
        assertEquals("identity_public_value", store.get_ratchet_identity_public_b64())
        assertNull(store.get_ratchet_signed_prekey_jwk())
        assertNull(store.get_ratchet_signed_prekey_public_b64())
        assertNull(store.get_ratchet_pq_identity_secret())
    }

    @Test
    fun `identity without identity public is still stored`() {
        val store = store_for(vault("ratchet_identity_key" to "identity_jwk_value"))
        assertEquals("identity_jwk_value", store.get_ratchet_identity_jwk())
        assertNull(store.get_ratchet_identity_public_b64())
    }

    @Test
    fun `identity plus signed prekey stores both`() {
        val store = store_for(
            vault(
                "ratchet_identity_key" to "identity_jwk_value",
                "ratchet_identity_public" to "identity_public_value",
                "ratchet_signed_prekey" to "spk_jwk_value",
                "ratchet_signed_prekey_public" to "spk_public_value",
            ),
        )
        assertEquals("identity_jwk_value", store.get_ratchet_identity_jwk())
        assertEquals("spk_jwk_value", store.get_ratchet_signed_prekey_jwk())
        assertEquals("spk_public_value", store.get_ratchet_signed_prekey_public_b64())
    }

    @Test
    fun `pq secret without pq public is still stored`() {
        val store = store_for(
            vault(
                "ratchet_identity_key" to "identity_jwk_value",
                "ratchet_pq_identity_key" to "pq_secret_value",
            ),
        )
        assertEquals("identity_jwk_value", store.get_ratchet_identity_jwk())
        assertEquals("pq_secret_value", store.get_ratchet_pq_identity_secret())
        assertNull(store.get_ratchet_pq_identity_public())
    }

    @Test
    fun `full vault stores every ratchet field`() {
        val store = store_for(
            vault(
                "ratchet_identity_key" to "identity_jwk_value",
                "ratchet_identity_public" to "identity_public_value",
                "ratchet_signed_prekey" to "spk_jwk_value",
                "ratchet_signed_prekey_public" to "spk_public_value",
                "ratchet_pq_identity_key" to "pq_secret_value",
                "ratchet_pq_identity_public" to "pq_public_value",
            ),
        )
        assertEquals("identity_jwk_value", store.get_ratchet_identity_jwk())
        assertEquals("identity_public_value", store.get_ratchet_identity_public_b64())
        assertEquals("spk_jwk_value", store.get_ratchet_signed_prekey_jwk())
        assertEquals("spk_public_value", store.get_ratchet_signed_prekey_public_b64())
        assertEquals("pq_secret_value", store.get_ratchet_pq_identity_secret())
        assertEquals("pq_public_value", store.get_ratchet_pq_identity_public())
    }

    @Test
    fun `empty vault stores nothing`() {
        val store = store_for(JSONObject())
        assertNull(store.get_ratchet_identity_jwk())
        assertNull(store.get_ratchet_signed_prekey_jwk())
        assertNull(store.get_ratchet_pq_identity_secret())
        assertNull(store.get_ratchet_previous_keys_json())
    }

    @Test
    fun `blank fields are treated as absent`() {
        val store = store_for(
            vault(
                "ratchet_identity_key" to "identity_jwk_value",
                "ratchet_signed_prekey" to "",
                "ratchet_signed_prekey_public" to "   ",
                "ratchet_pq_identity_key" to "",
            ),
        )
        assertEquals("identity_jwk_value", store.get_ratchet_identity_jwk())
        assertNull(store.get_ratchet_signed_prekey_jwk())
        assertNull(store.get_ratchet_signed_prekey_public_b64())
        assertNull(store.get_ratchet_pq_identity_secret())
    }

    @Test
    fun `previous keys array is carried through`() {
        val obj = JSONObject()
        obj.put("ratchet_identity_key", "identity_jwk_value")
        obj.put("ratchet_previous_keys", org.json.JSONArray().put(JSONObject().put("ratchet_identity_key", "old_jwk")))
        val store = store_for(obj)
        val previous = store.get_ratchet_previous_keys_json()
        assertEquals(true, previous?.contains("old_jwk"))
    }
}
