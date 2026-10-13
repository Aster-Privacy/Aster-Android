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

import org.astermail.android.mail.ListReadFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxReadFilterTest {
    @Test
    fun the_unread_and_read_chips_ask_for_a_filtered_list() {
        assertEquals(ListReadFilter.unread, read_filter_for_inbox_filter(inbox_filter_unread))
        assertEquals(ListReadFilter.read, read_filter_for_inbox_filter(inbox_filter_read))
    }

    @Test
    fun the_attachments_chip_stays_on_the_unfiltered_list() {
        assertEquals(ListReadFilter.all, read_filter_for_inbox_filter(inbox_filter_attachments))
        assertEquals(ListReadFilter.all, read_filter_for_inbox_filter(inbox_filter_all))
    }

    @Test
    fun a_filtered_list_with_no_rows_shows_the_filter_empty_state() {
        assertTrue(filter_empty_state_wanted(filter_active = true, server_filtered = true, threads_empty = true, visible_empty = true))
    }

    @Test
    fun loaded_rows_that_all_miss_the_filter_show_the_filter_empty_state() {
        assertTrue(filter_empty_state_wanted(filter_active = true, server_filtered = false, threads_empty = false, visible_empty = true))
    }

    @Test
    fun an_empty_folder_keeps_its_own_empty_state() {
        assertFalse(filter_empty_state_wanted(filter_active = false, server_filtered = false, threads_empty = true, visible_empty = true))
        assertFalse(filter_empty_state_wanted(filter_active = true, server_filtered = false, threads_empty = true, visible_empty = true))
    }

    @Test
    fun matching_rows_never_show_the_filter_empty_state() {
        assertFalse(filter_empty_state_wanted(filter_active = true, server_filtered = true, threads_empty = false, visible_empty = false))
    }

    @Test
    fun the_inbox_count_comes_from_the_account_totals() {
        assertEquals(23, mark_all_unread_count("inbox", 23, mapOf("inbox" to 4)))
        assertNull(mark_all_unread_count("inbox", null, emptyMap()))
    }

    @Test
    fun other_folders_use_their_own_count_when_one_is_known() {
        assertEquals(7, mark_all_unread_count("label:abc", 23, mapOf("label:abc" to 7)))
        assertNull(mark_all_unread_count("sent", 23, mapOf("label:abc" to 7)))
    }

    @Test
    fun spam_and_trash_counts_are_never_trusted() {
        assertNull(mark_all_unread_count("spam", 23, mapOf("spam" to 9)))
        assertNull(mark_all_unread_count("trash", 23, mapOf("trash" to 9)))
    }
}
