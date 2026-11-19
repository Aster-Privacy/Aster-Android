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

package org.astermail.android.crypto.storage_rotation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.astermail.android.storage.ScheduledUntaggedSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultMarkerQueueTest {

    @Test
    fun a_failed_pin_write_is_retried_until_the_vault_takes_it() = runTest {
        val written = ArrayList<Map<String, Int>>()
        var failures_left = 2
        val queue = VaultMarkerQueue(
            scope = this,
            write = { pins, _ ->
                if (failures_left > 0) {
                    failures_left -= 1
                    false
                } else {
                    written.add(pins)
                    true
                }
            },
            retry_delays_ms = listOf(1_000L, 5_000L),
            log = {},
        )

        assertFalse(queue.add_folder_pin("Folder-1", 2))
        assertEquals(1, queue.pending_count())

        advanceTimeBy(1_001L)
        runCurrent()
        assertEquals(1, queue.pending_count())
        assertTrue(written.isEmpty())

        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(0, queue.pending_count())
        assertEquals(listOf(mapOf("folder-1" to 2)), written)
    }

    @Test
    fun queued_pins_keep_the_highest_level_and_the_first_snapshot() = runTest {
        var accept = false
        val written = ArrayList<Pair<Map<String, Int>, ScheduledUntaggedSnapshot?>>()
        val queue = VaultMarkerQueue(
            scope = TestScope(testScheduler),
            write = { pins, scheduled ->
                if (accept) written.add(pins to scheduled)
                accept
            },
            retry_delays_ms = emptyList(),
            log = {},
        )

        queue.add_folder_pin("folder-1", 2)
        queue.add_folder_pin("FOLDER-1", 1)
        val first = ScheduledUntaggedSnapshot("2026-11-01T00:00:00.000Z", setOf("s0"))
        queue.add_scheduled_untagged(first)
        queue.add_scheduled_untagged(ScheduledUntaggedSnapshot("2026-11-02T00:00:00.000Z", setOf("s0", "s1")))
        accept = true

        assertTrue(queue.flush())
        assertEquals(listOf(mapOf("folder-1" to 2) to first), written)
        assertEquals(0, queue.pending_count())
    }
}
