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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimisticSendsTest {

    private val started_at_ms = 1_800_000_000_000L

    private fun send(
        pending_id: String,
        account_id: String? = "user_1",
        thread_token: String? = "thread_1",
        reply_to_id: String? = "msg_1",
        started_at: Long = started_at_ms,
    ) = OptimisticSend(
        pending_id = pending_id,
        account_id = account_id,
        thread_token = thread_token,
        reply_to_id = reply_to_id,
        started_at_ms = started_at,
        message = build_optimistic_send_message(
            pending_id = pending_id,
            started_at_ms = started_at,
            sender_email = "me@astermail.org",
            sender_display_name = "Me",
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Re: Hi",
            body_html = "<p>one</p><p>two &amp; three</p>",
            has_attachments = false,
        ),
    )

    private fun server_message(id: String, item_type: String, created_at_ms: Long): ThreadMessageDecrypted {
        val base = send("server").message
        val ts = java.time.Instant.ofEpochMilli(created_at_ms).toString()
        return base.copy(
            id = id,
            timestamp = ts,
            raw_item = base.raw_item.copy(id = id, item_type = item_type, send_status = null, created_at = ts),
        )
    }

    @Test
    fun `optimistic message is a sending sent item with plain text`() {
        val message = send("p1").message
        assertTrue(is_optimistic_send_id(message.id))
        assertEquals("sent", message.raw_item.item_type)
        assertEquals(OPTIMISTIC_SEND_STATUS, message.raw_item.send_status)
        assertEquals("one\ntwo & three", message.body_text)
    }

    @Test
    fun `sends show only in the thread and account they belong to`() {
        val sends = listOf(
            send("mine"),
            send("other_account", account_id = "user_2"),
            send("other_thread", thread_token = "thread_2", reply_to_id = "msg_9"),
        )

        val shown = optimistic_sends_for_thread(sends, "user_1", "thread_1", "msg_1", emptyList())

        assertEquals(listOf(optimistic_send_message_id("mine")), shown.map { it.id })
    }

    @Test
    fun `a reply matches through its parent when the thread has no token yet`() {
        val sends = listOf(send("p1", thread_token = "fresh_token", reply_to_id = "msg_1"))

        val shown = optimistic_sends_for_thread(sends, "user_1", null, "msg_1", emptyList())

        assertEquals(1, shown.size)
    }

    @Test
    fun `nothing shows without an open thread`() {
        val shown = optimistic_sends_for_thread(listOf(send("p1")), "user_1", "thread_1", null, emptyList())

        assertTrue(shown.isEmpty())
    }

    @Test
    fun `sends are ordered by start time`() {
        val sends = listOf(send("late", started_at = started_at_ms + 5_000L), send("early"))

        val shown = optimistic_sends_for_thread(sends, "user_1", "thread_1", "msg_1", emptyList())

        assertEquals(
            listOf(optimistic_send_message_id("early"), optimistic_send_message_id("late")),
            shown.map { it.id },
        )
    }

    @Test
    fun `an uncommitted send stays even when a newer sent message exists`() {
        val shown = optimistic_sends_for_thread(
            listOf(send("p1")),
            "user_1",
            "thread_1",
            "msg_1",
            listOf(server_message("srv", "sent", started_at_ms + 1_000L)),
        )

        assertEquals(1, shown.size)
    }

    @Test
    fun `a committed send gives way to the server copy by id`() {
        val committed = send("p1").copy(committed = true, mail_item_id = "srv_1")

        assertFalse(optimistic_send_has_server_copy(committed, emptyList()))
        assertTrue(optimistic_send_has_server_copy(committed, listOf(server_message("srv_1", "sent", 0L))))
    }

    @Test
    fun `a committed send gives way to a sent message created after it started`() {
        val committed = send("p1").copy(committed = true)

        assertFalse(
            optimistic_send_has_server_copy(
                committed,
                listOf(server_message("old", "sent", started_at_ms - 60_000L)),
            ),
        )
        assertFalse(
            optimistic_send_has_server_copy(
                committed,
                listOf(server_message("incoming", "received", started_at_ms + 1_000L)),
            ),
        )
        assertTrue(
            optimistic_send_has_server_copy(
                committed,
                listOf(server_message("new", "sent", started_at_ms + 1_000L)),
            ),
        )
    }

    @Test
    fun `store marks a send committed once and removes it by pending id`() {
        val store = OptimisticSendStore()
        store.add(send("p1"))
        store.add(send("p2"))

        assertTrue(store.mark_committed("p1", "srv_1"))
        assertFalse(store.mark_committed("p1", "srv_1"))
        assertEquals("srv_1", store.get("p1")?.mail_item_id)
        assertTrue(store.remove("p1"))
        assertFalse(store.remove("p1"))
        assertEquals(listOf("p2"), store.sends.value.map { it.pending_id })
    }

    @Test
    fun `a server copy stamped slightly before the send by a slow clock still matches`() {
        val committed = send("p1").copy(committed = true)

        assertTrue(
            optimistic_send_has_server_copy(
                committed,
                listOf(server_message("skewed", "sent", started_at_ms - 5_000L)),
            ),
        )
    }

    @Test
    fun `the shown send reports its phase and send time`() {
        val queued = send("p1").copy(send_at_ms = started_at_ms + 10_000L)
        val committed = send("p2").copy(committed = true)

        val shown = optimistic_sends_for_thread(listOf(queued, committed), "user_1", "thread_1", "msg_1", emptyList())

        assertEquals(listOf(OPTIMISTIC_SEND_STATUS, OPTIMISTIC_SENT_STATUS), shown.map { it.raw_item.send_status })
        assertEquals(started_at_ms + 10_000L, shown.first().pending_send_at_ms)
    }

    @Test
    fun `a committed send lends its key to its server copy`() {
        val committed = send("p1").copy(committed = true, mail_item_id = "srv_1")
        val pending = send("p2")
        val server = listOf(server_message("srv_1", "sent", started_at_ms + 1_000L))

        assertEquals(
            mapOf("srv_1" to optimistic_send_message_id("p1")),
            optimistic_send_aliases(listOf(committed, pending), "user_1", server),
        )
        assertTrue(optimistic_send_aliases(listOf(committed), "user_2", server).isEmpty())
    }

    @Test
    fun `two sends never claim the same server copy`() {
        val first = send("p1").copy(committed = true)
        val second = send("p2", started_at = started_at_ms + 1_000L).copy(committed = true)
        val server = listOf(server_message("srv_1", "sent", started_at_ms + 2_000L))

        assertEquals(1, optimistic_send_aliases(listOf(first, second), "user_1", server).size)
    }
}
