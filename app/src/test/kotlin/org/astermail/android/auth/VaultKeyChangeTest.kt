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

package org.astermail.android.auth

import org.astermail.android.storage.SessionKeyStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultKeyChangeTest {

    private fun store_with_keys(): SessionKeyStore {
        val store = SessionKeyStore(null)
        store.put_identity_key("identity_value")
        store.put_ratchet_keys(
            identity_jwk = "identity_jwk_value",
            identity_public_b64 = "identity_public_value",
            signed_prekey_jwk = null,
            signed_prekey_public_b64 = null,
        )
        store.put_ratchet_previous_keys("""[{"ratchet_identity_key":"old_jwk"}]""")
        store.put_ratchet_pq_identity("pq_secret_value", "pq_public_value")
        return store
    }

    @Test
    fun `unchanged store reports no change`() {
        val store = store_with_keys()
        val before = vault_key_snapshot(store)
        val after = vault_key_snapshot(store)
        assertFalse(vault_keys_changed(before, after))
    }

    @Test
    fun `pq secret only rotation reports change`() {
        val store = store_with_keys()
        val before = vault_key_snapshot(store)
        store.put_ratchet_pq_identity("pq_secret_rotated", "pq_public_value")
        assertTrue(vault_keys_changed(before, vault_key_snapshot(store)))
    }

    @Test
    fun `pq public only rotation reports change`() {
        val store = store_with_keys()
        val before = vault_key_snapshot(store)
        store.put_ratchet_pq_identity("pq_secret_value", "pq_public_rotated")
        assertTrue(vault_keys_changed(before, vault_key_snapshot(store)))
    }

    @Test
    fun `pq identity appearing for the first time reports change`() {
        val store = SessionKeyStore(null)
        val before = vault_key_snapshot(store)
        store.put_ratchet_pq_identity("pq_secret_value", null)
        assertTrue(vault_keys_changed(before, vault_key_snapshot(store)))
    }

    @Test
    fun `ratchet identity rotation reports change`() {
        val store = store_with_keys()
        val before = vault_key_snapshot(store)
        store.put_ratchet_keys(
            identity_jwk = "identity_jwk_rotated",
            identity_public_b64 = "identity_public_rotated",
            signed_prekey_jwk = null,
            signed_prekey_public_b64 = null,
        )
        assertTrue(vault_keys_changed(before, vault_key_snapshot(store)))
    }

    @Test
    fun `previous keys growth reports change`() {
        val store = store_with_keys()
        val before = vault_key_snapshot(store)
        store.put_ratchet_previous_keys(
            """[{"ratchet_identity_key":"old_jwk"},{"ratchet_identity_key":"older_jwk"}]""",
        )
        assertTrue(vault_keys_changed(before, vault_key_snapshot(store)))
    }

    @Test
    fun `identity key rotation reports change`() {
        val store = store_with_keys()
        val before = vault_key_snapshot(store)
        store.put_identity_key("identity_rotated")
        assertTrue(vault_keys_changed(before, vault_key_snapshot(store)))
    }
}
