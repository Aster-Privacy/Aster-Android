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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxFooterSkeletonTest {
    @Test
    fun a_background_fill_under_a_short_list_shows_no_placeholders() {
        val at_end = list_scrolled_to_end(has_scrolled = false, last_visible_index = 0, total_items = 1)
        assertFalse(at_end)
        assertFalse(footer_skeleton_wanted(is_loading_more = true, has_more = true, at_list_end = at_end, removal_quiet = false))
    }

    @Test
    fun placeholders_show_only_after_the_user_scrolls_to_the_end_of_a_long_list() {
        val at_end = list_scrolled_to_end(has_scrolled = true, last_visible_index = 48, total_items = 50)
        assertTrue(at_end)
        assertTrue(footer_skeleton_wanted(is_loading_more = true, has_more = true, at_list_end = at_end, removal_quiet = false))
    }

    @Test
    fun placeholders_stay_hidden_while_the_user_is_far_from_the_end() {
        val at_end = list_scrolled_to_end(has_scrolled = true, last_visible_index = 20, total_items = 50)
        assertFalse(at_end)
        assertFalse(footer_skeleton_wanted(is_loading_more = true, has_more = true, at_list_end = at_end, removal_quiet = false))
    }

    @Test
    fun placeholders_hide_once_the_fetch_completes() {
        assertFalse(footer_skeleton_wanted(is_loading_more = false, has_more = true, at_list_end = true, removal_quiet = false))
    }

    @Test
    fun placeholders_hide_when_there_are_no_more_pages() {
        assertFalse(footer_skeleton_wanted(is_loading_more = true, has_more = false, at_list_end = true, removal_quiet = false))
    }

    @Test
    fun an_empty_list_is_never_at_its_end() {
        assertFalse(list_scrolled_to_end(has_scrolled = true, last_visible_index = -1, total_items = 0))
    }

    @Test
    fun placeholders_stay_up_while_they_are_on_screen_during_the_fetch() {
        assertTrue(list_scrolled_to_end(has_scrolled = true, last_visible_index = 45, total_items = 53, footer_rows_visible = true))
    }
}
