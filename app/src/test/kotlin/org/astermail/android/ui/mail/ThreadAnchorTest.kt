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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadAnchorTest {

    private val ids = (1..30).map { "m$it" }

    @Test
    fun scrolls_when_the_target_is_not_laid_out() {
        assertTrue(thread_anchor_needs_scroll(item_offset = null, desired_offset = 0, can_scroll_forward = false))
    }

    @Test
    fun scrolls_when_the_target_sits_below_the_top_and_more_content_now_fits() {
        assertTrue(thread_anchor_needs_scroll(item_offset = 640, desired_offset = 0, can_scroll_forward = true))
    }

    @Test
    fun holds_still_when_the_list_is_at_its_end_with_a_short_last_message() {
        assertFalse(thread_anchor_needs_scroll(item_offset = 640, desired_offset = 0, can_scroll_forward = false))
    }

    @Test
    fun scrolls_when_the_target_was_pushed_above_the_top() {
        assertTrue(thread_anchor_needs_scroll(item_offset = -300, desired_offset = 0, can_scroll_forward = true))
    }

    @Test
    fun holds_still_when_the_target_is_in_place() {
        assertFalse(thread_anchor_needs_scroll(item_offset = 0, desired_offset = 0, can_scroll_forward = true))
        assertFalse(thread_anchor_needs_scroll(item_offset = 1, desired_offset = 0, can_scroll_forward = true))
        assertFalse(thread_anchor_needs_scroll(item_offset = -120, desired_offset = -120, can_scroll_forward = true))
    }

    @Test
    fun restored_offset_counts_as_in_place() {
        assertTrue(thread_anchor_needs_scroll(item_offset = 0, desired_offset = -120, can_scroll_forward = true))
        assertFalse(thread_anchor_needs_scroll(item_offset = -121, desired_offset = -120, can_scroll_forward = true))
    }

    @Test
    fun key_index_accounts_for_the_header() {
        assertEquals(0, thread_item_index_for_key(ids, thread_header_item_key, header_item_count = 1))
        assertEquals(1, thread_item_index_for_key(ids, "m1", header_item_count = 1))
        assertEquals(30, thread_item_index_for_key(ids, "m30", header_item_count = 1))
        assertNull(thread_item_index_for_key(ids, "gone", header_item_count = 1))
    }

    @Test
    fun long_thread_targets_the_latest_message() {
        val long = (1..120).map { "m$it" }
        assertEquals(120, thread_open_target_index(long, emptySet(), "m120", header_item_count = 1))
    }

    @Test
    fun specific_message_stays_the_target() {
        assertEquals(10, thread_open_target_index(ids, emptySet(), "m10", header_item_count = 1))
    }

    @Test
    fun own_replies_after_the_opened_message_move_the_target_to_the_tail() {
        val own = setOf("m29", "m30")
        assertEquals(30, thread_open_target_index(ids, own, "m28", header_item_count = 1))
    }

    @Test
    fun short_threads_stay_at_the_top() {
        assertNull(thread_open_target_index(listOf("a"), emptySet(), "a", header_item_count = 1))
        assertNull(thread_open_target_index(listOf("a", "b"), emptySet(), "b", header_item_count = 1))
    }

    @Test
    fun a_new_own_tail_since_the_last_visit_counts_as_a_reply() {
        assertTrue(thread_replied_since_saved("r1", tail_is_own = true, saved_tail_key = "m30"))
        assertFalse(thread_replied_since_saved("m31", tail_is_own = false, saved_tail_key = "m30"))
        assertFalse(thread_replied_since_saved("m30", tail_is_own = true, saved_tail_key = "m30"))
        assertFalse(thread_replied_since_saved("r1", tail_is_own = true, saved_tail_key = null))
    }
}
