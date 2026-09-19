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
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadCountTest {

    private fun email(
        id: String,
        thread_id: String,
        received_at: Long = 1_000L,
        claimed: Int = 1,
    ) = Email(
        id = id,
        sender_name = "Aster",
        sender_email = "noreply@astermail.org",
        subject = "Welcome",
        preview = "hi",
        received_at = received_at,
        is_read = true,
        is_starred = false,
        has_attachment = false,
        thread_id = thread_id,
        thread_message_count = claimed,
    )

    @Test
    fun a_duplicated_message_does_not_turn_one_message_into_a_thread() {
        val rows = group_by_thread(listOf(email("m1", "t1"), email("m1", "t1")))
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().message_count)
    }

    @Test
    fun duplicate_ids_keep_the_first_copy() {
        val first = email("m1", "t1", received_at = 5_000L)
        val second = email("m1", "t1", received_at = 1_000L)
        val rows = group_by_thread(listOf(first, second))
        assertEquals(5_000L, rows.single().newest.received_at)
    }

    @Test
    fun two_distinct_messages_count_as_two() {
        val rows = group_by_thread(listOf(email("m1", "t1"), email("m2", "t1", received_at = 2_000L)))
        assertEquals(2, rows.single().message_count)
        assertEquals("m2", rows.single().newest.id)
    }

    @Test
    fun the_server_count_wins_when_no_correction_is_known() {
        val rows = group_by_thread(listOf(email("m1", "t1", claimed = 4)))
        assertEquals(4, rows.single().message_count)
    }

    @Test
    fun a_loaded_thread_corrects_an_inflated_server_count() {
        val corrections = mapOf("t1" to ThreadCountCorrection(claimed = 3, loaded = 1))
        val rows = group_by_thread(listOf(email("m1", "t1", claimed = 3)), corrections)
        assertEquals(1, rows.single().message_count)
    }

    @Test
    fun a_stale_correction_is_ignored_once_the_server_count_moves() {
        val corrections = mapOf("t1" to ThreadCountCorrection(claimed = 3, loaded = 1))
        val rows = group_by_thread(listOf(email("m1", "t1", claimed = 4)), corrections)
        assertEquals(4, rows.single().message_count)
    }

    @Test
    fun the_count_never_drops_below_the_messages_on_screen() {
        assertEquals(3, thread_row_count(api_count = 2, distinct_messages = 3, correction = null))
        assertEquals(
            2,
            thread_row_count(api_count = 5, distinct_messages = 2, correction = ThreadCountCorrection(5, 1)),
        )
        assertEquals(1, thread_row_count(api_count = 0, distinct_messages = 0, correction = null))
    }

    @Test
    fun corrections_are_scoped_to_their_own_thread() {
        val corrections = mapOf("t1" to ThreadCountCorrection(claimed = 3, loaded = 1))
        val rows = group_by_thread(
            listOf(email("m1", "t1", claimed = 3), email("m2", "t2", claimed = 3)),
            corrections,
        ).associateBy { it.thread_id }
        assertEquals(1, rows.getValue("t1").message_count)
        assertEquals(3, rows.getValue("t2").message_count)
    }

    @Test
    fun a_correction_counts_distinct_loaded_messages() {
        val correction = thread_count_correction_for(3, listOf("a", "a", "b"), load_limit = null)
        assertEquals(ThreadCountCorrection(claimed = 3, loaded = 2), correction)
    }

    @Test
    fun a_capped_load_never_becomes_a_correction() {
        assertNull(thread_count_correction_for(40, List(20) { "m$it" }, load_limit = 20))
    }

    @Test
    fun an_empty_load_never_becomes_a_correction() {
        assertNull(thread_count_correction_for(3, emptyList(), load_limit = null))
    }

    @Test
    fun a_missing_server_count_is_treated_as_one() {
        assertEquals(ThreadCountCorrection(1, 1), thread_count_correction_for(0, listOf("a"), load_limit = null))
    }

    @Test
    fun corrected_count_ignores_a_mismatched_claim() {
        assertEquals(6, corrected_thread_count(6, ThreadCountCorrection(claimed = 5, loaded = 2)))
        assertEquals(2, corrected_thread_count(5, ThreadCountCorrection(claimed = 5, loaded = 2)))
        assertEquals(5, corrected_thread_count(5, null))
    }

    @Test
    fun flat_rows_always_count_one() {
        val rows = flat_thread_rows(listOf(email("m1", "t1", claimed = 7)))
        assertEquals(1, rows.single().message_count)
    }
}
