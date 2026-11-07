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

import kotlinx.coroutines.runBlocking
import org.astermail.android.api.keys.CurrentVaultResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingIdentityKeyInstallTest {

    private val new_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nnew\n-----END PGP PRIVATE KEY BLOCK-----"
    private val added_at = "2026-11-07T00:00:00Z"

    private class FakePort(
        private val server: CurrentVaultResult,
        private val vault: JSONObject?,
        private val generated: String?,
        private val save_succeeds: Boolean = true,
    ) : IdentityKeyInstallPort {
        var generate_calls = 0
        var saved: JSONObject? = null

        override suspend fun fetch_server_vault(): CurrentVaultResult = server
        override fun open_vault(sealed: SealedVault): JSONObject? = vault?.let { JSONObject(it.toString()) }
        override fun generate_identity_key(): String? {
            generate_calls += 1
            return generated
        }
        override fun seal_vault(vault: JSONObject): SealedVault = SealedVault("sealed", "nonce")
        override suspend fun save_vault(sealed: SealedVault, vault: JSONObject): Boolean {
            if (save_succeeds) saved = JSONObject(vault.toString())
            return save_succeeds
        }
    }

    private val available = CurrentVaultResult.Available("server", "server_nonce")

    private fun keyless_vault(): JSONObject = JSONObject()
        .put("version", 1)
        .put("identity_private_key", "b2xkLWlkZW50aXR5")
        .put("data_kek", "ZGF0YS1rZWs=")
        .put("legacy_keks", JSONArray().put(JSONObject().put("k", "ZXhpc3Rpbmc=").put("added_at", "2026-01-01T00:00:00Z")))

    private fun legacy_kek_values(vault: JSONObject): List<String> {
        val array = vault.getJSONArray("legacy_keks")
        return (0 until array.length()).map { array.getJSONObject(it).getString("k") }
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
    fun `keyless server vault gets a new key and is saved`() = runBlocking {
        val port = FakePort(available, keyless_vault(), new_key)

        val result = install_missing_identity_key(port) { added_at }

        assertTrue(result is IdentityKeyInstallResult.Installed)
        result as IdentityKeyInstallResult.Installed
        assertEquals(new_key, result.identity_key)
        assertEquals(SealedVault("sealed", "nonce"), result.sealed)
        assertEquals(new_key, port.saved?.getString("identity_key"))
        assertTrue(legacy_kek_values(port.saved!!).containsAll(missing_identity_keks()))
    }

    @Test
    fun `server vault that already holds a key is adopted without generating`() = runBlocking {
        val vault = keyless_vault().put("identity_key", new_key)
        val port = FakePort(available, vault, "unused")

        val result = install_missing_identity_key(port) { added_at }

        assertTrue(result is IdentityKeyInstallResult.AlreadyInstalled)
        assertEquals(SealedVault("server", "server_nonce"), (result as IdentityKeyInstallResult.AlreadyInstalled).sealed)
        assertEquals(0, port.generate_calls)
        assertEquals(null, port.saved)
    }

    @Test
    fun `unverified or missing server vault is never written`() = runBlocking {
        for (server in listOf(CurrentVaultResult.Unavailable, CurrentVaultResult.Missing)) {
            val port = FakePort(server, keyless_vault(), new_key)

            assertSame(IdentityKeyInstallResult.NotWritten, install_missing_identity_key(port) { added_at })
            assertEquals(0, port.generate_calls)
        }
    }

    @Test
    fun `vault that does not open is never written`() = runBlocking {
        val port = FakePort(available, null, new_key)

        assertSame(IdentityKeyInstallResult.NotWritten, install_missing_identity_key(port) { added_at })
        assertEquals(0, port.generate_calls)
    }

    @Test
    fun `failed key generation or save reports not written`() = runBlocking {
        assertSame(
            IdentityKeyInstallResult.NotWritten,
            install_missing_identity_key(FakePort(available, keyless_vault(), null)) { added_at },
        )
        assertSame(
            IdentityKeyInstallResult.NotWritten,
            install_missing_identity_key(FakePort(available, keyless_vault(), "not a pgp key")) { added_at },
        )
        assertSame(
            IdentityKeyInstallResult.NotWritten,
            install_missing_identity_key(FakePort(available, keyless_vault(), new_key, save_succeeds = false)) { added_at },
        )
    }

    @Test
    fun `only a pgp identity key counts as installed`() {
        assertFalse(vault_holds_pgp_identity_key(JSONObject().put("identity_key", "b2xk")))
        assertTrue(vault_holds_pgp_identity_key(JSONObject().put("identity_private_key", new_key)))
        assertTrue(vault_holds_pgp_identity_key(JSONObject().put("identity_key", new_key)))
    }
}
