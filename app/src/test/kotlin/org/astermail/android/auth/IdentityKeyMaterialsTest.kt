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

import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityKeyMaterialsTest {
    private val old_password = "old password one".toCharArray()
    private val current_password = "current password two".toCharArray()

    private fun vault(identity: String, previous: List<String> = emptyList(), legacy: List<String> = emptyList()) =
        JSONObject()
            .put("identity_key", identity)
            .put("previous_keys", JSONArray(previous))
            .apply { if (legacy.isNotEmpty()) put("legacy_identity_keys", JSONArray(legacy)) }

    private fun fake_reprotect(armored: String, old: CharArray, current: CharArray): String {
        if (armored.startsWith("broken")) error("wrong password")
        return "re_$armored"
    }

    @Test
    fun mail_sent_to_the_archived_key_decrypts_with_the_merged_key_and_the_current_passphrase() {
        val current_key = PgpKeyGenerator.generate("User", "user@astermail.org", current_password)
        val archived_key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val ciphertext = PgpEncryptor.encrypt_to_keys("archived hello", listOf(archived_key.armored_public_key))!!
        val live = vault(current_key.armored_private_key)

        val merged = merge_recovered_identity_keys(
            live,
            listOf(vault(archived_key.armored_private_key)),
            old_password,
            current_password,
        )

        assertEquals(listOf(true), merged.absorbed)
        assertEquals(1, merged.previous_keys.size)
        val recovered = merged.previous_keys.single()
        assertEquals(pgp_key_identity(archived_key.armored_private_key), pgp_key_identity(recovered))
        assertEquals("archived hello", PgpDecryptor.decrypt(ciphertext, recovered, current_password))
        assertNull(PgpDecryptor.decrypt(ciphertext, recovered, old_password))
        assertEquals(listOf(archived_key.armored_private_key), merged.legacy_identity_keys)
        assertEquals(current_key.armored_private_key, vault_identity_key(live))
    }

    @Test
    fun archived_previous_keys_are_reprotected_too() {
        val current_key = PgpKeyGenerator.generate("User", "user@astermail.org", current_password)
        val archived_key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val older_key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val ciphertext = PgpEncryptor.encrypt_to_keys("older hello", listOf(older_key.armored_public_key))!!

        val merged = merge_recovered_identity_keys(
            vault(current_key.armored_private_key),
            listOf(vault(archived_key.armored_private_key, listOf(older_key.armored_private_key))),
            old_password,
            current_password,
        )

        assertEquals(listOf(true), merged.absorbed)
        val older = merged.previous_keys.single { pgp_key_identity(it) == pgp_key_identity(older_key.armored_private_key) }
        assertEquals("older hello", PgpDecryptor.decrypt(ciphertext, older, current_password))
    }

    @Test
    fun a_password_change_reprotects_the_identity_key_and_older_keys() {
        val identity = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val older = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val to_identity = PgpEncryptor.encrypt_to_keys("fresh hello", listOf(identity.armored_public_key))!!
        val to_older = PgpEncryptor.encrypt_to_keys("older hello", listOf(older.armored_public_key))!!
        val live = vault(identity.armored_private_key, listOf(older.armored_private_key))

        val changed = reprotect_vault_pgp_keys(live, old_password, current_password)

        assertEquals(2, changed)
        assertEquals("fresh hello", PgpDecryptor.decrypt(to_identity, vault_identity_key(live), current_password))
        assertNull(PgpDecryptor.decrypt(to_identity, vault_identity_key(live), old_password))
        val previous = json_strings(live.optJSONArray("previous_keys")).single()
        assertEquals("older hello", PgpDecryptor.decrypt(to_older, previous, current_password))
    }

    @Test
    fun a_key_that_cannot_be_reprotected_is_left_untouched() {
        val live = vault("identity", listOf("broken_previous", "older"))

        val changed = reprotect_vault_pgp_keys(live, old_password, current_password, ::fake_reprotect)

        assertEquals(2, changed)
        assertEquals("re_identity", vault_identity_key(live))
        assertEquals(listOf("broken_previous", "re_older"), json_strings(live.optJSONArray("previous_keys")))
    }

    @Test
    fun a_wrong_old_password_absorbs_nothing_but_keeps_the_material() {
        val current_key = PgpKeyGenerator.generate("User", "user@astermail.org", current_password)
        val archived_key = PgpKeyGenerator.generate("User", "user@astermail.org", "another password".toCharArray())

        val merged = merge_recovered_identity_keys(
            vault(current_key.armored_private_key),
            listOf(vault(archived_key.armored_private_key)),
            old_password,
            current_password,
        )

        assertEquals(listOf(false), merged.absorbed)
        assertTrue(merged.previous_keys.isEmpty())
        assertEquals(listOf(archived_key.armored_private_key), merged.legacy_identity_keys)
    }

    @Test
    fun a_key_already_in_previous_keys_is_not_duplicated() {
        val current_key = PgpKeyGenerator.generate("User", "user@astermail.org", current_password)
        val archived_key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val already = reprotect_pgp_key(archived_key.armored_private_key, old_password, current_password)
        assertNotEquals(archived_key.armored_private_key, already)

        val merged = merge_recovered_identity_keys(
            vault(current_key.armored_private_key, listOf(already)),
            listOf(vault(archived_key.armored_private_key)),
            old_password,
            current_password,
        )

        assertEquals(listOf(already), merged.previous_keys)
        assertEquals(listOf(true), merged.absorbed)
    }

    @Test
    fun existing_previous_keys_are_never_evicted_and_an_unkept_set_is_not_absorbed() {
        val existing = (1..MAX_PREVIOUS_IDENTITY_KEYS).map { "prev_$it" }

        val merged = merge_recovered_identity_keys(
            vault("live", existing),
            listOf(vault("archived")),
            old_password,
            current_password,
            ::fake_reprotect,
        )

        assertEquals(existing, merged.previous_keys)
        assertEquals(listOf(false), merged.absorbed)
        assertEquals(listOf("archived"), merged.legacy_identity_keys)
    }

    @Test
    fun absorbs_only_the_sets_whose_identity_key_was_recovered() {
        val merged = merge_recovered_identity_keys(
            vault("live", listOf("prev_1")),
            listOf(vault("archived_a"), vault("broken_b"), vault("archived_c", listOf("broken_d"))),
            old_password,
            current_password,
            ::fake_reprotect,
        )

        assertEquals(listOf(true, false, true), merged.absorbed)
        assertEquals(listOf("prev_1", "re_archived_a", "re_archived_c"), merged.previous_keys)
    }

    @Test
    fun legacy_identity_keys_keep_existing_entries_first_unique_and_capped() {
        val existing = (1..30).map { "legacy_$it" }
        val merged = merge_recovered_identity_keys(
            vault("live", legacy = existing),
            listOf(
                vault("archived_a", listOf("legacy_1", "archived_b"), listOf("archived_c", "archived_d")),
            ),
            old_password,
            current_password,
            ::fake_reprotect,
        )

        assertEquals(MAX_LEGACY_IDENTITY_KEYS, merged.legacy_identity_keys.size)
        assertEquals(existing + listOf("archived_a", "archived_b"), merged.legacy_identity_keys)
    }

    @Test
    fun an_old_vault_without_an_identity_key_contributes_nothing() {
        val merged = merge_recovered_identity_keys(
            vault("live"),
            listOf(JSONObject()),
            old_password,
            current_password,
            ::fake_reprotect,
        )

        assertEquals(listOf(true), merged.absorbed)
        assertTrue(merged.previous_keys.isEmpty())
        assertTrue(merged.legacy_identity_keys.isEmpty())
        assertFalse(merged.absorbed.isEmpty())
    }

    @Test
    fun reads_the_legacy_identity_private_key_field() {
        val legacy_vault = JSONObject().put("identity_private_key", "archived")

        assertEquals("archived", vault_identity_key(legacy_vault))
    }

    @Test
    fun replacing_the_identity_key_keeps_the_old_one_first_in_legacy_identity_keys() {
        val live = vault("key_a", listOf("prev_1"), listOf("older", "key_a"))

        replace_vault_identity_key(live, "key_b")

        assertEquals("key_b", vault_identity_key(live))
        assertEquals(listOf("key_a", "older"), json_strings(live.optJSONArray("legacy_identity_keys")))
        assertEquals(listOf("prev_1"), json_strings(live.optJSONArray("previous_keys")))
    }

    @Test
    fun replacing_the_identity_key_creates_legacy_identity_keys_when_missing() {
        val live = JSONObject().put("identity_key", "key_a")

        replace_vault_identity_key(live, "key_b")

        assertEquals(listOf("key_a"), json_strings(live.optJSONArray("legacy_identity_keys")))
    }

    @Test
    fun replacing_the_identity_key_caps_legacy_identity_keys() {
        val existing = (1..MAX_LEGACY_IDENTITY_KEYS).map { "legacy_$it" }
        val live = vault("key_a", legacy = existing)

        replace_vault_identity_key(live, "key_b")

        val legacy = json_strings(live.optJSONArray("legacy_identity_keys"))
        assertEquals(MAX_LEGACY_IDENTITY_KEYS, legacy.size)
        assertEquals(listOf("key_a") + existing.dropLast(1), legacy)
    }

    @Test
    fun storing_the_same_identity_key_leaves_legacy_identity_keys_alone() {
        val live = vault("key_a", legacy = listOf("older"))

        replace_vault_identity_key(live, "key_a")

        assertEquals(listOf("older"), json_strings(live.optJSONArray("legacy_identity_keys")))
    }

    @Test
    fun older_identity_keys_merge_previous_and_legacy_without_the_current_key() {
        val live = vault("key_b", listOf("prev_1", "key_a"), listOf("key_a", "key_b", "older"))

        assertEquals(listOf("prev_1", "key_a", "older"), vault_older_identity_keys(live))
    }
}
