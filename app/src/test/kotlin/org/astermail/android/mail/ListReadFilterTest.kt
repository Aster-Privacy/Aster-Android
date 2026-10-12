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

package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListReadFilterTest {
    @Test
    fun an_unfiltered_list_sends_no_read_state() {
        assertNull(list_read_filter_query(ListReadFilter.all))
    }

    @Test
    fun the_unread_and_read_filters_map_to_the_read_state_sent() {
        assertEquals(false, list_read_filter_query(ListReadFilter.unread))
        assertEquals(true, list_read_filter_query(ListReadFilter.read))
    }

    @Test
    fun folders_without_a_read_state_never_filter() {
        assertEquals(ListReadFilter.all, effective_read_filter("drafts", ListReadFilter.unread))
        assertEquals(ListReadFilter.all, effective_read_filter("scheduled", ListReadFilter.read))
        assertFalse(folder_supports_read_filter("drafts"))
        assertFalse(folder_supports_read_filter("scheduled"))
    }

    @Test
    fun mail_folders_keep_the_requested_filter() {
        assertEquals(ListReadFilter.unread, effective_read_filter("inbox", ListReadFilter.unread))
        assertEquals(ListReadFilter.read, effective_read_filter("label:abc", ListReadFilter.read))
        assertEquals(ListReadFilter.unread, effective_read_filter("tag:abc", ListReadFilter.unread))
        assertEquals(ListReadFilter.all, effective_read_filter("archive", ListReadFilter.all))
    }

    @Test
    fun only_an_unfiltered_list_may_be_cached() {
        assertTrue(read_filter_allows_cache_write(ListReadFilter.all))
        assertFalse(read_filter_allows_cache_write(ListReadFilter.unread))
        assertFalse(read_filter_allows_cache_write(ListReadFilter.read))
    }

    @Test
    fun the_next_page_offset_counts_only_rows_that_still_match() {
        val flags = listOf(false, false, true, false, true)
        assertEquals(5, read_filter_offset_base(flags, ListReadFilter.all))
        assertEquals(3, read_filter_offset_base(flags, ListReadFilter.unread))
        assertEquals(2, read_filter_offset_base(flags, ListReadFilter.read))
        assertEquals(0, read_filter_offset_base(emptyList(), ListReadFilter.unread))
    }

    @Test
    fun mark_all_follows_the_folder_count_when_no_loaded_row_is_unread() {
        assertTrue(mark_all_has_unread(filter_active = false, loaded_has_unread = false, server_unread_count = 23))
        assertFalse(mark_all_has_unread(filter_active = false, loaded_has_unread = false, server_unread_count = 0))
    }

    @Test
    fun mark_all_follows_a_loaded_unread_row_when_the_count_is_stale() {
        assertTrue(mark_all_has_unread(filter_active = false, loaded_has_unread = true, server_unread_count = 0))
        assertTrue(mark_all_has_unread(filter_active = false, loaded_has_unread = true, server_unread_count = null))
    }

    @Test
    fun mark_all_uses_loaded_rows_when_the_count_is_unknown() {
        assertFalse(mark_all_has_unread(filter_active = false, loaded_has_unread = false, server_unread_count = null))
    }

    @Test
    fun mark_all_under_a_filter_follows_the_rows_shown() {
        assertFalse(mark_all_has_unread(filter_active = true, loaded_has_unread = false, server_unread_count = 23))
        assertTrue(mark_all_has_unread(filter_active = true, loaded_has_unread = true, server_unread_count = 0))
    }
}
