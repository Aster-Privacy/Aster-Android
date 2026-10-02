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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupUnlockedKeysTest {
    private val passphrase = "pass".toCharArray()

    private fun vault(identity: String = "locked-current", previous: List<String> = emptyList()): JSONObject {
        val vault = JSONObject().put("identity_key", identity)
        if (previous.isNotEmpty()) vault.put("previous_keys", JSONArray(previous))
        return vault
    }

    private fun pairs(vault: JSONObject): List<Pair<String, String>> {
        val array = vault.optJSONArray(BACKUP_UNLOCKED_KEYS_FIELD) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONArray(it).getString(0) to array.getJSONArray(it).getString(1) }
    }

    @Test
    fun strip_removes_the_backup_field_without_touching_the_source() {
        val source = vault().put(BACKUP_UNLOCKED_KEYS_FIELD, JSONArray().put(JSONArray().put("a").put("b")))

        val stripped = strip_backup_fields(source)

        assertNotSame(source, stripped)
        assertFalse(stripped.has(BACKUP_UNLOCKED_KEYS_FIELD))
        assertTrue(source.has(BACKUP_UNLOCKED_KEYS_FIELD))
        assertEquals("locked-current", stripped.getString("identity_key"))
    }

    @Test
    fun read_skips_malformed_pairs() {
        val source = JSONObject().put(
            BACKUP_UNLOCKED_KEYS_FIELD,
            JSONArray()
                .put(JSONArray().put("locked-a").put("open-a"))
                .put(JSONArray().put("only-one"))
                .put(JSONArray().put("").put("open-b"))
                .put(JSONArray().put("locked-c").put(""))
                .put(JSONArray().put("locked-d").put(7))
                .put("not-a-pair")
                .put(JSONArray().put("locked-e").put("open-e")),
        )

        assertEquals(mapOf("locked-a" to "open-a", "locked-e" to "open-e"), read_unlocked_keys(source))
    }

    @Test
    fun read_returns_nothing_without_the_field() {
        assertTrue(read_unlocked_keys(vault()).isEmpty())
    }

    @Test
    fun read_stops_at_the_cap() {
        val array = JSONArray()
        repeat(MAX_BACKUP_UNLOCKED_KEYS + 5) { array.put(JSONArray().put("locked-$it").put("open-$it")) }

        val unlocked = read_unlocked_keys(JSONObject().put(BACKUP_UNLOCKED_KEYS_FIELD, array))

        assertEquals(MAX_BACKUP_UNLOCKED_KEYS, unlocked.size)
        assertTrue(unlocked.containsKey("locked-0"))
        assertFalse(unlocked.containsKey("locked-$MAX_BACKUP_UNLOCKED_KEYS"))
    }

    @Test
    fun build_pairs_the_current_and_previous_keys_once_each() {
        val source = vault(previous = listOf("locked-old", "locked-current", "locked-older"))

        val backup = build_backup_vault(source, passphrase) { armored, _ -> "open:$armored" }

        assertEquals(
            listOf(
                "locked-current" to "open:locked-current",
                "locked-old" to "open:locked-old",
                "locked-older" to "open:locked-older",
            ),
            pairs(backup),
        )
        assertFalse(source.has(BACKUP_UNLOCKED_KEYS_FIELD))
    }

    @Test
    fun build_skips_keys_the_passphrase_cannot_unlock() {
        val source = vault(previous = listOf("locked-old"))

        val backup = build_backup_vault(source, passphrase) { armored, _ ->
            if (armored == "locked-old") error("wrong passphrase") else "open:$armored"
        }

        assertEquals(listOf("locked-current" to "open:locked-current"), pairs(backup))
    }

    @Test
    fun build_leaves_the_field_out_when_nothing_unlocks() {
        val backup = build_backup_vault(vault(), passphrase) { _, _ -> error("wrong passphrase") }

        assertFalse(backup.has(BACKUP_UNLOCKED_KEYS_FIELD))
    }

    @Test
    fun build_replaces_stale_pairs_and_respects_the_cap() {
        val source = vault(previous = (1..MAX_BACKUP_UNLOCKED_KEYS + 4).map { "locked-$it" })
            .put(BACKUP_UNLOCKED_KEYS_FIELD, JSONArray().put(JSONArray().put("stale").put("stale-open")))

        val backup = build_backup_vault(source, passphrase) { armored, _ -> "open:$armored" }

        val built = pairs(backup)
        assertEquals(MAX_BACKUP_UNLOCKED_KEYS, built.size)
        assertEquals("locked-current" to "open:locked-current", built.first())
        assertFalse(built.any { it.first == "stale" })
    }

    @Test
    fun carry_keeps_only_pairs_for_keys_still_in_the_vault() {
        val source = vault(previous = listOf("locked-old"))

        val backup = carry_backup_unlocked_keys(
            source,
            mapOf("locked-old" to "open-old", "locked-gone" to "open-gone"),
        )

        assertEquals(listOf("locked-old" to "open-old"), pairs(backup))
        assertFalse(source.has(BACKUP_UNLOCKED_KEYS_FIELD))
    }

    @Test
    fun carry_without_matches_leaves_the_field_out() {
        assertFalse(carry_backup_unlocked_keys(vault(), mapOf("other" to "open")).has(BACKUP_UNLOCKED_KEYS_FIELD))
    }

    @Test
    fun relock_locks_the_unlocked_copy_with_the_new_passphrase() {
        val relock = relock_with_unlocked_keys(mapOf("locked-old" to "open-old"), passphrase) { open, pass ->
            "relocked:$open:${String(pass)}"
        }

        assertEquals("relocked:open-old:pass", relock("locked-old"))
    }

    @Test
    fun relock_vault_keys_rewrites_identity_and_previous_keys_under_the_new_passphrase() {
        val source = vault(identity = "locked-current", previous = listOf("locked-old", "locked-foreign"))
        val unlocked = mapOf("locked-current" to "open-current", "locked-old" to "open-old")
        val next = "new".toCharArray()

        val relocked = relock_vault_keys(source, unlocked, next) { open, pass -> "$open@" + String(pass) }

        assertEquals("open-current@new", source.getString("identity_key"))
        assertEquals(listOf("open-old@new", "locked-foreign"), json_strings(source.getJSONArray("previous_keys")))
        assertEquals(mapOf("open-current@new" to "open-current", "open-old@new" to "open-old"), relocked)
        assertEquals(
            listOf("open-current@new" to "open-current", "open-old@new" to "open-old"),
            pairs(carry_backup_unlocked_keys(source, relocked)),
        )
    }

    @Test
    fun relock_vault_keys_keeps_a_key_whose_lock_fails() {
        val source = vault(identity = "locked-current")
        val unlocked = mapOf("locked-current" to "open-current")

        val relocked = relock_vault_keys(source, unlocked, passphrase) { _, _ -> error("boom") }

        assertEquals("locked-current", source.getString("identity_key"))
        assertTrue(relocked.isEmpty())
    }

    @Test
    fun relock_vault_keys_moves_a_legacy_identity_field_to_identity_key() {
        val source = JSONObject().put("identity_private_key", "locked-legacy")

        relock_vault_keys(source, mapOf("locked-legacy" to "open-legacy"), passphrase) { open, _ -> "$open!" }

        assertEquals("open-legacy!", source.getString("identity_key"))
        assertFalse(source.has("identity_private_key"))
    }

    @Test
    fun carries_master_key_requires_format_two_and_a_data_kek() {
        assertFalse(carries_master_key(vault()))
        assertFalse(carries_master_key(vault().put("data_kek", "kek")))
        assertFalse(carries_master_key(vault().put("vault_format", 2)))
        assertTrue(carries_master_key(vault().put("vault_format", 2).put("data_kek", "kek")))
    }

    @Test
    fun relock_fails_for_a_key_without_an_unlocked_copy() {
        val relock = relock_with_unlocked_keys(emptyMap(), passphrase) { open, _ -> open }

        assertThrows(IllegalStateException::class.java) { relock("locked-old") }
    }
}
