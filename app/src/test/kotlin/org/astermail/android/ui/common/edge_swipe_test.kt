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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class edge_swipe_test {
    @Test
    fun only_touches_in_the_leading_strip_count() {
        assertTrue(edge_swipe_starts_in_zone(10f, 1080f, 63f, rtl = false))
        assertFalse(edge_swipe_starts_in_zone(200f, 1080f, 63f, rtl = false))
        assertTrue(edge_swipe_starts_in_zone(1070f, 1080f, 63f, rtl = true))
        assertFalse(edge_swipe_starts_in_zone(10f, 1080f, 63f, rtl = true))
    }

    @Test
    fun inward_horizontal_drag_opens() {
        assertEquals(EdgeSwipeDecision.open, edge_swipe_decision(40f, 5f, 20f, rtl = false))
        assertEquals(EdgeSwipeDecision.open, edge_swipe_decision(-40f, 5f, 20f, rtl = true))
    }

    @Test
    fun vertical_or_outward_drag_is_left_to_the_content() {
        assertEquals(EdgeSwipeDecision.release, edge_swipe_decision(5f, 40f, 20f, rtl = false))
        assertEquals(EdgeSwipeDecision.release, edge_swipe_decision(-40f, 0f, 20f, rtl = false))
        assertEquals(EdgeSwipeDecision.release, edge_swipe_decision(40f, 0f, 20f, rtl = true))
    }

    @Test
    fun small_moves_wait_for_more_input() {
        assertEquals(EdgeSwipeDecision.pending, edge_swipe_decision(8f, 4f, 20f, rtl = false))
    }
}
