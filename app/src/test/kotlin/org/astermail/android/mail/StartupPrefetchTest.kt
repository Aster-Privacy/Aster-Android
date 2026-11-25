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

package org.astermail.android.mail

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupPrefetchTest {

    @Test
    fun take_returns_prefetched_value_once() = runTest {
        val prefetch = StartupPrefetch<String, List<Int>>(this, UnconfinedTestDispatcher(testScheduler))
        val loads = AtomicInteger()
        prefetch.start("inbox") { loads.incrementAndGet(); listOf(1, 2, 3) }
        assertEquals(listOf(1, 2, 3), prefetch.take("inbox"))
        assertNull(prefetch.take("inbox"))
        assertEquals(1, loads.get())
    }

    @Test
    fun start_twice_loads_once() = runTest {
        val prefetch = StartupPrefetch<String, Int>(this, UnconfinedTestDispatcher(testScheduler))
        val loads = AtomicInteger()
        prefetch.start("inbox") { loads.incrementAndGet() }
        prefetch.start("inbox") { loads.incrementAndGet() }
        assertEquals(1, prefetch.take("inbox"))
        assertEquals(1, loads.get())
    }

    @Test
    fun take_of_other_key_is_null() = runTest {
        val prefetch = StartupPrefetch<String, Int>(this, UnconfinedTestDispatcher(testScheduler))
        prefetch.start("inbox") { 7 }
        assertNull(prefetch.take("sent"))
        assertEquals(7, prefetch.take("inbox"))
    }

    @Test
    fun invalidate_drops_pending_value() = runTest {
        val prefetch = StartupPrefetch<String, Int>(this, UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Int>()
        prefetch.start("inbox") { gate.await() }
        prefetch.invalidate("inbox")
        gate.complete(5)
        assertNull(prefetch.take("inbox"))
    }

    @Test
    fun clear_drops_every_value() = runTest {
        val prefetch = StartupPrefetch<String, Int>(this, UnconfinedTestDispatcher(testScheduler))
        prefetch.start("inbox") { 1 }
        prefetch.start("sent") { 2 }
        prefetch.clear()
        assertNull(prefetch.take("inbox"))
        assertNull(prefetch.take("sent"))
    }

    @Test
    fun failed_load_yields_null() = runTest {
        val prefetch = StartupPrefetch<String, Int>(this, UnconfinedTestDispatcher(testScheduler))
        prefetch.start("inbox") { error("database unavailable") }
        assertNull(prefetch.take("inbox"))
    }

    @Test
    fun take_waits_for_a_load_in_flight() = runTest {
        val prefetch = StartupPrefetch<String, Int>(this, UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Int>()
        prefetch.start("inbox") { gate.await() }
        val result = async { prefetch.take("inbox") }
        gate.complete(9)
        assertEquals(9, result.await())
    }
}
