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

import java.security.SecureRandom
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256
import org.astermail.android.storage.fake_prefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryBackupFormatTest {

    private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun vault(identity_key: String) =
        JSONObject().put("identity_key", identity_key).toString().toByteArray(Charsets.UTF_8)

    private fun identity(plain: ByteArray) = JSONObject(String(plain, Charsets.UTF_8)).getString("identity_key")

    private fun legacy_seal(plain: ByteArray, recovery_key: ByteArray): SealedVaultBackup {
        val salt = random(16)
        val nonce = random(12)
        val key = hkdf_sha256(recovery_key, salt, "Aster Mail_Recovery_Vault_v1".toByteArray(Charsets.UTF_8), 32)
        return SealedVaultBackup(AesGcm.encrypt(key, nonce, plain), nonce, salt)
    }

    private fun open(backup: SealedVaultBackup, key: ByteArray) =
        read_vault_backup(backup.encrypted_data, backup.nonce, backup.salt, key)

    @Test
    fun `sealed backup carries its user and generation and hides them from the vault`() {
        val key = random(32)
        val sealed = seal_vault_backup(vault("bound"), key, RecoveryBackupBinding("user-1", 42L))
        val opened = open(sealed, key)

        assertEquals("bound", identity(opened.plain))
        assertEquals(42L, opened.generation)
        assertEquals("user-1", opened.user_id)
        val fields = JSONObject(String(opened.plain, Charsets.UTF_8))
        assertFalse(fields.has("backup_generation"))
        assertFalse(fields.has("backup_user_id"))
        assertEquals(RecoveryBackupBinding("user-1", 42L), recovery_backup_binding_of(sealed.encrypted_data))
    }

    @Test
    fun `a changed generation or user in the header does not open`() {
        val key = random(32)
        val sealed = seal_vault_backup(vault("bound"), key, RecoveryBackupBinding("user-1", 42L))
        val tampered = sealed.encrypted_data.copyOf().also { it[11] = (it[11].toInt() xor 1).toByte() }

        assertThrows(Exception::class.java) {
            read_vault_backup(tampered, sealed.nonce, sealed.salt, key)
        }
    }

    @Test
    fun `a backup written before generations existed still opens`() {
        val key = random(32)
        val opened = open(legacy_seal(vault("legacy"), key), key)

        assertEquals("legacy", identity(opened.plain))
        assertEquals(0L, opened.generation)
        assertEquals("", opened.user_id)
    }

    @Test
    fun `recovery refuses a generation below the floor and accepts the newest`() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val key = random(32)
        val older = seal_vault_backup(vault("older"), key, RecoveryBackupBinding("user-1", 100L))
        val newer = seal_vault_backup(vault("newer"), key, RecoveryBackupBinding("user-1", 200L))
        floors.raise("user-1", 200L, "user@astermail.org")

        val opened = floors.open_for_recovery(newer.encrypted_data, newer.nonce, newer.salt, key, "user@astermail.org")
        assertEquals("newer", identity(opened.plain))
        assertThrows(RecoveryBackupStaleException::class.java) {
            floors.open_for_recovery(older.encrypted_data, older.nonce, older.salt, key, "user@astermail.org")
        }
        assertThrows(RecoveryBackupStaleException::class.java) {
            floors.open_for_recovery(older.encrypted_data, older.nonce, older.salt, key, null)
        }
    }

    @Test
    fun `the floor applies to a legacy backup once the user has a newer one`() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val key = random(32)
        val legacy = legacy_seal(vault("legacy"), key)

        val before = floors.open_for_recovery(legacy.encrypted_data, legacy.nonce, legacy.salt, key, "user@astermail.org")
        assertEquals("legacy", identity(before.plain))

        floors.raise("user-1", floors.next_binding("user-1").generation, "user@astermail.org")

        assertThrows(RecoveryBackupStaleException::class.java) {
            floors.open_for_recovery(legacy.encrypted_data, legacy.nonce, legacy.salt, key, "user@astermail.org")
        }
        val other_account = floors.open_for_recovery(legacy.encrypted_data, legacy.nonce, legacy.salt, key, "other@astermail.org")
        assertEquals("legacy", identity(other_account.plain))
    }

    @Test
    fun `a backup bound to another account is refused`() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val key = random(32)
        floors.raise("user-1", 10L, "user@astermail.org")
        val foreign = seal_vault_backup(vault("foreign"), key, RecoveryBackupBinding("user-2", 500L))

        assertThrows(RecoveryBackupStaleException::class.java) {
            floors.open_for_recovery(foreign.encrypted_data, foreign.nonce, foreign.salt, key, "user@astermail.org")
        }
    }

    @Test
    fun `next binding always moves past the floor`() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val ahead = System.currentTimeMillis() + 60_000L
        floors.raise("user-1", ahead)

        val binding = floors.next_binding("user-1")
        assertEquals("user-1", binding.user_id)
        assertTrue(binding.generation > ahead)
    }

    @Test
    fun `before readiness the backup is written in the first format`() {
        val key = random(32)
        val sealed = seal_vault_backup(vault("first"), key, RecoveryBackupBinding("user-1", 7L), false)
        val opened = open(sealed, key)

        assertEquals("first", identity(opened.plain))
        assertEquals(0L, opened.generation)
        assertEquals("", opened.user_id)
    }

    @Test
    fun `the backup generation counts up from the vault and the floor`() {
        val floors = RecoveryBackupFloors(fake_prefs())

        assertEquals(8L, floors.next_binding("user-1", null, 7L).generation)
        floors.raise("user-1", 20L)
        assertEquals(21L, floors.next_binding("user-1", null, 7L).generation)
        assertEquals(31L, floors.next_binding("user-1", null, 30L).generation)
    }

    @Test
    fun `a first format backup is refused once the account moved to the new format`() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val key = random(32)
        val legacy = legacy_seal(vault("legacy"), key)

        floors.mark_v2("user-1", "user@astermail.org")

        assertThrows(RecoveryBackupStaleException::class.java) {
            floors.open_for_recovery(legacy.encrypted_data, legacy.nonce, legacy.salt, key, "user@astermail.org")
        }
        val current = seal_vault_backup(vault("current"), key, RecoveryBackupBinding("user-1", 3L))
        val opened = floors.open_for_recovery(current.encrypted_data, current.nonce, current.salt, key, "user@astermail.org")
        assertEquals("current", identity(opened.plain))
    }

    @Test
    fun `archived key restore still opens an older backup`() {
        val key = random(32)
        val older = seal_vault_backup(vault("older"), key, RecoveryBackupBinding("user-1", 1L))
        val floors = RecoveryBackupFloors(fake_prefs())
        floors.raise("user-1", 999L, "user@astermail.org")

        assertEquals("older", identity(open(older, key).plain))
    }

    @Test
    fun a_new_format_backup_without_a_user_id_is_refused() {
        val key = ByteArray(32) { 5 }
        assertThrows(IllegalArgumentException::class.java) {
            seal_vault_backup(vault("unbound"), key, RecoveryBackupBinding("", 3L), true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            seal_vault_backup(vault("unbound"), key, RecoveryBackupBinding("  ", 3L), true)
        }
    }

    @Test
    fun an_old_format_backup_still_seals_without_a_user_id() {
        val key = ByteArray(32) { 5 }
        val sealed = seal_vault_backup(vault("legacy"), key, RecoveryBackupBinding("", 0L), false)
        assertEquals(null, recovery_backup_binding_of(sealed.encrypted_data))
    }

    @Test
    fun rotating_codes_stamps_the_next_generation_into_the_vault() {
        val floors = RecoveryBackupFloors(fake_prefs())
        floors.raise("user-1", 6L)
        val vault = JSONObject().put(RecoveryBackupVaultFields.GENERATION, 4L)

        val plan = apply_rotated_codes(vault, listOf("AAAA-BBBB"), floors, "user-1", null, ready_v2 = true)

        assertEquals(RecoveryBackupBinding("user-1", 7L), plan.binding)
        assertTrue(plan.format_v2)
        assertEquals(7L, RecoveryBackupVaultFields.generation(vault))
        assertTrue(RecoveryBackupVaultFields.format_v2(vault))
        assertEquals("AAAA-BBBB", vault.getJSONArray("recovery_codes").getString(0))
    }

    @Test
    fun rotating_codes_keeps_the_new_format_once_the_vault_records_it() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val vault = JSONObject()
            .put(RecoveryBackupVaultFields.GENERATION, 9L)
            .put(RecoveryBackupVaultFields.FORMAT_V2, true)

        val plan = apply_rotated_codes(vault, listOf("CCCC-DDDD"), floors, "user-1", null, ready_v2 = false)

        assertEquals(10L, plan.binding.generation)
        assertTrue(plan.format_v2)
        assertEquals(10L, RecoveryBackupVaultFields.generation(vault))
    }
}
