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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFieldPreservationTest {

    private fun upgraded_vault(): JSONObject = JSONObject()
        .put("identity_key", "identity")
        .put("data_kek", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .put("data_kek_random", true)
        .put("vault_format", MASTER_KEY_VAULT_FORMAT)
        .put("mk_created_at", "2026-11-06T00:00:00Z")
        .put("future_field", JSONObject().put("x", 1))
        .put(
            "legacy_keks",
            JSONArray()
                .put(JSONObject().put("k", "retired").put("added_at", "2026-11-06T00:00:00Z"))
                .put(JSONObject().put("k", "older").put("added_at", "2026-01-01T00:00:00Z")),
        )

    private fun assert_master_key_fields(vault_obj: JSONObject) {
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", vault_obj.getString("data_kek"))
        assertTrue(vault_obj.getBoolean("data_kek_random"))
        assertEquals(MASTER_KEY_VAULT_FORMAT, vault_obj.getInt("vault_format"))
        assertEquals("2026-11-06T00:00:00Z", vault_obj.getString("mk_created_at"))
        assertEquals(1, vault_obj.getJSONObject("future_field").getInt("x"))
    }

    @Test
    fun `recovered key merge keeps the retiring key first and adds recovered keys after it`() {
        val vault_obj = upgraded_vault()

        val merged = merge_legacy_keks_reporting(
            vault_obj.optJSONArray("legacy_keks"),
            listOf("recovered", "older"),
            "2026-11-07T00:00:00Z",
        )
        vault_obj.put("legacy_keks", merged.keks)

        assertEquals(listOf("retired", "older", "recovered"), vault_legacy_kek_values(vault_obj))
        assert_master_key_fields(vault_obj)
    }

    @Test
    fun `backup copy keeps the master key fields`() {
        val vault_obj = upgraded_vault().put(BACKUP_UNLOCKED_KEYS_FIELD, JSONArray())

        val stripped = strip_backup_fields(vault_obj)

        assertFalse(stripped.has(BACKUP_UNLOCKED_KEYS_FIELD))
        assert_master_key_fields(stripped)
        assertEquals(listOf("retired", "older"), vault_legacy_kek_values(stripped))
    }

    @Test
    fun `the vault upload reports the master key format`() {
        assertEquals(MASTER_KEY_VAULT_FORMAT, stored_vault_format(upgraded_vault()))
        assertEquals(MASTER_KEY_VAULT_FORMAT, stored_vault_format(JSONObject().put("data_kek", "x")))
        assertEquals(1, stored_vault_format(JSONObject()))
    }

    @Test
    fun `an upgrade of an upgraded vault changes nothing`() {
        val vault_obj = upgraded_vault()
        val before = vault_obj.toString()

        ensure_master_key_vault(
            vault_obj = vault_obj,
            derive_storage_key = { ByteArray(32) { 1 } },
            encode = { java.util.Base64.getEncoder().encodeToString(it) },
            decode = { java.util.Base64.getDecoder().decode(it) },
            now = { "2026-12-01T00:00:00Z" },
            generate = { ByteArray(32) { 2 } },
        )

        assertEquals(before, vault_obj.toString())
    }
}
