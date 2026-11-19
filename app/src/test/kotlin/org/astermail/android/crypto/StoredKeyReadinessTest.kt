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

package org.astermail.android.crypto

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredKeyReadinessTest {

    @After
    fun teardown() {
        stored_key_readiness.install { false }
        stored_key_readiness.clock = System::currentTimeMillis
    }

    @Test
    fun readiness_is_cached_per_account_and_expires() = runBlocking {
        var now = 0L
        var calls = 0
        var answer = true
        stored_key_readiness.clock = { now }
        stored_key_readiness.install { calls++; answer }

        assertTrue(stored_key_readiness.is_ready("user-a"))
        answer = false
        assertTrue(stored_key_readiness.is_ready("user-a"))
        assertEquals(1, calls)
        assertFalse(stored_key_readiness.is_ready("user-b"))
        assertEquals(2, calls)
        now += 5 * 60 * 1000L
        assertFalse(stored_key_readiness.is_ready("user-b"))
        assertEquals(3, calls)
    }

    @Test
    fun a_failed_lookup_counts_as_not_ready_and_is_retried_after_a_short_wait() = runBlocking {
        var now = 0L
        var calls = 0
        var fail = true
        stored_key_readiness.clock = { now }
        stored_key_readiness.install { calls++; if (fail) error("offline") else true }

        assertFalse(stored_key_readiness.is_ready("user-a"))
        fail = false
        assertFalse(stored_key_readiness.is_ready("user-a"))
        assertEquals(1, calls)
        now += 30 * 1000L
        assertTrue(stored_key_readiness.is_ready("user-a"))
        assertEquals(2, calls)
    }

    @Test
    fun a_not_ready_answer_expires_sooner_than_a_ready_one() = runBlocking {
        var now = 0L
        var calls = 0
        var answer = false
        stored_key_readiness.clock = { now }
        stored_key_readiness.install { calls++; answer }

        assertFalse(stored_key_readiness.is_ready("user-a"))
        answer = true
        now += 29 * 1000L
        assertFalse(stored_key_readiness.is_ready("user-a"))
        now += 1000L
        assertTrue(stored_key_readiness.is_ready("user-a"))
        assertEquals(2, calls)
        answer = false
        now += 4 * 60 * 1000L
        assertTrue(stored_key_readiness.is_ready("user-a"))
        assertEquals(2, calls)
    }

    @Test
    fun accounts_keep_separate_answers() = runBlocking {
        var calls = 0
        stored_key_readiness.install { calls++; calls == 1 }

        assertTrue(stored_key_readiness.is_ready("user-a"))
        assertFalse(stored_key_readiness.is_ready("user-b"))
        assertTrue(stored_key_readiness.is_ready("user-a"))
        assertEquals(2, calls)
    }

    @Test
    fun no_account_reads_as_not_ready_without_a_lookup() = runBlocking {
        var calls = 0
        stored_key_readiness.install { calls++; true }

        assertFalse(stored_key_readiness.is_ready(null))
        assertFalse(stored_key_readiness.is_ready(""))
        assertEquals(0, calls)
    }
}
