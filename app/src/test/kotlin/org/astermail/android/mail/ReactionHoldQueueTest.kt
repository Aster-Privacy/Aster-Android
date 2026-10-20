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

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionHoldQueueTest {

    private class Recorder {
        var sends = 0
        val outcomes = mutableListOf<ReactionHoldOutcome>()
    }

    private fun TestScope.settle() {
        advanceTimeBy(60_000L)
        runCurrent()
    }

    private fun TestScope.queue(account: () -> String? = { "user_1" }) =
        ReactionHoldQueue(backgroundScope, account)

    @Test
    fun `a held reaction sends only after the delay`() = runTest {
        val q = queue()
        val rec = Recorder()

        val held = q.hold("r1", 5_000L, send = { rec.sends++; Result.success("srv_r1") }) { rec.outcomes += it }

        assertEquals(5_000L, held.delay_ms)
        assertTrue(q.is_held("r1"))
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(0, rec.sends)
        advanceTimeBy(2L)
        runCurrent()
        assertEquals(1, rec.sends)
        assertEquals(listOf<ReactionHoldOutcome>(ReactionHoldOutcome.Sent("srv_r1")), rec.outcomes)
        assertFalse(q.is_held("r1"))
    }

    @Test
    fun `undo cancels the held reaction before it sends`() = runTest {
        val q = queue()
        val rec = Recorder()
        val held = q.hold("r1", 5_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }

        advanceTimeBy(1_000L)
        assertTrue(held.undo())
        settle()

        assertEquals(0, rec.sends)
        assertEquals(listOf<ReactionHoldOutcome>(ReactionHoldOutcome.Canceled), rec.outcomes)
        assertFalse(q.is_held("r1"))
        assertFalse(held.undo())
    }

    @Test
    fun `undo by id cancels the matching reaction only`() = runTest {
        val q = queue()
        val rec = Recorder()
        q.hold("r1", 5_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }
        q.hold("r2", 5_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }

        assertTrue(q.undo("r1"))
        settle()

        assertEquals(1, rec.sends)
        assertEquals(
            listOf(ReactionHoldOutcome.Canceled, ReactionHoldOutcome.Sent(null)),
            rec.outcomes,
        )
    }

    @Test
    fun `undo is refused once the reaction has been sent`() = runTest {
        val q = queue()
        val rec = Recorder()
        val held = q.hold("r1", 1_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }

        settle()

        assertFalse(held.undo())
        assertEquals(1, rec.sends)
        assertEquals(listOf<ReactionHoldOutcome>(ReactionHoldOutcome.Sent(null)), rec.outcomes)
    }

    @Test
    fun `zero delay sends at once and is not held`() = runTest {
        val q = queue()
        val rec = Recorder()

        val held = q.hold("r1", 0L, send = { rec.sends++; Result.success("srv") }) { rec.outcomes += it }

        assertEquals(0L, held.delay_ms)
        assertFalse(q.is_held("r1"))
        runCurrent()
        assertEquals(1, rec.sends)
        assertEquals(listOf<ReactionHoldOutcome>(ReactionHoldOutcome.Sent("srv")), rec.outcomes)
    }

    @Test
    fun `a failed send reports the error`() = runTest {
        val q = queue()
        val rec = Recorder()
        val error = IllegalStateException("boom")

        q.hold("r1", 1_000L, send = { rec.sends++; Result.failure(error) }) { rec.outcomes += it }
        settle()

        assertEquals(listOf<ReactionHoldOutcome>(ReactionHoldOutcome.Failed(error)), rec.outcomes)
    }

    @Test
    fun `a thrown send error is reported as a failure`() = runTest {
        val q = queue()
        val rec = Recorder()

        q.hold("r1", 0L, send = { throw IllegalStateException("thrown") }) { rec.outcomes += it }
        settle()

        val outcome = rec.outcomes.single()
        assertTrue(outcome is ReactionHoldOutcome.Failed)
        assertEquals("thrown", (outcome as ReactionHoldOutcome.Failed).error.message)
    }

    @Test
    fun `an account switch during the hold cancels the send`() = runTest {
        var account = "user_1"
        val q = queue { account }
        val rec = Recorder()

        q.hold("r1", 5_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }
        account = "user_2"
        settle()

        assertEquals(0, rec.sends)
        assertEquals(listOf<ReactionHoldOutcome>(ReactionHoldOutcome.AccountChanged), rec.outcomes)
    }

    @Test
    fun `flush sends every held reaction at once`() = runTest {
        val q = queue()
        val rec = Recorder()
        val first = q.hold("r1", 10_000L, send = { rec.sends++; Result.success("srv_r1") }) { rec.outcomes += it }
        q.hold("r2", 10_000L, send = { rec.sends++; Result.success("srv_r2") }) { rec.outcomes += it }

        advanceTimeBy(1_000L)
        q.flush()
        runCurrent()

        assertEquals(2, rec.sends)
        assertFalse(q.is_held("r1"))
        assertFalse(q.is_held("r2"))
        assertFalse(first.undo())
        assertEquals(
            listOf(ReactionHoldOutcome.Sent("srv_r1"), ReactionHoldOutcome.Sent("srv_r2")),
            rec.outcomes,
        )
    }

    @Test
    fun `flush all reaches every live queue`() = runTest {
        val a = queue()
        val b = queue()
        val rec = Recorder()
        a.hold("r1", 10_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }
        b.hold("r2", 10_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }

        ReactionHoldQueue.flush_all()
        runCurrent()

        assertEquals(2, rec.sends)
    }

    @Test
    fun `flush leaves undone and new reactions alone`() = runTest {
        val q = queue()
        val rec = Recorder()
        val undone = q.hold("r1", 10_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }
        assertTrue(undone.undo())

        q.flush()
        runCurrent()
        q.hold("r2", 10_000L, send = { rec.sends++; Result.success(null) }) { rec.outcomes += it }
        advanceTimeBy(9_000L)
        runCurrent()

        assertEquals(0, rec.sends)
        assertTrue(q.is_held("r2"))
        settle()
        assertEquals(1, rec.sends)
    }
}
