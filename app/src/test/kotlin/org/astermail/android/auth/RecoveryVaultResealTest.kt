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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryVaultResealTest {
    private val old_password = "old password one".toCharArray()
    private val new_password = "brand new password two".toCharArray()
    private val codes = listOf("AAAA-BBBB", "CCCC-DDDD")

    private fun backup(identity: String, previous: List<String> = emptyList(), unlocked: List<Pair<String, String>> = emptyList()): JSONObject {
        val vault = JSONObject()
            .put("identity_key", identity)
            .put("previous_keys", JSONArray(previous))
            .put("ratchet_identity_private", "ratchet-secret")
            .put("signed_prekey_private", "prekey-secret")
        if (unlocked.isNotEmpty()) {
            val pairs = JSONArray()
            unlocked.forEach { (locked, open) -> pairs.put(JSONArray().put(locked).put(open)) }
            vault.put(BACKUP_UNLOCKED_KEYS_FIELD, pairs)
        }
        return vault
    }

    @Test
    fun backup_without_unlocked_identity_asks_for_previous_password() {
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        assertTrue(recovery_needs_previous_password(backup(key.armored_private_key)))
    }

    @Test
    fun backup_with_unlocked_identity_needs_no_previous_password() {
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val open = unlock_pgp_key(key.armored_private_key, old_password)
        val source = backup(key.armored_private_key, unlocked = listOf(key.armored_private_key to open))
        assertFalse(recovery_needs_previous_password(source))
    }

    @Test
    fun raw_identity_material_never_asks_for_previous_password() {
        val source = JSONObject().put("identity_private_key", "raw-native-identity")
        assertFalse(recovery_needs_previous_password(source))
    }

    @Test
    fun unlocked_copy_relocks_same_identity_under_new_password() {
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val ciphertext = PgpEncryptor.encrypt_to_keys("hello", listOf(key.armored_public_key))!!
        val open = unlock_pgp_key(key.armored_private_key, old_password)
        val source = backup(key.armored_private_key, unlocked = listOf(key.armored_private_key to open))

        val result = reseal_recovery_vault(source, codes, new_password, null)

        val identity = vault_identity_key(result.vault)
        assertFalse(result.identity_locked)
        assertEquals(pgp_key_identity(key.armored_private_key), pgp_key_identity(identity))
        assertEquals("hello", PgpDecryptor.decrypt(ciphertext, identity, new_password))
        assertEquals("ratchet-secret", result.vault.getString("ratchet_identity_private"))
        assertEquals("prekey-secret", result.vault.getString("signed_prekey_private"))
        assertEquals(codes, json_strings(result.vault.getJSONArray("recovery_codes")))
        assertFalse(result.vault.has(BACKUP_UNLOCKED_KEYS_FIELD))
        assertEquals(open, read_unlocked_keys(result.backup)[identity])
    }

    @Test
    fun previous_password_reprotects_identity_and_previous_keys() {
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val older = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val current_mail = PgpEncryptor.encrypt_to_keys("current", listOf(key.armored_public_key))!!
        val older_mail = PgpEncryptor.encrypt_to_keys("older", listOf(older.armored_public_key))!!
        val source = backup(key.armored_private_key, previous = listOf(older.armored_private_key))

        val result = reseal_recovery_vault(source, codes, new_password, old_password)

        val identity = vault_identity_key(result.vault)
        assertFalse(result.identity_locked)
        assertEquals(pgp_key_identity(key.armored_private_key), pgp_key_identity(identity))
        assertEquals("current", PgpDecryptor.decrypt(current_mail, identity, new_password))
        val previous = json_strings(result.vault.getJSONArray("previous_keys"))
        assertTrue(previous.any { PgpDecryptor.decrypt(older_mail, it, new_password) == "older" })
        assertTrue(read_unlocked_keys(result.backup).containsKey(identity))
        assertEquals("ratchet-secret", result.vault.getString("ratchet_identity_private"))
    }

    @Test
    fun wrong_previous_password_is_rejected_before_anything_changes() {
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val source = backup(key.armored_private_key)
        val before = source.toString()

        assertThrows(RecoveryPreviousPasswordIncorrect::class.java) {
            reseal_recovery_vault(source, codes, new_password, "not it".toCharArray())
        }
        assertEquals(before, source.toString())
    }

    @Test
    fun skipping_previous_password_keeps_the_same_locked_keys() {
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val older = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val source = backup(key.armored_private_key, previous = listOf(older.armored_private_key))

        val result = reseal_recovery_vault(source, codes, new_password, null)

        assertTrue(result.identity_locked)
        assertEquals(key.armored_private_key, vault_identity_key(result.vault))
        assertEquals(listOf(older.armored_private_key), json_strings(result.vault.getJSONArray("previous_keys")))
        assertTrue(pgp_key_unlocks(vault_identity_key(result.vault), old_password))
        assertNull(read_unlocked_keys(result.backup)[key.armored_private_key])
    }

    @Test
    fun reseal_never_introduces_a_new_identity() {
        val key = PgpKeyGenerator.generate("User", "user@astermail.org", old_password)
        val source = backup(key.armored_private_key)

        val skipped = reseal_recovery_vault(source, codes, new_password, null)
        val repaired = reseal_recovery_vault(source, codes, new_password, old_password)

        val expected = pgp_key_identity(key.armored_private_key)
        assertEquals(expected, pgp_key_identity(vault_identity_key(skipped.vault)))
        assertEquals(expected, pgp_key_identity(vault_identity_key(repaired.vault)))
    }
}
