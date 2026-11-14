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

package org.astermail.android.storage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class sealed_prefs_test {

    private val store = "aster_session_keys_v1"
    private val disk = fake_prefs()
    private val prefs = SealedPrefs(disk, store) { name -> name == "user_id" }

    @Before
    fun start_closed() {
        LockKeyHolder.close()
    }

    @After
    fun end_closed() {
        LockKeyHolder.close()
    }

    @Test
    fun a_secret_written_while_the_lock_key_is_open_is_not_stored_in_the_clear() {
        LockKeyHolder.open(LockSeal.new_lock_key())
        prefs.edit().putString("passphrase", "correct horse").putString("user_id", "u1").commit()

        assertTrue(LockSeal.is_sealed(disk.values["passphrase"] as String))
        assertFalse((disk.values["passphrase"] as String).contains("correct horse"))
        assertEquals("u1", disk.values["user_id"])
        assertEquals("correct horse", prefs.getString("passphrase", null))
    }

    @Test
    fun a_sealed_secret_reads_as_missing_until_the_lock_key_is_open() {
        val lock_key = LockSeal.new_lock_key()
        LockKeyHolder.open(lock_key)
        prefs.edit().putString("passphrase", "correct horse").putString("user_id", "u1").commit()
        LockKeyHolder.close()

        assertNull(prefs.getString("passphrase", null))
        assertFalse(prefs.all.containsKey("passphrase"))
        assertEquals("u1", prefs.getString("user_id", null))

        LockKeyHolder.open(lock_key)
        assertEquals("correct horse", prefs.getString("passphrase", null))
    }

    @Test
    fun a_wrong_lock_key_does_not_open_a_sealed_secret() {
        LockKeyHolder.open(LockSeal.new_lock_key())
        prefs.edit().putString("passphrase", "correct horse").commit()
        LockKeyHolder.close()
        LockKeyHolder.open(LockSeal.new_lock_key())

        assertNull(prefs.getString("passphrase", null))
    }

    @Test
    fun a_write_while_locked_never_replaces_or_drops_a_sealed_secret() {
        val lock_key = LockSeal.new_lock_key()
        LockKeyHolder.open(lock_key)
        prefs.edit().putString("passphrase", "correct horse").commit()
        LockKeyHolder.close()

        prefs.edit().putString("passphrase", "other").commit()
        prefs.edit().putString("passphrase", null).commit()

        LockKeyHolder.open(lock_key)
        assertEquals("correct horse", prefs.getString("passphrase", null))
    }

    @Test
    fun a_write_while_locked_still_stores_a_value_that_has_no_sealed_copy() {
        prefs.edit().putString("vault_nonce", "abc").commit()

        assertEquals("abc", disk.values["vault_nonce"])
        assertEquals("abc", prefs.getString("vault_nonce", null))
    }

    @Test
    fun remove_and_clear_work_while_locked() {
        LockKeyHolder.open(LockSeal.new_lock_key())
        prefs.edit().putString("passphrase", "a").putString("identity_key", "b").commit()
        LockKeyHolder.close()

        prefs.edit().remove("passphrase").putString("passphrase", "fresh").commit()
        assertEquals("fresh", disk.values["passphrase"])

        prefs.edit().clear().commit()
        assertTrue(disk.values.isEmpty())
    }

    @Test
    fun seal_all_protects_existing_values_and_unseal_all_restores_them() {
        disk.values["passphrase"] = "correct horse"
        disk.values["user_id"] = "u1"
        disk.values["count"] = 3
        assertTrue(prefs.has_unsealed_secrets())

        val lock_key = LockSeal.new_lock_key()
        LockKeyHolder.open(lock_key)
        assertTrue(prefs.seal_all())

        assertTrue(LockSeal.is_sealed(disk.values["passphrase"] as String))
        assertEquals("u1", disk.values["user_id"])
        assertEquals(3, disk.values["count"])
        assertTrue(prefs.has_sealed_values())
        assertFalse(prefs.has_unsealed_secrets())

        LockKeyHolder.close()
        assertTrue(prefs.unseal_all(lock_key))
        assertEquals("correct horse", disk.values["passphrase"])
        assertFalse(prefs.has_sealed_values())
    }

    @Test
    fun unseal_all_changes_nothing_without_the_right_lock_key() {
        LockKeyHolder.open(LockSeal.new_lock_key())
        prefs.edit().putString("passphrase", "a").putString("identity_key", "b").commit()
        LockKeyHolder.close()
        val before = HashMap(disk.values)

        assertFalse(prefs.unseal_all())
        assertFalse(prefs.unseal_all(LockSeal.new_lock_key()))
        assertEquals(before, disk.values)
    }

    @Test
    fun seal_all_does_nothing_while_locked() {
        disk.values["passphrase"] = "correct horse"

        assertFalse(prefs.seal_all())
        assertEquals("correct horse", disk.values["passphrase"])
    }

    @Test
    fun open_listeners_run_only_when_the_holder_goes_from_closed_to_open() {
        var runs = 0
        val listener = { runs += 1 }
        LockKeyHolder.add_open_listener(listener)
        val lock_key = LockSeal.new_lock_key()

        LockKeyHolder.open(lock_key)
        LockKeyHolder.open(lock_key)
        assertEquals(1, runs)

        LockKeyHolder.close()
        LockKeyHolder.open(lock_key)
        assertEquals(2, runs)
    }
}
