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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagePreloaderTest {

    private fun TestScope.cache(capacity: Int = 3, ttl_ms: Long = Long.MAX_VALUE, clock: () -> Long = { 0L }) =
        preload_cache<String, String>(capacity, backgroundScope, ttl_ms, clock)

    @Test
    fun prepare_reuses_the_running_load() = runTest {
        val cache = cache()
        val gate = CompletableDeferred<String>()
        var calls = 0
        val first = cache.prepare("a") { calls++; gate.await() }
        val second = cache.prepare("a") { calls++; "other" }
        runCurrent()
        assertSame(first, second)
        assertEquals(1, calls)
    }

    @Test
    fun take_returns_null_while_running_and_keeps_the_entry() = runTest {
        val cache = cache()
        val gate = CompletableDeferred<String>()
        val job = cache.prepare("a") { gate.await() }
        runCurrent()
        assertNull(cache.take("a"))
        assertTrue(cache.contains("a"))
        assertSame(job, cache.pending("a"))
    }

    @Test
    fun take_after_completion_hands_over_once() = runTest {
        val cache = cache()
        cache.prepare("a") { "body" }
        advanceUntilIdle()
        assertNull(cache.pending("a"))
        assertEquals("body", cache.take("a"))
        assertNull(cache.take("a"))
        assertFalse(cache.contains("a"))
    }

    @Test
    fun awaiting_a_pending_load_shares_the_same_deferred() = runTest {
        val cache = cache()
        val gate = CompletableDeferred<String>()
        var calls = 0
        cache.prepare("a") { calls++; gate.await() }
        runCurrent()
        val waiter = async { cache.await_and_take("a") }
        runCurrent()
        gate.complete("body")
        advanceUntilIdle()
        assertEquals("body", waiter.await())
        assertEquals(1, calls)
        assertEquals(0, cache.size())
    }

    @Test
    fun await_and_take_without_entry_returns_null() = runTest {
        assertNull(cache().await_and_take("missing"))
    }

    @Test
    fun null_result_removes_the_entry() = runTest {
        val cache = cache()
        cache.prepare("a") { null }
        advanceUntilIdle()
        assertFalse(cache.contains("a"))
        assertEquals(0, cache.size())
    }

    @Test
    fun failure_removes_the_entry() = runTest {
        val cache = cache()
        cache.prepare("a") { throw IllegalStateException("boom") }
        advanceUntilIdle()
        assertFalse(cache.contains("a"))
        assertNull(cache.take("a"))
    }

    @Test
    fun eviction_cancels_the_eldest_running_load() = runTest {
        val cache = cache(capacity = 2)
        val gate = CompletableDeferred<String>()
        val first = cache.prepare("a") { gate.await() }
        cache.prepare("b") { gate.await() }
        cache.prepare("c") { gate.await() }
        runCurrent()
        assertTrue(first.isCancelled)
        assertFalse(cache.contains("a"))
        assertTrue(cache.contains("b"))
        assertTrue(cache.contains("c"))
    }

    @Test
    fun cancel_if_running_leaves_finished_entries() = runTest {
        val cache = cache()
        val gate = CompletableDeferred<String>()
        cache.prepare("done") { "body" }
        val running = cache.prepare("slow") { gate.await() }
        advanceUntilIdle()
        cache.cancel_if_running("done")
        cache.cancel_if_running("slow")
        assertTrue(running.isCancelled)
        assertEquals("body", cache.take("done"))
        assertFalse(cache.contains("slow"))
    }

    @Test
    fun clear_cancels_and_empties() = runTest {
        val cache = cache()
        val gate = CompletableDeferred<String>()
        val running = cache.prepare("a") { gate.await() }
        cache.prepare("b") { "body" }
        advanceUntilIdle()
        cache.clear()
        assertTrue(running.isCancelled)
        assertEquals(0, cache.size())
        assertNull(cache.take("b"))
    }

    @Test
    fun remove_if_drops_matching_and_running_entries() = runTest {
        val cache = cache()
        val gate = CompletableDeferred<String>()
        cache.prepare("keep") { "other thread" }
        cache.prepare("stale") { "this thread" }
        advanceUntilIdle()
        val running = cache.prepare("slow") { gate.await() }
        runCurrent()
        cache.remove_if { it == "this thread" }
        assertTrue(running.isCancelled)
        assertFalse(cache.contains("stale"))
        assertFalse(cache.contains("slow"))
        assertEquals("other thread", cache.take("keep"))
    }

    @Test
    fun expired_entries_are_not_handed_over() = runTest {
        var now = 0L
        val cache = cache(ttl_ms = 1_000L, clock = { now })
        cache.prepare("a") { "body" }
        advanceUntilIdle()
        now = 1_001L
        assertFalse(cache.contains("a"))
        assertNull(cache.take("a"))
    }

    @Test
    fun expired_entry_is_reloaded_on_prepare() = runTest {
        var now = 0L
        val cache = cache(ttl_ms = 1_000L, clock = { now })
        var calls = 0
        cache.prepare("a") { calls++; "old" }
        advanceUntilIdle()
        now = 5_000L
        cache.prepare("a") { calls++; "new" }
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("new", cache.take("a"))
    }

    @Test
    fun neighbors_are_next_then_previous() {
        assertEquals(listOf("c", "a"), preload_neighbors(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("b"), preload_neighbors(listOf("a", "b"), "a"))
        assertEquals(emptyList<String>(), preload_neighbors(listOf("a", "b"), "z"))
    }

    @Test
    fun visible_candidates_skip_loaded_blank_and_duplicates() {
        val result = preload_visible_candidates(
            listOf("a", "", "b", "a", "c", "d", "e", "f"),
            limit = 3,
        ) { it == "b" }
        assertEquals(listOf("a", "c", "d"), result)
    }
}
