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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DetailSwipeNavigationTest {
    private val width = 1080f
    private val fling = 1300f

    private fun outcome(
        offset: Float,
        velocity: Float = 0f,
        has_next: Boolean = true,
        has_previous: Boolean = true,
        rtl: Boolean = false,
    ) = detail_swipe_outcome(offset, velocity, width, fling, has_next, has_previous, rtl)

    @Test
    fun clearly_horizontal_drag_claims() {
        assertEquals(DetailSwipeClaim.claim, detail_swipe_claim(40f, 5f, 20f))
        assertEquals(DetailSwipeClaim.claim, detail_swipe_claim(-40f, -10f, 20f))
    }

    @Test
    fun vertical_drag_goes_to_the_list() {
        assertEquals(DetailSwipeClaim.release, detail_swipe_claim(5f, 40f, 20f))
        assertEquals(DetailSwipeClaim.release, detail_swipe_claim(30f, 30f, 20f))
    }

    @Test
    fun diagonal_or_small_moves_wait() {
        assertEquals(DetailSwipeClaim.pending, detail_swipe_claim(8f, 2f, 20f))
        assertEquals(DetailSwipeClaim.pending, detail_swipe_claim(30f, 22f, 20f))
    }

    @Test
    fun long_drag_left_opens_next_and_right_opens_previous() {
        assertEquals(DetailSwipeOutcome.next, outcome(-400f))
        assertEquals(DetailSwipeOutcome.previous, outcome(400f))
    }

    @Test
    fun rtl_mirrors_direction() {
        assertEquals(DetailSwipeOutcome.next, outcome(400f, rtl = true))
        assertEquals(DetailSwipeOutcome.previous, outcome(-400f, rtl = true))
    }

    @Test
    fun short_drag_without_fling_settles() {
        assertEquals(DetailSwipeOutcome.settle, outcome(-200f))
        assertEquals(DetailSwipeOutcome.settle, outcome(0f, velocity = -5000f))
    }

    @Test
    fun quick_fling_commits_a_short_drag() {
        assertEquals(DetailSwipeOutcome.next, outcome(-100f, velocity = -2000f))
        assertEquals(DetailSwipeOutcome.settle, outcome(-30f, velocity = -2000f))
    }

    @Test
    fun fling_back_cancels_a_long_drag() {
        assertEquals(DetailSwipeOutcome.settle, outcome(-500f, velocity = 2000f))
        assertEquals(DetailSwipeOutcome.next, outcome(-500f, velocity = 400f))
    }

    @Test
    fun missing_neighbor_never_commits() {
        assertEquals(DetailSwipeOutcome.settle, outcome(-900f, velocity = -5000f, has_next = false))
        assertEquals(DetailSwipeOutcome.settle, outcome(900f, velocity = 5000f, has_previous = false))
        assertEquals(DetailSwipeOutcome.previous, outcome(900f, has_next = false))
    }

    @Test
    fun edge_resistance_stays_small_and_keeps_direction() {
        val pulled = detail_swipe_visual_px(-2000f, can_go = false, width_px = width)
        assertTrue(pulled < 0f)
        assertTrue(abs(pulled) <= width * DETAIL_SWIPE_EDGE_STRETCH_FRACTION)
        assertTrue(abs(pulled) / width < DETAIL_SWIPE_COMMIT_FRACTION)
        assertEquals(-300f, detail_swipe_visual_px(-300f, can_go = true, width_px = width), 0f)
        assertEquals(0f, detail_swipe_visual_px(0f, can_go = false, width_px = width), 0f)
    }

    @Test
    fun new_message_enters_from_the_side_the_finger_came_from() {
        assertEquals(1, detail_swipe_enter_side(to_next = true, rtl = false))
        assertEquals(-1, detail_swipe_enter_side(to_next = false, rtl = false))
        assertEquals(-1, detail_swipe_enter_side(to_next = true, rtl = true))
    }

    @Test
    fun touches_in_system_gesture_strips_are_ignored() {
        assertTrue(detail_swipe_starts_in_system_zone(10f, width, 60f, 60f))
        assertTrue(detail_swipe_starts_in_system_zone(1070f, width, 60f, 60f))
        assertFalse(detail_swipe_starts_in_system_zone(540f, width, 60f, 60f))
    }

    @Test
    fun wide_body_claims_pan_only_toward_hidden_content() {
        assertTrue(body_claims_horizontal_pan(-20f, can_scroll_forward = true, can_scroll_back = false))
        assertFalse(body_claims_horizontal_pan(20f, can_scroll_forward = true, can_scroll_back = false))
        assertTrue(body_claims_horizontal_pan(20f, can_scroll_forward = false, can_scroll_back = true))
        assertFalse(body_claims_horizontal_pan(-20f, can_scroll_forward = false, can_scroll_back = false))
    }
}
