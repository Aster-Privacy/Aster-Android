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

class InboxRowRemovalTest {
    @Test
    fun a_list_emptied_by_an_action_is_not_a_first_load() {
        assertTrue(inbox_emptied_by_action(had_rows = true, is_empty = true, initial = false))
    }

    @Test
    fun a_folder_that_never_showed_rows_still_loads_normally() {
        assertFalse(inbox_emptied_by_action(had_rows = false, is_empty = true, initial = false))
        assertFalse(inbox_emptied_by_action(had_rows = true, is_empty = true, initial = true))
        assertFalse(inbox_emptied_by_action(had_rows = true, is_empty = false, initial = false))
    }

    @Test
    fun placeholder_rows_stay_hidden_right_after_rows_are_removed() {
        assertFalse(footer_skeleton_wanted(is_loading_more = true, removal_quiet = true))
    }

    @Test
    fun placeholder_rows_show_while_scrolling_into_the_next_page() {
        assertTrue(footer_skeleton_wanted(is_loading_more = true, removal_quiet = false))
        assertFalse(footer_skeleton_wanted(is_loading_more = false, removal_quiet = false))
    }

    @Test
    fun placeholder_rows_show_while_a_filter_loads_the_next_page() {
        assertTrue(footer_skeleton_wanted(is_loading_more = true, removal_quiet = false))
    }

    @Test
    fun the_unread_filter_keeps_loading_until_every_counted_thread_is_listed() {
        assertTrue(unread_filter_rows_missing(inbox_filter_unread, visible_rows = 19, unread_count = 27, loaded_items = 50))
        assertFalse(unread_filter_rows_missing(inbox_filter_unread, visible_rows = 27, unread_count = 27, loaded_items = 100))
        assertFalse(unread_filter_rows_missing(inbox_filter_all, visible_rows = 19, unread_count = 27, loaded_items = 50))
        assertFalse(unread_filter_rows_missing(inbox_filter_read, visible_rows = 19, unread_count = 27, loaded_items = 50))
        assertFalse(unread_filter_rows_missing(inbox_filter_unread, visible_rows = 19, unread_count = 27, loaded_items = 1000))
    }

    private fun drained_unread_rows(unread_positions: Set<Int>, mailbox_size: Int, unread_count: Int): Int {
        val page_size = 50
        var loaded = minOf(page_size, mailbox_size)
        var pages = 0
        while (loaded < mailbox_size && pages < 100) {
            val listed = unread_positions.count { it < loaded }
            if (!inbox_auto_fill_wanted(inbox_filter_unread, listed, listed, unread_count, loaded)) break
            loaded = minOf(loaded + page_size, mailbox_size)
            pages++
        }
        return unread_positions.count { it < loaded }
    }

    @Test
    fun every_unread_thread_is_listed_when_some_sit_on_later_pages() {
        val unread_positions = (0 until 19).map { it * 2 }.toSet() + setOf(210, 255, 301, 340, 377, 412, 455, 498)
        assertEquals(27, unread_positions.size)
        assertEquals(27, drained_unread_rows(unread_positions, mailbox_size = 520, unread_count = 27))
    }

    @Test
    fun loading_stops_at_the_end_of_the_mailbox_when_the_count_overstates() {
        val unread_positions = (0 until 19).map { it * 2 }.toSet()
        assertEquals(19, drained_unread_rows(unread_positions, mailbox_size = 320, unread_count = 27))
    }

    @Test
    fun a_filtered_select_all_never_widens_to_the_whole_folder() {
        assertTrue(scope_selection_allowed(select_all_active = true, filter_active = false))
        assertFalse(scope_selection_allowed(select_all_active = true, filter_active = true))
        assertFalse(scope_selection_allowed(select_all_active = false, filter_active = false))
    }

    @Test
    fun loading_stops_once_the_counted_threads_are_listed() {
        assertFalse(inbox_auto_fill_wanted(inbox_filter_unread, 27, 27, 27, 300))
        assertTrue(inbox_auto_fill_wanted(inbox_filter_unread, 3, 3, 3, 50))
        assertFalse(inbox_auto_fill_wanted(inbox_filter_unread, 3, 3, 3, 200))
    }
}
