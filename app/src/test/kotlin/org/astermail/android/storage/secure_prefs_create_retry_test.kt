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
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class secure_prefs_create_retry_test {

    @Test
    fun a_transient_keystore_failure_is_retried_before_giving_up() {
        var calls = 0
        val slept = mutableListOf<Long>()

        val result = with_create_retries(longArrayOf(10L, 20L), sleep = { slept.add(it) }) {
            calls += 1
            if (calls < 3) throw java.security.KeyStoreException("keystore busy")
            "opened"
        }

        assertEquals("opened", result)
        assertEquals(3, calls)
        assertEquals(listOf(10L, 20L), slept)
    }

    @Test
    fun a_persistent_failure_is_rethrown_after_every_attempt() {
        var calls = 0
        val failure = java.security.KeyStoreException("keystore broken")

        try {
            with_create_retries(longArrayOf(1L, 1L), sleep = {}) {
                calls += 1
                throw failure
            }
            fail("expected the failure to propagate")
        } catch (thrown: java.security.KeyStoreException) {
            assertSame(failure, thrown)
        }
        assertEquals(3, calls)
    }

    @Test
    fun a_first_try_success_never_sleeps() {
        val slept = mutableListOf<Long>()

        with_create_retries(longArrayOf(10L), sleep = { slept.add(it) }) { "opened" }

        assertEquals(emptyList<Long>(), slept)
    }
}
