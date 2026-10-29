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
import org.junit.Assert.assertTrue
import org.junit.Test

class InactiveKeyRestoreTest {

    private val added_at = "2026-08-27T00:00:00Z"

    private fun keks(array: JSONArray): List<String> =
        (0 until array.length()).map { array.getJSONObject(it).getString("k") }

    @Test
    fun harvests_the_archived_data_kek_first() {
        val old_vault = JSONObject()
            .put("data_kek", "archived")
            .put("vault_format", MASTER_KEY_VAULT_FORMAT)

        assertEquals(listOf("archived", "derived"), harvest_storage_keks(old_vault, "derived"))
    }

    @Test
    fun harvests_the_legacy_keks_the_archived_vault_carried() {
        val old_vault = JSONObject()
            .put("data_kek", "archived")
            .put(
                "legacy_keks",
                JSONArray()
                    .put(JSONObject().put("k", "older").put("added_at", added_at))
                    .put(JSONObject().put("k", "oldest").put("added_at", added_at)),
            )

        assertEquals(
            listOf("archived", "older", "oldest", "derived"),
            harvest_storage_keks(old_vault, "derived"),
        )
    }

    @Test
    fun skips_the_derived_key_when_the_archived_vault_uses_an_unsupported_kdf() {
        val old_vault = JSONObject()
            .put("data_kek", "archived")
            .put("kdf_version", SUPPORTED_STORAGE_KDF_VERSION + 1)

        assertEquals(listOf("archived"), harvest_storage_keks(old_vault, "derived"))
    }

    @Test
    fun harvests_only_the_derived_key_for_a_legacy_archived_vault() {
        assertEquals(listOf("derived"), harvest_storage_keks(JSONObject(), "derived"))
    }

    @Test
    fun harvests_nothing_when_no_key_is_available() {
        assertTrue(harvest_storage_keks(JSONObject(), null).isEmpty())
    }

    @Test
    fun merges_recovered_keks_after_the_ones_the_vault_held() {
        val existing = JSONArray().put(JSONObject().put("k", "held").put("added_at", added_at))

        val merged = merge_legacy_keks_reporting(existing, listOf("recovered"), added_at)

        assertEquals(listOf("held", "recovered"), keks(merged.keks))
        assertEquals(0, merged.dropped)
    }

    @Test
    fun merges_without_duplicating_a_kek_the_vault_already_held() {
        val existing = JSONArray().put(JSONObject().put("k", "held").put("added_at", added_at))

        val merged = merge_legacy_keks(existing, listOf("held", "recovered"), added_at)

        assertEquals(listOf("held", "recovered"), keks(merged))
    }

    @Test
    fun caps_the_merged_kek_list_and_reports_what_it_dropped() {
        val existing = JSONArray()
        for (i in 0 until MAX_LEGACY_KEKS) {
            existing.put(JSONObject().put("k", "held_$i").put("added_at", added_at))
        }

        val merged = merge_legacy_keks_reporting(existing, listOf("recovered", "held_0"), added_at)

        assertEquals(MAX_LEGACY_KEKS, merged.keks.length())
        assertEquals("held_0", merged.keks.getJSONObject(0).getString("k"))
        assertTrue(keks(merged.keks).none { it == "recovered" })
        assertEquals(1, merged.dropped)
    }

    @Test
    fun the_kek_cap_is_at_least_the_web_cap() {
        assertTrue(MAX_LEGACY_KEKS >= 64)
    }

    @Test
    fun retains_the_archived_ratchet_identity_and_its_history() {
        val old_vault = JSONObject()
            .put("ratchet_identity_key", "ik")
            .put("ratchet_identity_public", "ipub")
            .put("ratchet_signed_prekey", "spk")
            .put("ratchet_signed_prekey_public", "spkpub")
            .put("ratchet_pq_identity_seed", "seed")
            .put(
                "ratchet_previous_keys",
                JSONArray().put(JSONObject().put("ratchet_identity_public", "older_pub")),
            )

        val retained = retain_previous_ratchet_keys(old_vault)

        assertEquals(2, retained.size)
        assertEquals("ipub", retained[0].getString("ratchet_identity_public"))
        assertEquals("seed", retained[0].getString("ratchet_pq_identity_seed"))
        assertEquals("older_pub", retained[1].getString("ratchet_identity_public"))
    }

    @Test
    fun retains_nothing_when_the_archived_vault_has_no_complete_identity() {
        val old_vault = JSONObject().put("ratchet_identity_key", "ik")

        assertTrue(retain_previous_ratchet_keys(old_vault).isEmpty())
    }

    @Test
    fun merges_previous_ratchet_keys_without_duplicates() {
        val existing = JSONArray().put(JSONObject().put("ratchet_identity_public", "a"))
        val recovered = listOf(
            JSONObject().put("ratchet_identity_public", "a"),
            JSONObject().put("ratchet_identity_public", "b"),
        )

        val merged = merge_previous_ratchet_keys(existing, recovered)

        assertEquals(2, merged.length())
        assertEquals("a", merged.getJSONObject(0).getString("ratchet_identity_public"))
        assertEquals("b", merged.getJSONObject(1).getString("ratchet_identity_public"))
    }

    @Test
    fun keeps_every_recovered_ratchet_key_past_the_rotation_cap() {
        val existing = JSONArray()
        for (i in 0 until 32) {
            existing.put(JSONObject().put("ratchet_identity_public", "pub_$i"))
        }

        val merged = merge_previous_ratchet_keys_reporting(
            existing,
            listOf(
                JSONObject().put("ratchet_identity_public", "pub_0"),
                JSONObject().put("ratchet_identity_public", "extra"),
            ),
        )

        assertEquals(33, merged.keys.length())
        assertEquals("extra", merged.keys.getJSONObject(32).getString("ratchet_identity_public"))
        assertEquals(0, merged.dropped)
    }

    @Test
    fun keeps_post_quantum_entries_that_carry_no_identity_key() {
        val merged = merge_previous_ratchet_keys(
            JSONArray().put(JSONObject().put("ratchet_identity_public", "a")),
            listOf(
                JSONObject().put("ratchet_pq_identity_key", "pq_secret").put("ratchet_pq_identity_public", "pq_pub"),
                JSONObject().put("ratchet_pq_identity_public", "pq_pub"),
            ),
        )

        assertEquals(2, merged.length())
        assertEquals("pq_secret", merged.getJSONObject(1).getString("ratchet_pq_identity_key"))
    }

    @Test
    fun keeps_a_rotated_signed_prekey_under_the_same_identity() {
        val merged = merge_previous_ratchet_keys(
            JSONArray().put(JSONObject().put("ratchet_identity_public", "a").put("ratchet_signed_prekey_public", "spk1")),
            listOf(JSONObject().put("ratchet_identity_public", "a").put("ratchet_signed_prekey_public", "spk2")),
        )

        assertEquals(2, merged.length())
    }
}
