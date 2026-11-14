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

package org.astermail.android.security

import org.astermail.android.storage.LockSeal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class lock_key_wrap_test {

    private val iterations = 1_000

    @Test
    fun the_lock_key_comes_back_only_with_the_right_pin() {
        val lock_key = LockSeal.new_lock_key()
        val wrapped = LockKeyWrap.wrap("482913", lock_key, iterations)

        assertArrayEquals(lock_key, LockKeyWrap.unwrap("482913", wrapped, iterations))
        assertNull(LockKeyWrap.unwrap("482914", wrapped, iterations))
        assertNull(LockKeyWrap.unwrap("482913", wrapped, iterations + 1))
    }

    @Test
    fun the_wrapped_form_does_not_contain_the_lock_key_and_changes_every_time() {
        val lock_key = LockSeal.new_lock_key()
        val first = LockKeyWrap.wrap("482913", lock_key, iterations)
        val second = LockKeyWrap.wrap("482913", lock_key, iterations)

        assertNotEquals(first, second)
        assertFalse(first.contains(java.util.Base64.getEncoder().encodeToString(lock_key)))
    }

    @Test
    fun a_missing_tampered_or_foreign_value_does_not_unwrap() {
        val wrapped = LockKeyWrap.wrap("482913", LockSeal.new_lock_key(), iterations)
        val flipped = wrapped.dropLast(2) + if (wrapped.endsWith("AA")) "BB" else "AA"

        assertNull(LockKeyWrap.unwrap("482913", null, iterations))
        assertNull(LockKeyWrap.unwrap("482913", "", iterations))
        assertNull(LockKeyWrap.unwrap("482913", "lw1:", iterations))
        assertNull(LockKeyWrap.unwrap("482913", wrapped.removePrefix("lw1:"), iterations))
        assertNull(LockKeyWrap.unwrap("482913", flipped, iterations))
    }
}
