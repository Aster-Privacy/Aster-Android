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
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadOpenLayoutTest {

    private val ids = listOf("m1", "m2", "m3", "m4", "m5")

    @Test
    fun a_single_message_thread_opens_expanded() {
        val layout = initial_thread_layout(listOf("m1"), "m1")
        assertEquals(setOf("m1"), layout.expanded_ids)
        assertTrue(layout.hidden_ids.isEmpty())
    }

    @Test
    fun the_latest_message_opens_expanded() {
        val layout = initial_thread_layout(ids, "m1")
        assertTrue(layout.expanded_ids.contains("m5"))
    }

    @Test
    fun the_tapped_message_opens_expanded() {
        val layout = initial_thread_layout(ids, "m2")
        assertTrue(layout.expanded_ids.contains("m2"))
        assertTrue(layout.expanded_ids.contains("m5"))
    }

    @Test
    fun the_middle_of_a_long_thread_is_collapsed_into_the_hidden_group() {
        val layout = initial_thread_layout(ids, "m5")
        assertEquals(setOf("m2", "m3"), layout.hidden_ids)
    }

    @Test
    fun the_hidden_group_never_swallows_the_tapped_message() {
        val layout = initial_thread_layout(ids, "m3")
        assertTrue(layout.hidden_ids.isEmpty())
    }

    @Test
    fun a_short_thread_hides_nothing() {
        val layout = initial_thread_layout(listOf("m1", "m2", "m3", "m4"), "m4")
        assertTrue(layout.hidden_ids.isEmpty())
    }

    @Test
    fun expansion_follows_the_layout_not_the_arrival_order() {
        val layout = initial_thread_layout(ids, "m2")
        assertTrue(
            thread_message_is_expanded("m2", false, ids.size, layout.expanded_ids, layout.known_ids),
        )
        assertFalse(
            thread_message_is_expanded("m4", false, ids.size, layout.expanded_ids, layout.known_ids),
        )
    }

    @Test
    fun a_message_that_arrives_after_the_open_expands_when_it_is_last() {
        val layout = initial_thread_layout(ids, "m5")
        assertTrue(
            thread_message_is_expanded("m6", true, 6, layout.expanded_ids, layout.known_ids),
        )
    }

    @Test
    fun a_lone_message_is_always_expanded() {
        assertTrue(thread_message_is_expanded("m1", true, 1, emptySet(), emptySet()))
    }

    @Test
    fun a_thread_is_incomplete_until_every_message_has_arrived() {
        assertFalse(
            thread_is_complete(
                loaded_count = 1,
                expected_count = 3,
                settled = false,
                any_body_pending = false,
            ),
        )
        assertTrue(
            thread_is_complete(
                loaded_count = 3,
                expected_count = 3,
                settled = false,
                any_body_pending = false,
            ),
        )
    }

    @Test
    fun a_pending_body_keeps_the_thread_incomplete() {
        assertFalse(
            thread_is_complete(
                loaded_count = 3,
                expected_count = 3,
                settled = false,
                any_body_pending = true,
            ),
        )
    }

    @Test
    fun a_settled_load_completes_even_when_the_count_was_wrong() {
        assertTrue(
            thread_is_complete(
                loaded_count = 2,
                expected_count = 9,
                settled = true,
                any_body_pending = true,
            ),
        )
    }

    @Test
    fun an_empty_thread_is_never_complete() {
        assertFalse(
            thread_is_complete(
                loaded_count = 0,
                expected_count = 0,
                settled = true,
                any_body_pending = false,
            ),
        )
    }

    @Test
    fun the_reveal_waits_for_every_expanded_body() {
        assertFalse(
            thread_reveal_ready(
                complete = true,
                expanded_ids = setOf("m1", "m5"),
                hidden_ids = emptySet(),
                ready_body_ids = setOf("m5"),
                body_wait_expired = false,
            ),
        )
        assertTrue(
            thread_reveal_ready(
                complete = true,
                expanded_ids = setOf("m1", "m5"),
                hidden_ids = emptySet(),
                ready_body_ids = setOf("m1", "m5"),
                body_wait_expired = false,
            ),
        )
    }

    @Test
    fun a_hidden_message_never_holds_the_reveal() {
        assertTrue(
            thread_reveal_ready(
                complete = true,
                expanded_ids = setOf("m2", "m5"),
                hidden_ids = setOf("m2"),
                ready_body_ids = setOf("m5"),
                body_wait_expired = false,
            ),
        )
    }

    @Test
    fun the_reveal_never_runs_before_the_thread_is_complete() {
        assertFalse(
            thread_reveal_ready(
                complete = false,
                expanded_ids = emptySet(),
                hidden_ids = emptySet(),
                ready_body_ids = emptySet(),
                body_wait_expired = true,
            ),
        )
    }

    @Test
    fun a_slow_body_stops_holding_the_reveal_once_the_wait_expires() {
        assertTrue(
            thread_reveal_ready(
                complete = true,
                expanded_ids = setOf("m5"),
                hidden_ids = emptySet(),
                ready_body_ids = emptySet(),
                body_wait_expired = true,
            ),
        )
    }
}
