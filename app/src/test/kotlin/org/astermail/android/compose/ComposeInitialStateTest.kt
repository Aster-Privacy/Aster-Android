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

package org.astermail.android.compose

import org.astermail.android.ui.compose.build_compose_initial_state
import org.astermail.android.ui.compose.compose_identity_snapshot
import org.astermail.android.ui.compose.compose_screen_args
import org.astermail.android.ui.compose.compose_thread_message
import org.astermail.android.ui.compose.compose_thread_snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeInitialStateTest {

    private val identity = compose_identity_snapshot(
        user_email = "me@astermail.org",
        display_name = "Me",
        alias_options = listOf("me@astermail.org", "work@astermail.org"),
        primary_sender_email = "me@astermail.org",
        alias_display_names = mapOf("work@astermail.org" to "Work"),
    )

    private val incoming = compose_thread_message(
        id = "msg_1",
        sender_email = "sender@example.com",
        to_addresses = listOf("work@astermail.org", "peer@example.com"),
        cc_addresses = listOf("cc@example.com", "me@astermail.org"),
        subject = "Quarterly plan",
        body_html = "<p>Original body</p>",
        timestamp = "2026-08-28T10:00:00Z",
        delivered_to = "work@astermail.org",
    )

    private val thread = compose_thread_snapshot(
        item_id = "msg_1",
        item_subject = "Quarterly plan",
        messages = listOf(incoming),
    )

    @Test
    fun new_message_is_complete_on_first_build() {
        val state = build_compose_initial_state(
            args = compose_screen_args(mode = "new"),
            identity = identity,
            thread = compose_thread_snapshot(),
        )
        assertEquals("me@astermail.org", state.from_address)
        assertEquals("Me", state.sender_display_name)
        assertEquals(emptyList<String>(), state.to_chips)
        assertEquals("", state.subject)
        assertTrue(state.is_complete)
    }

    @Test
    fun reply_is_complete_on_first_build() {
        val state = build_compose_initial_state(
            args = compose_screen_args(reply_to = "msg_1", mode = "reply"),
            identity = identity,
            thread = thread,
        )
        assertEquals("work@astermail.org", state.from_address)
        assertEquals("Work", state.sender_display_name)
        assertEquals(listOf("sender@example.com"), state.to_chips)
        assertEquals(emptyList<String>(), state.cc_chips)
        assertEquals("Re: Quarterly plan", state.subject)
        assertEquals("<p>Original body</p>", state.quoted_html)
        assertEquals("sender@example.com", state.quoted_sender)
        assertTrue(state.is_complete)
    }

    @Test
    fun reply_all_is_complete_on_first_build() {
        val state = build_compose_initial_state(
            args = compose_screen_args(reply_to = "msg_1", mode = "reply_all"),
            identity = identity,
            thread = thread,
        )
        assertEquals("work@astermail.org", state.from_address)
        assertEquals(
            listOf("sender@example.com", "work@astermail.org", "peer@example.com"),
            state.to_chips,
        )
        assertEquals(listOf("cc@example.com"), state.cc_chips)
        assertEquals("Re: Quarterly plan", state.subject)
        assertEquals("<p>Original body</p>", state.quoted_html)
        assertTrue(state.is_complete)
    }

    @Test
    fun forward_is_complete_on_first_build() {
        val state = build_compose_initial_state(
            args = compose_screen_args(reply_to = "msg_1", mode = "forward"),
            identity = identity,
            thread = thread,
        )
        assertEquals("work@astermail.org", state.from_address)
        assertEquals(emptyList<String>(), state.to_chips)
        assertEquals("Fwd: Quarterly plan", state.subject)
        assertEquals("<p>Original body</p>", state.quoted_html)
        assertTrue(state.is_complete)
    }

    @Test
    fun later_data_does_not_change_a_complete_state() {
        val modes = listOf("new", "reply", "reply_all", "forward")
        modes.forEach { mode ->
            val args = if (mode == "new") {
                compose_screen_args(mode = mode)
            } else {
                compose_screen_args(reply_to = "msg_1", mode = mode)
            }
            val seed_thread = if (mode == "new") compose_thread_snapshot() else thread
            val first = build_compose_initial_state(args, identity, seed_thread)
            val later_identity = identity.copy(
                alias_options = identity.alias_options + "extra@astermail.org",
            )
            val later_thread = if (mode == "new") {
                compose_thread_snapshot()
            } else {
                thread.copy(messages = thread.messages + incoming.copy(id = "msg_2"))
            }
            val second = build_compose_initial_state(args, later_identity, later_thread)
            assertEquals(first.from_address, second.from_address)
            assertEquals(first.to_chips, second.to_chips)
            assertEquals(first.cc_chips, second.cc_chips)
            assertEquals(first.subject, second.subject)
            assertEquals(first.quoted_html, second.quoted_html)
        }
    }

    @Test
    fun missing_thread_data_reports_a_skeleton_instead_of_a_wrong_default() {
        val state = build_compose_initial_state(
            args = compose_screen_args(reply_to = "msg_1", mode = "reply"),
            identity = identity,
            thread = compose_thread_snapshot(),
        )
        assertTrue(state.thread_is_skeleton)
        assertFalse(state.is_complete)
        assertEquals(emptyList<String>(), state.to_chips)
        assertEquals("", state.subject)
    }

    @Test
    fun missing_identity_reports_a_skeleton_instead_of_a_placeholder_address() {
        val state = build_compose_initial_state(
            args = compose_screen_args(mode = "new"),
            identity = compose_identity_snapshot(),
            thread = compose_thread_snapshot(),
        )
        assertTrue(state.identity_is_skeleton)
        assertEquals("", state.from_address)
    }

    @Test
    fun share_payload_wins_over_thread_derived_values() {
        val state = build_compose_initial_state(
            args = compose_screen_args(
                reply_to = "msg_1",
                mode = "reply",
                share_to = listOf("shared@example.com"),
                share_subject = "Shared subject",
            ),
            identity = identity,
            thread = thread,
        )
        assertEquals(listOf("shared@example.com"), state.to_chips)
        assertEquals("Shared subject", state.subject)
    }

    @Test
    fun `prefill_to becomes one chip per address`() {
        val state = build_compose_initial_state(
            args = compose_screen_args(prefill_to = "a@example.com, b@example.com ,c@example.com"),
            identity = identity,
            thread = compose_thread_snapshot(),
        )

        assertEquals(
            listOf("a@example.com", "b@example.com", "c@example.com"),
            state.to_chips,
        )
    }
}
