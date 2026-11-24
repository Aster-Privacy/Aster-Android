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

package org.astermail.android.design.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class skeleton_paragraph_test {

    @Test
    fun every_placed_line_fits_inside_the_available_height() {
        for (available in listOf(30f, 64f, 120f, 233f, 240f, 641f)) {
            val slots = skeleton_paragraph_slots(available, 22f, 11f, 10f, 40)
            slots.forEach { slot -> assertTrue(slot.top + 11f <= available) }
        }
    }

    @Test
    fun no_line_is_cut_when_the_box_is_shorter_than_the_paragraph() {
        val slots = skeleton_paragraph_slots(120f, 22f, 11f, 10f, 12)
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.size < 12)
        assertTrue(slots.last().top + 11f <= 120f)
    }

    @Test
    fun an_unbounded_box_places_every_line() {
        assertEquals(12, skeleton_paragraph_slots(Float.POSITIVE_INFINITY, 22f, 11f, 10f, 12).size)
    }

    @Test
    fun the_last_line_of_each_paragraph_is_shorter() {
        val slots = skeleton_paragraph_slots(Float.POSITIVE_INFINITY, 22f, 11f, 10f, 12)
        assertTrue(slots[3].fraction < 0.8f)
        assertTrue(slots[0].fraction > 0.85f)
        assertTrue(slots[4].top - slots[3].top > 22f)
    }

    @Test
    fun a_skeleton_shown_moments_ago_continues_without_the_reveal_delay() {
        assertTrue(skeleton_reveal_continues(visible_count = 1, last_hidden_at = 0L, now = 5_000L))
        assertTrue(skeleton_reveal_continues(visible_count = 0, last_hidden_at = 4_900L, now = 5_000L))
        assertFalse(skeleton_reveal_continues(visible_count = 0, last_hidden_at = 1_000L, now = 5_000L))
        assertFalse(skeleton_reveal_continues(visible_count = 0, last_hidden_at = 0L, now = 5_000L))
    }
}
