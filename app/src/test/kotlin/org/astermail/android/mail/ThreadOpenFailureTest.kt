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

package org.astermail.android.mail

import org.astermail.android.api.mail.MailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadOpenFailureTest {

    private fun seed(id: String = "msg_1", token: String? = "thread_1") = InboxItem(
        id = id,
        thread_token = token,
        thread_message_count = 2,
        sender_name = "Friend",
        sender_email = "friend@astermail.org",
        subject = "Hi",
        preview = "p",
        timestamp = "2026-11-25T00:00:00Z",
        is_read = true,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = MailItem(id = id),
    )

    private fun message(id: String, pending_id: String = id) = build_optimistic_send_message(
        pending_id = pending_id,
        started_at_ms = 1_800_000_000_000L,
        sender_email = "me@astermail.org",
        sender_display_name = "Me",
        to = listOf("friend@astermail.org"),
        cc = emptyList(),
        bcc = emptyList(),
        subject = "Re: Hi",
        body_html = "<p>cached</p>",
        has_attachments = false,
    ).let { it.copy(id = id, is_body_pending = false, raw_item = it.raw_item.copy(id = id, send_status = null)) }

    private fun pending_reply(thread_token: String?, reply_to_id: String?) = OptimisticSend(
        pending_id = "p_reply",
        account_id = "user_1",
        thread_token = thread_token,
        reply_to_id = reply_to_id,
        started_at_ms = 1_800_000_100_000L,
        phase = OptimisticSendPhase.WAITING,
        message = build_optimistic_send_message(
            pending_id = "p_reply",
            started_at_ms = 1_800_000_100_000L,
            sender_email = "me@astermail.org",
            sender_display_name = "Me",
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Re: Hi",
            body_html = "<p>my reply</p>",
            has_attachments = false,
        ),
    )

    private fun shown_replies(state: ThreadUiState, send: OptimisticSend): List<ThreadMessageDecrypted> {
        val target = thread_optimistic_target(state)
        return optimistic_sends_for_thread(
            sends = listOf(send),
            account_id = "user_1",
            thread_token = target.thread_token,
            item_id = target.item_id,
            server_messages = state.messages,
        )
    }

    @Test
    fun `offline reopen with a cached thread keeps the messages and marks the thread offline`() {
        val cached = ThreadUiState(
            item = seed(),
            messages = listOf(message("msg_0"), message("msg_1")),
            is_loading = true,
        )
        val state = thread_state_after_open_failure(
            item_id = "msg_1",
            keep = cached,
            seed = seed(),
            offline_fallback = null,
            missing_on_server = false,
            transient = true,
            error_text = "offline",
        )
        assertNull(state.error)
        assertTrue(state.offline)
        assertFalse(state.is_loading)
        assertEquals(listOf("msg_0", "msg_1"), state.messages.map { it.id })
        assertEquals(listOf(optimistic_send_message_id("p_reply")), shown_replies(state, pending_reply("thread_1", "msg_1")).map { it.id })
    }

    @Test
    fun `offline reopen from the inbox copy marks the thread offline`() {
        val state = thread_state_after_open_failure(
            item_id = "msg_1",
            keep = ThreadUiState(is_loading = true),
            seed = seed(),
            offline_fallback = message("msg_1"),
            missing_on_server = false,
            transient = true,
            error_text = "offline",
        )
        assertNull(state.error)
        assertTrue(state.offline)
        assertEquals(listOf(optimistic_send_message_id("p_reply")), shown_replies(state, pending_reply("thread_1", "msg_1")).map { it.id })
    }

    @Test
    fun `offline reopen without a cached copy keeps the error and still shows the pending reply`() {
        val state = thread_state_after_open_failure(
            item_id = "msg_1",
            keep = ThreadUiState(is_loading = true),
            seed = seed(),
            offline_fallback = null,
            missing_on_server = false,
            transient = true,
            error_text = "offline",
        )
        assertEquals("offline", state.error)
        assertTrue(state.offline)
        assertTrue(state.messages.isEmpty())
        assertEquals(listOf(optimistic_send_message_id("p_reply")), shown_replies(state, pending_reply("thread_1", null)).map { it.id })
    }

    @Test
    fun `a failed open with no inbox copy still shows a reply to that message`() {
        val state = thread_state_after_open_failure(
            item_id = "msg_1",
            keep = ThreadUiState(is_loading = true),
            seed = null,
            offline_fallback = null,
            missing_on_server = false,
            transient = false,
            error_text = "failed",
        )
        assertEquals("failed", state.error)
        assertFalse(state.offline)
        assertEquals(listOf(optimistic_send_message_id("p_reply")), shown_replies(state, pending_reply(null, "msg_1")).map { it.id })
    }

    @Test
    fun `an error for another message does not show the pending reply`() {
        val state = thread_state_after_open_failure(
            item_id = "msg_9",
            keep = ThreadUiState(is_loading = true),
            seed = seed(id = "msg_9", token = "thread_9"),
            offline_fallback = null,
            missing_on_server = false,
            transient = true,
            error_text = "offline",
        )
        assertTrue(shown_replies(state, pending_reply("thread_1", "msg_1")).isEmpty())
    }

    @Test
    fun `a message deleted on the server shows the deleted error and is not offline`() {
        val state = thread_state_after_open_failure(
            item_id = "msg_1",
            keep = ThreadUiState(is_loading = true),
            seed = seed(),
            offline_fallback = null,
            missing_on_server = true,
            transient = false,
            error_text = "gone",
        )
        assertEquals("gone", state.error)
        assertFalse(state.offline)
    }

    @Test
    fun `a loaded thread targets its own item`() {
        val state = ThreadUiState(item = seed(), messages = listOf(message("msg_1")))
        val target = thread_optimistic_target(state)
        assertEquals("msg_1", target.item_id)
        assertEquals("thread_1", target.thread_token)
    }
}
