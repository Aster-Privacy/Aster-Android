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

package org.astermail.android.crypto.storage_rotation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMarkerMergeTest {

    private val now = "2026-11-17T00:00:00Z"

    private fun marker(v: Int, at: String) = JSONObject().put("v", v).put("at", at)

    @Test
    fun retirement_marker_survives_a_writer_that_does_not_know_it() {
        val server = JSONObject().put("mail_legacy_retired", marker(1, "server"))
        val next = JSONObject().put("identity_key", "k")

        preserve_vault_fields(server, next, LocalStorageKeys.NONE, false, now)

        assertEquals(1, next.getJSONObject("mail_legacy_retired").getInt("v"))
    }

    @Test
    fun retirement_marker_keeps_the_highest_version() {
        val server = JSONObject().put("mail_legacy_retired", marker(2, "server"))
        val next = JSONObject().put("mail_legacy_retired", marker(1, "stale"))

        preserve_vault_fields(server, next, LocalStorageKeys.NONE, false, now)

        val kept = next.getJSONObject("mail_legacy_retired")
        assertEquals(2, kept.getInt("v"))
        assertEquals("server", kept.getString("at"))
    }

    @Test
    fun retirement_marker_is_not_lowered_by_an_invalid_write() {
        val server = JSONObject().put("mail_legacy_retired", marker(1, "server"))
        val next = JSONObject().put("mail_legacy_retired", JSONObject().put("v", 0))

        preserve_vault_fields(server, next, LocalStorageKeys.NONE, false, now)

        assertEquals(1, next.getJSONObject("mail_legacy_retired").getInt("v"))
    }

    @Test
    fun folder_pins_merge_by_maximum_per_folder() {
        val server = JSONObject().put("folder_key_pins", JSONObject().put("aaa", 2).put("bbb", 1))
        val next = JSONObject().put("folder_key_pins", JSONObject().put("aaa", 1).put("ccc", 2))

        preserve_vault_fields(server, next, LocalStorageKeys.NONE, false, now)

        val pins = next.getJSONObject("folder_key_pins")
        assertEquals(2, pins.getInt("aaa"))
        assertEquals(1, pins.getInt("bbb"))
        assertEquals(2, pins.getInt("ccc"))
    }

    @Test
    fun unknown_fields_are_kept() {
        val server = JSONObject().put("future_field", "x").put("folder_key_pins", JSONObject().put("aaa", 1))
        val next = JSONObject()

        preserve_vault_fields(server, next, LocalStorageKeys.NONE, false, now)

        assertEquals("x", next.getString("future_field"))
        assertTrue(next.getJSONObject("folder_key_pins").has("aaa"))
    }
}
