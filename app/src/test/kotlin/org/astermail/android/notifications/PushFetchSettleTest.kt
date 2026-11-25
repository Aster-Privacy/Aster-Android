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

package org.astermail.android.notifications

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushFetchSettleTest {

    @Test
    fun push_without_ciphertext_is_fetched_inline_without_scheduling_work() = runTest {
        var fetched = 0
        var scheduled = 0
        val completed = settle_push_fetch(
            fetch_now = { fetched++ },
            enqueue_fallback = { scheduled++ },
        )
        assertTrue(completed)
        assertEquals(1, fetched)
        assertEquals(0, scheduled)
    }

    @Test
    fun failed_inline_fetch_falls_back_to_scheduled_work() = runTest {
        var scheduled = 0
        val completed = settle_push_fetch(
            fetch_now = { error("offline") },
            enqueue_fallback = { scheduled++ },
        )
        assertFalse(completed)
        assertEquals(1, scheduled)
    }

    @Test
    fun stalled_inline_fetch_falls_back_after_the_budget() = runTest {
        var scheduled = 0
        val completed = settle_push_fetch(
            fetch_now = { delay(PUSH_FETCH_TIMEOUT_MS * 2) },
            enqueue_fallback = { scheduled++ },
        )
        assertFalse(completed)
        assertEquals(1, scheduled)
    }

    @Test
    fun fallback_failure_does_not_escape() = runTest {
        val completed = settle_push_fetch(
            fetch_now = { error("offline") },
            enqueue_fallback = { error("work manager unavailable") },
        )
        assertFalse(completed)
    }

    @Test
    fun wake_lock_outlasts_the_inline_fetch_budget() {
        assertTrue(PUSH_FETCH_WAKE_LOCK_MS > PUSH_FETCH_TIMEOUT_MS)
    }
}
