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

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VaultMasterKeyUpgradeTest {

    private val old_storage_key = ByteArray(32) { it.toByte() }

    private val random_key = ByteArray(32) { (it + 100).toByte() }

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    private fun upgrade(
        vault_obj: JSONObject,
        key: ByteArray = old_storage_key,
        fresh: ByteArray = random_key,
    ): ByteArray =
        ensure_master_key_vault(
            vault_obj = vault_obj,
            derive_storage_key = { key.copyOf() },
            encode = ::encode,
            decode = ::decode,
            now = { "2026-08-21T00:00:00Z" },
            generate = { fresh.copyOf() },
        )

    private fun legacy_values(vault_obj: JSONObject): List<String> = vault_legacy_kek_values(vault_obj)

    @Test
    fun `legacy vault gets a random data key and keeps the password key as a legacy key`() {
        val vault_obj = JSONObject()

        val data_kek = upgrade(vault_obj)

        assertArrayEquals(random_key, data_kek)
        assertFalse(data_kek.contentEquals(old_storage_key))
        assertEquals(MASTER_KEY_VAULT_FORMAT, vault_obj.getInt("vault_format"))
        assertEquals(encode(random_key), vault_obj.getString("data_kek"))
        assertTrue(vault_obj.getBoolean("data_kek_random"))
        assertEquals("2026-08-21T00:00:00Z", vault_obj.getString("mk_created_at"))
        assertEquals(listOf(encode(old_storage_key)), legacy_values(vault_obj))
    }

    @Test
    fun `upgrade puts the old key in front of existing legacy keys and keeps unknown fields`() {
        val earlier = encode(ByteArray(32) { 3 })
        val vault_obj = JSONObject()
            .put("identity_key", "identity")
            .put("future_field", JSONObject().put("x", 1))
            .put(
                "legacy_keks",
                JSONArray().put(JSONObject().put("k", earlier).put("added_at", "2026-01-01T00:00:00Z")),
            )

        upgrade(vault_obj)

        assertEquals(listOf(encode(old_storage_key), earlier), legacy_values(vault_obj))
        assertEquals("identity", vault_obj.getString("identity_key"))
        assertEquals(1, vault_obj.getJSONObject("future_field").getInt("x"))
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
    fun `unusable data key is replaced by a random key`() {
        val vault_obj = JSONObject()
            .put("data_kek", "not-base64")
            .put("vault_format", MASTER_KEY_VAULT_FORMAT)

        val preserved = upgrade(vault_obj)

        assertArrayEquals(random_key, preserved)
        assertEquals(encode(random_key), vault_obj.getString("data_kek"))
        assertEquals(listOf(encode(old_storage_key)), legacy_values(vault_obj))
    }

    @Test
    fun `legacy key list keeps earlier entries and stays bounded`() {
        val vault_obj = JSONObject()
        var retired = ByteArray(32) { 1 }
        upgrade(vault_obj, retired, ByteArray(32) { 101 })

        for (generation in 2..(MAX_LEGACY_KEKS + 5)) {
            vault_obj.remove("data_kek")
            retired = ByteArray(32) { generation.toByte() }
            upgrade(vault_obj, retired, ByteArray(32) { (generation + 100).toByte() })
        }

        val list = legacy_values(vault_obj)

        assertEquals(MAX_LEGACY_KEKS, list.size)
        assertEquals(encode(retired), list[0])
        assertEquals(list.size, list.toSet().size)
    }

    @Test
    fun `the data key never appears in the legacy list`() {
        val vault_obj = JSONObject()
            .put("legacy_keks", JSONArray().put(JSONObject().put("k", encode(random_key))))

        upgrade(vault_obj)

        assertFalse(legacy_values(vault_obj).contains(encode(random_key)))
    }

    @Test
    fun `sign up installs a random data key with the new fields`() {
        val vault_obj = JSONObject().put("identity_key", "identity")

        install_signup_data_kek(vault_obj, encode(random_key)) { "2026-11-06T00:00:00Z" }

        assertEquals(encode(random_key), vault_obj.getString("data_kek"))
        assertTrue(vault_obj.getBoolean("data_kek_random"))
        assertEquals(MASTER_KEY_VAULT_FORMAT, vault_obj.getInt("vault_format"))
        assertEquals("2026-11-06T00:00:00Z", vault_obj.getString("mk_created_at"))
        assertEquals("identity", vault_obj.getString("identity_key"))
    }

    @Test
    fun `sign up keeps an existing creation time`() {
        val vault_obj = JSONObject().put("mk_created_at", "2026-01-01T00:00:00Z")

        install_signup_data_kek(vault_obj, encode(random_key)) { "2026-11-06T00:00:00Z" }

        assertEquals("2026-01-01T00:00:00Z", vault_obj.getString("mk_created_at"))
    }

    @Test(expected = UnsupportedVaultKdfException::class)
    fun `a vault on an unsupported storage kdf refuses to fabricate a data key`() {
        val vault_obj = JSONObject().put("kdf_version", 2)

        upgrade(vault_obj)
    }

    @Test(expected = UnsupportedVaultKdfException::class)
    fun `a vault on an unknown storage kdf refuses to fabricate a data key`() {
        val vault_obj = JSONObject().put("kdf_version", 7)

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
