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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class inbox_list_position_test {
    @Test
    fun skeleton_first_row_matches_the_loaded_first_row() {
        val position = skeleton_row_group_position(index = 0, row_count = 12)
        assertTrue(position.is_first)
        assertFalse(position.is_last)
    }

    @Test
    fun skeleton_last_row_stays_a_middle_row_while_the_list_continues() {
        val position = skeleton_row_group_position(index = 11, row_count = 12)
        assertFalse(position.is_first)
        assertFalse(position.is_last)
    }

    @Test
    fun skeleton_last_row_closes_the_group_only_when_the_list_ends_on_screen() {
        val position = skeleton_row_group_position(index = 2, row_count = 3, list_ends_on_screen = true)
        assertTrue(position.is_last)
    }

    @Test
    fun skeleton_middle_rows_have_no_rounded_ends() {
        for (index in 1 until 11) {
            val position = skeleton_row_group_position(index = index, row_count = 12)
            assertFalse(position.is_first)
            assertFalse(position.is_last)
        }
    }

    @Test
    fun near_top_covers_only_the_first_rows() {
        assertTrue(list_near_top(0))
        assertTrue(list_near_top(LIST_NEAR_TOP_ROWS))
        assertFalse(list_near_top(LIST_NEAR_TOP_ROWS + 1))
        assertFalse(list_near_top(40_000))
        assertFalse(list_near_top(-1))
    }

    @Test
    fun settle_key_is_null_while_moving_or_empty() {
        assertNull(list_settle_key(scrolling = true, fast_scroll_dragging = false, first_visible_index = 3, row_count = 50))
        assertNull(list_settle_key(scrolling = false, fast_scroll_dragging = true, first_visible_index = 3, row_count = 50))
        assertNull(list_settle_key(scrolling = false, fast_scroll_dragging = false, first_visible_index = 3, row_count = 0))
    }

    @Test
    fun settle_key_changes_with_position_and_row_count() {
        val base = list_settle_key(scrolling = false, fast_scroll_dragging = false, first_visible_index = 3, row_count = 50)
        assertEquals(base, list_settle_key(scrolling = false, fast_scroll_dragging = false, first_visible_index = 3, row_count = 50))
        assertNotEquals(base, list_settle_key(scrolling = false, fast_scroll_dragging = false, first_visible_index = 4, row_count = 50))
        assertNotEquals(base, list_settle_key(scrolling = false, fast_scroll_dragging = false, first_visible_index = 3, row_count = 100))
    }
}
