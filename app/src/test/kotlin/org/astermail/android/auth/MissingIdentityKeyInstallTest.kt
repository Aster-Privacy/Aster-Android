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

import org.astermail.android.crypto.storage_rotation.VaultEdit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingIdentityKeyInstallTest {

    private val new_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nnew\n-----END PGP PRIVATE KEY BLOCK-----"
    private val added_at = "2026-11-07T00:00:00Z"

    private fun keyless_vault(): JSONObject = JSONObject()
        .put("version", 1)
        .put("identity_private_key", "b2xkLWlkZW50aXR5")
        .put("data_kek", "ZGF0YS1rZWs=")
        .put("legacy_keks", JSONArray().put(JSONObject().put("k", "ZXhpc3Rpbmc=").put("added_at", "2026-01-01T00:00:00Z")))

    private fun legacy_kek_values(vault: JSONObject): List<String> {
        val array = vault.getJSONArray("legacy_keks")
        return (0 until array.length()).map { array.getJSONObject(it).getString("k") }
    }

    private class CountingGenerator(private val result: String?) {
        var calls = 0
        fun generate(): String? {
            calls += 1
            return result
        }
    }

    @Test
    fun `missing identity keks match the web derivation for every account data context`() {
        val keks = missing_identity_keks()

        assertEquals(11, keks.size)
        assertEquals("WKk77GlgzyLDRiwNmKtEtEX85tjGtdOENdEryQSIXqw=", keks.first())
        assertEquals("GNbUVg+Tah//5+O3y8zjDBgwM0SfByzozJ/wMJpQikk=", keks.last())
    }

    @Test
    fun `install keeps old key material readable and sets the new identity key`() {
        val vault = keyless_vault()

        install_identity_key_into_vault(vault, new_key, added_at)

        assertEquals(new_key, vault.getString("identity_key"))
        assertEquals("b2xkLWlkZW50aXR5", vault.getString("identity_private_key"))
        assertEquals("ZGF0YS1rZWs=", vault.getString("data_kek"))
        assertEquals(listOf("b2xkLWlkZW50aXR5"), json_strings(vault.optJSONArray("legacy_identity_keys")))
        val keks = legacy_kek_values(vault)
        assertTrue(keks.containsAll(missing_identity_keks()))
        assertTrue(keks.contains("ZXhpc3Rpbmc="))
        assertEquals(keks.size, keks.toSet().size)
    }

    @Test
    fun `installing twice does not duplicate legacy keks`() {
        val vault = keyless_vault()

        install_identity_key_into_vault(vault, new_key, added_at)
        val first = legacy_kek_values(vault).toSet()
        install_identity_key_into_vault(vault, new_key, added_at)

        assertEquals(first, legacy_kek_values(vault).toSet())
        assertEquals(new_key, vault.getString("identity_key"))
    }

    @Test
    fun `keyless vault gets a new key`() {
        val generator = CountingGenerator(new_key)
        val edit = MissingIdentityKeyEdit(generator::generate) { added_at }
        val vault = keyless_vault()

        assertEquals(VaultEdit.CHANGED, edit.apply(vault))
        assertEquals(new_key, edit.installed_key)
        assertEquals(new_key, vault.getString("identity_key"))
        assertTrue(legacy_kek_values(vault).containsAll(missing_identity_keks()))
    }

    @Test
    fun `retried edit reuses the generated key`() {
        val generator = CountingGenerator(new_key)
        val edit = MissingIdentityKeyEdit(generator::generate) { added_at }

        assertEquals(VaultEdit.CHANGED, edit.apply(keyless_vault()))
        assertEquals(VaultEdit.CHANGED, edit.apply(keyless_vault()))
        assertEquals(1, generator.calls)
    }

    @Test
    fun `vault that already holds a key is left unchanged without generating`() {
        val generator = CountingGenerator("unused")
        val edit = MissingIdentityKeyEdit(generator::generate) { added_at }
        val vault = keyless_vault().put("identity_key", new_key)

        assertEquals(VaultEdit.UNCHANGED, edit.apply(vault))
        assertEquals(0, generator.calls)
        assertNull(edit.installed_key)
    }

    @Test
    fun `key installed by another device on retry clears the pending install`() {
        val edit = MissingIdentityKeyEdit(CountingGenerator(new_key)::generate) { added_at }

        assertEquals(VaultEdit.CHANGED, edit.apply(keyless_vault()))
        assertEquals(VaultEdit.UNCHANGED, edit.apply(keyless_vault().put("identity_key", new_key)))
        assertNull(edit.installed_key)
    }

    @Test
    fun `failed or invalid key generation aborts`() {
        for (generated in listOf(null, "not a pgp key")) {
            val edit = MissingIdentityKeyEdit(CountingGenerator(generated)::generate) { added_at }
            val vault = keyless_vault()

            assertEquals(VaultEdit.ABORT, edit.apply(vault))
            assertNull(edit.installed_key)
            assertFalse(vault.has("identity_key"))
        }
    }

    @Test
    fun `only a pgp identity key counts as installed`() {
        assertFalse(vault_holds_pgp_identity_key(JSONObject().put("identity_key", "b2xk")))
        assertTrue(vault_holds_pgp_identity_key(JSONObject().put("identity_private_key", new_key)))
        assertTrue(vault_holds_pgp_identity_key(JSONObject().put("identity_key", new_key)))
    }
}
