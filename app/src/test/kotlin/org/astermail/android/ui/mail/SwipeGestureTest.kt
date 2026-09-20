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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeGestureTest {

    private val slop = 12f
    private val limit = 1000f

    @Test
    fun a_short_move_decides_nothing() {
        assertEquals(SwipeAxis.undecided, swipe_axis_for(4f, 3f, slop))
    }

    @Test
    fun a_horizontal_drag_locks_horizontally_just_past_the_slop() {
        assertEquals(SwipeAxis.horizontal, swipe_axis_for(14f, 2f, slop))
        assertEquals(SwipeAxis.horizontal, swipe_axis_for(-14f, 2f, slop))
    }

    @Test
    fun a_vertical_drag_never_starts_a_swipe() {
        assertEquals(SwipeAxis.vertical, swipe_axis_for(6f, 30f, slop))
        assertEquals(SwipeAxis.vertical, swipe_axis_for(-6f, -30f, slop))
    }

    @Test
    fun a_diagonal_drag_needs_clear_horizontal_dominance() {
        assertEquals(SwipeAxis.vertical, swipe_axis_for(20f, 19f, slop))
        assertEquals(SwipeAxis.horizontal, swipe_axis_for(40f, 20f, slop))
    }

    @Test
    fun a_slow_short_drag_snaps_back() {
        assertFalse(swipe_commits(travelled = 120f, velocity = 50f, limit = limit))
    }

    @Test
    fun a_long_drag_commits_without_velocity() {
        assertTrue(swipe_commits(travelled = 420f, velocity = 0f, limit = limit))
    }

    @Test
    fun a_fast_fling_commits_a_short_drag() {
        assertTrue(swipe_commits(travelled = 150f, velocity = 1800f, limit = limit))
    }

    @Test
    fun a_fling_back_toward_the_start_never_commits() {
        assertFalse(swipe_commits(travelled = 150f, velocity = -1800f, limit = limit))
    }

    @Test
    fun a_fling_that_barely_moved_never_commits() {
        assertFalse(swipe_commits(travelled = 20f, velocity = 4000f, limit = limit))
    }

    @Test
    fun an_untouched_row_never_commits() {
        assertFalse(swipe_commits(travelled = 0f, velocity = 4000f, limit = limit))
        assertFalse(swipe_commits(travelled = 100f, velocity = 4000f, limit = 0f))
    }

    @Test
    fun a_leftward_fling_commits_too() {
        assertTrue(swipe_commits(travelled = -150f, velocity = -1800f, limit = limit))
    }
}
