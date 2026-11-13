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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class lock_seal_test {

    private val store = "aster_session_keys_v1"

    @Test
    fun a_sealed_value_opens_only_with_the_same_lock_key() {
        val lock_key = LockSeal.new_lock_key()
        val sealed = LockSeal.seal(lock_key, store, "passphrase", "correct horse")

        assertTrue(LockSeal.is_sealed(sealed))
        assertFalse(sealed.contains("correct horse"))
        assertEquals("correct horse", LockSeal.open(lock_key, store, "passphrase", sealed))
        assertNull(LockSeal.open(LockSeal.new_lock_key(), store, "passphrase", sealed))
    }

    @Test
    fun a_sealed_value_cannot_be_moved_to_another_name_or_store() {
        val lock_key = LockSeal.new_lock_key()
        val sealed = LockSeal.seal(lock_key, store, "passphrase", "secret")

        assertNull(LockSeal.open(lock_key, store, "identity_key", sealed))
        assertNull(LockSeal.open(lock_key, "aster_other_store", "passphrase", sealed))
    }

    @Test
    fun a_tampered_or_truncated_value_does_not_open() {
        val lock_key = LockSeal.new_lock_key()
        val sealed = LockSeal.seal(lock_key, store, "passphrase", "secret")
        val flipped = sealed.dropLast(2) + if (sealed.endsWith("AA")) "BB" else "AA"

        assertNull(LockSeal.open(lock_key, store, "passphrase", flipped))
        assertNull(LockSeal.open(lock_key, store, "passphrase", "ls1:AAAA"))
        assertNull(LockSeal.open(lock_key, store, "passphrase", "ls1:not base64"))
    }

    @Test
    fun a_plain_value_is_not_treated_as_sealed() {
        val lock_key = LockSeal.new_lock_key()

        assertFalse(LockSeal.is_sealed("c2VjcmV0"))
        assertFalse(LockSeal.is_sealed(null))
        assertNull(LockSeal.open(lock_key, store, "passphrase", "c2VjcmV0"))
    }

    @Test
    fun sealing_twice_gives_different_ciphertexts() {
        val lock_key = LockSeal.new_lock_key()

        assertNotEquals(
            LockSeal.seal(lock_key, store, "passphrase", "secret"),
            LockSeal.seal(lock_key, store, "passphrase", "secret"),
        )
    }

    @Test
    fun the_key_check_accepts_only_the_right_lock_key() {
        val lock_key = LockSeal.new_lock_key()
        val check = LockSeal.key_check(lock_key)

        assertTrue(LockSeal.matches_key_check(lock_key, check))
        assertFalse(LockSeal.matches_key_check(LockSeal.new_lock_key(), check))
        assertFalse(LockSeal.matches_key_check(lock_key, null))
        assertFalse(LockSeal.matches_key_check(ByteArray(16), check))
    }
}
