// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class aster_pull_refresh_state_test {
    private val threshold = 100f
    private var haptics = 0
    private var refreshes = 0
    private var pullable = true

    private fun new_state() = aster_pull_refresh_state(
        threshold_px = { threshold },
        can_pull = { pullable },
        on_refresh = { refreshes += 1 },
        on_threshold = { haptics += 1 },
    )

    private fun aster_pull_refresh_state.pull_down(finger_px: Float) {
        onPostScroll(Offset.Zero, Offset(0f, finger_px), NestedScrollSource.UserInput)
    }

    private fun aster_pull_refresh_state.push_up(finger_px: Float) {
        onPreScroll(Offset(0f, -finger_px), NestedScrollSource.UserInput)
    }

    private fun finger_for(fraction: Float) = threshold * fraction / PULL_REFRESH_DRAG_RATIO

    @Test
    fun partial_pull_fires_nothing() = runTest {
        val state = new_state()
        repeat(10) { state.pull_down(finger_for(0.09f)) }
        state.onPreFling(Velocity.Zero)
        assertEquals(0, haptics)
        assertEquals(0, refreshes)
        assertFalse(state.holding)
    }

    @Test
    fun full_pull_fires_one_haptic_and_one_refresh() = runTest {
        val state = new_state()
        repeat(40) { state.pull_down(finger_for(0.05f)) }
        state.onPreFling(Velocity.Zero)
        assertEquals(1, haptics)
        assertEquals(1, refreshes)
        assertTrue(state.holding)
        state.onPreFling(Velocity.Zero)
        assertEquals(1, refreshes)
    }

    @Test
    fun jitter_at_threshold_does_not_repeat_haptic() = runTest {
        val state = new_state()
        state.pull_down(finger_for(1.02f))
        repeat(8) {
            state.push_up(finger_for(0.05f))
            state.pull_down(finger_for(0.05f))
        }
        assertEquals(1, haptics)
    }

    @Test
    fun retreat_below_rearm_then_recross_fires_again() = runTest {
        val state = new_state()
        state.pull_down(finger_for(1.1f))
        state.push_up(finger_for(0.6f))
        assertEquals(1, haptics)
        state.pull_down(finger_for(0.6f))
        assertEquals(2, haptics)
        state.push_up(finger_for(0.9f))
        state.onPreFling(Velocity.Zero)
        assertEquals(0, refreshes)
    }

    @Test
    fun blocked_pull_and_fling_input_fire_nothing() = runTest {
        pullable = false
        val state = new_state()
        state.pull_down(finger_for(1.5f))
        assertEquals(0, haptics)
        pullable = true
        state.onPostScroll(Offset.Zero, Offset(0f, finger_for(1.5f)), NestedScrollSource.SideEffect)
        assertEquals(0, haptics)
        state.onPreFling(Velocity.Zero)
        assertEquals(0, refreshes)
    }

    @Test
    fun release_allows_next_pull() = runTest {
        val state = new_state()
        state.pull_down(finger_for(1.2f))
        state.onPreFling(Velocity.Zero)
        state.pull_down(finger_for(1.2f))
        assertEquals(1, haptics)
        state.onPreFling(Velocity.Zero)
        state.release()
        state.pull_down(finger_for(1.2f))
        state.onPreFling(Velocity.Zero)
        assertEquals(2, haptics)
        assertEquals(2, refreshes)
    }
}
