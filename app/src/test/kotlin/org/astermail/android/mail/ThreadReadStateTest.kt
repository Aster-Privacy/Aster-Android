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

package org.astermail.android.mail

import io.mockk.mockk
import org.astermail.android.api.mail.MailItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadReadStateTest {

    private fun item(id: String, read: Boolean) = InboxItem(
        id = id,
        thread_token = null,
        thread_message_count = 1,
        sender_name = "a",
        sender_email = "a@b.c",
        subject = "s",
        preview = "p",
        timestamp = "2026-08-01T00:00:00Z",
        is_read = read,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = MailItem(id = id),
    )

    private fun message(id: String, read: Boolean) = ThreadMessageDecrypted(
        id = id,
        sender_name = "",
        sender_email = "",
        to_label = "me",
        timestamp = "2026-08-01T00:00:00Z",
        body_text = "",
        body_html = null,
        is_encrypted = false,
        is_read = read,
        raw_item = mockk(relaxed = true),
    )

    @Test
    fun opened_message_turns_read_with_the_item() {
        val state = ThreadUiState(
            item = item("a", read = false),
            messages = listOf(message("a", read = false), message("b", read = false)),
        )

        val next = thread_with_read(state, "a", true)

        assertTrue(next.item!!.is_read)
        assertTrue(next.messages.first { it.id == "a" }.is_read)
        assertFalse(next.messages.first { it.id == "b" }.is_read)
    }

    @Test
    fun message_in_thread_updates_when_item_is_another_id() {
        val state = ThreadUiState(
            item = item("a", read = true),
            messages = listOf(message("a", read = true), message("b", read = false)),
        )

        val next = thread_with_read(state, "b", true)

        assertTrue(next.messages.first { it.id == "b" }.is_read)
        assertTrue(next.item!!.is_read)
    }

    @Test
    fun unrelated_id_keeps_the_same_state() {
        val state = ThreadUiState(item = item("a", read = false), messages = listOf(message("a", read = false)))

        assertSame(state, thread_with_read(state, "z", true))
    }

    @Test
    fun matching_state_is_left_alone() {
        val state = ThreadUiState(item = item("a", read = true), messages = listOf(message("a", read = true)))

        assertSame(state, thread_with_read(state, "a", true))
    }
}
