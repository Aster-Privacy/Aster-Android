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
package org.astermail.android.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSaltMemoryTest {

    private fun vault_b64(seed: Int): String =
        java.util.Base64.getEncoder().encodeToString(ByteArray(80) { (it + seed).toByte() })

    private fun salt_of(seed: Int): ByteArray = ByteArray(16) { (it + seed).toByte() }

    @Test
    fun `a vault salt stays remembered after the session is cleared`() {
        val store = SessionKeyStore()
        store.put_encrypted_vault(vault_b64(3), "nonce")
        store.clear()
        val remembered = store.get_remembered_vault_salts()
        assertEquals(1, remembered.size)
        assertArrayEquals(salt_of(3), remembered.single())
    }

    @Test
    fun `every vault put in the store is remembered once`() {
        val store = SessionKeyStore()
        store.put_encrypted_vault(vault_b64(1), "nonce")
        store.put_encrypted_vault(vault_b64(2), "nonce")
        store.put_encrypted_vault(vault_b64(1), "nonce")
        val remembered = store.get_remembered_vault_salts()
        assertEquals(2, remembered.size)
        assertArrayEquals(salt_of(2), remembered[0])
        assertArrayEquals(salt_of(1), remembered[1])
    }

    @Test
    fun `the remembered list is bounded and survives a round trip`() {
        var entries = emptyList<String>()
        for (seed in 0 until VaultSaltMemory.max_entries + 5) {
            entries = VaultSaltMemory.remember(entries, vault_b64(seed))
        }
        assertEquals(VaultSaltMemory.max_entries, entries.size)
        val restored = VaultSaltMemory.parse(VaultSaltMemory.serialize(entries))
        assertEquals(entries, restored)
        assertArrayEquals(salt_of(VaultSaltMemory.max_entries + 4), VaultSaltMemory.decode(restored).last())
    }

    @Test
    fun `a vault too short to carry a salt is ignored`() {
        val short_vault = java.util.Base64.getEncoder().encodeToString(ByteArray(16))
        assertTrue(VaultSaltMemory.remember(emptyList(), short_vault).isEmpty())
        assertTrue(VaultSaltMemory.remember(emptyList(), "").isEmpty())
    }
}
