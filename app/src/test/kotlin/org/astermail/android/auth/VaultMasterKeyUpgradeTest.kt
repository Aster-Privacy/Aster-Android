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

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VaultMasterKeyUpgradeTest {

    private val old_storage_key = ByteArray(32) { it.toByte() }

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    private fun upgrade(vault_obj: JSONObject, key: ByteArray = old_storage_key): ByteArray =
        ensure_master_key_vault(
            vault_obj = vault_obj,
            derive_storage_key = { key.copyOf() },
            encode = ::encode,
            decode = ::decode,
            now = { "2026-08-21T00:00:00Z" },
        )

    @Test
    fun `legacy vault keeps the current storage key so a password change cannot orphan aliases`() {
        val vault_obj = JSONObject()

        val preserved = upgrade(vault_obj)

        assertArrayEquals(old_storage_key, preserved)
        assertEquals(MASTER_KEY_VAULT_FORMAT, vault_obj.getInt("vault_format"))
        assertEquals(encode(old_storage_key), vault_obj.getString("data_kek"))
        assertEquals(
            encode(old_storage_key),
            vault_obj.getJSONArray("legacy_keks").getJSONObject(0).getString("k"),
        )
    }

    @Test
    fun `existing master key vault is returned untouched`() {
        val existing = ByteArray(32) { 7 }
        val vault_obj = JSONObject()
            .put("data_kek", encode(existing))
            .put("vault_format", MASTER_KEY_VAULT_FORMAT)

        val preserved = upgrade(vault_obj)

        assertArrayEquals(existing, preserved)
        assertEquals(encode(existing), vault_obj.getString("data_kek"))
        assertTrue(vault_obj.optJSONArray("legacy_keks") == null)
    }

    @Test
    fun `unusable data key is replaced by the current storage key`() {
        val vault_obj = JSONObject()
            .put("data_kek", "not-base64")
            .put("vault_format", MASTER_KEY_VAULT_FORMAT)

        val preserved = upgrade(vault_obj)

        assertArrayEquals(old_storage_key, preserved)
        assertEquals(encode(old_storage_key), vault_obj.getString("data_kek"))
    }

    @Test
    fun `legacy key list keeps earlier entries and stays bounded`() {
        val vault_obj = JSONObject()
        var previous = upgrade(vault_obj, ByteArray(32) { 1 })

        for (generation in 2..20) {
            vault_obj.remove("data_kek")
            previous = upgrade(vault_obj, ByteArray(32) { generation.toByte() })
        }

        val list = vault_obj.getJSONArray("legacy_keks")

        assertEquals(MAX_LEGACY_KEKS, list.length())
        assertEquals(encode(previous), list.getJSONObject(0).getString("k"))
    }

    @Test(expected = UnsupportedVaultKdfException::class)
    fun `a vault on an unsupported storage kdf refuses to fabricate a data key`() {
        val vault_obj = JSONObject().put("kdf_version", 2)

        upgrade(vault_obj)
    }

    @Test
    fun `an unsupported storage kdf is left untouched when it refuses`() {
        val vault_obj = JSONObject().put("kdf_version", 2)

        runCatching { upgrade(vault_obj) }

        assertTrue(vault_obj.optString("data_kek", "").isBlank())
        assertEquals(1, vault_obj.optInt("vault_format", 1))
    }

    @Test
    fun `an unsupported storage kdf still returns an already stored data key`() {
        val existing = ByteArray(32) { 9 }
        val vault_obj = JSONObject()
            .put("kdf_version", 2)
            .put("data_kek", encode(existing))
            .put("vault_format", MASTER_KEY_VAULT_FORMAT)

        assertArrayEquals(existing, upgrade(vault_obj))
    }
}
