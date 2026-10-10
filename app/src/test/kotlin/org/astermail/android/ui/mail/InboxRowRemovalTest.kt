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
        assertFalse(footer_skeleton_wanted(is_loading_more = true, filter_active = false, removal_quiet = true))
    }

    @Test
    fun placeholder_rows_show_while_scrolling_into_the_next_page() {
        assertTrue(footer_skeleton_wanted(is_loading_more = true, filter_active = false, removal_quiet = false))
        assertFalse(footer_skeleton_wanted(is_loading_more = false, filter_active = false, removal_quiet = false))
        assertFalse(footer_skeleton_wanted(is_loading_more = true, filter_active = true, removal_quiet = false))
    }
}
