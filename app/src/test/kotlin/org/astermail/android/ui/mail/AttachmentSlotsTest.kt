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

import io.mockk.every
import io.mockk.mockk
import org.astermail.android.mail.InboundAttachmentEntry
import org.astermail.android.mail.ThreadMessageDecrypted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentSlotsTest {

    private fun message(id: String, count: Int?, flagged: Boolean = false): ThreadMessageDecrypted {
        val raw = mockk<org.astermail.android.api.mail.ThreadMessageItem>(relaxed = true)
        every { raw.attachment_count } returns count
        every { raw.has_attachments } returns flagged
        return ThreadMessageDecrypted(
            id = id,
            sender_name = "Alice",
            sender_email = "alice@example.com",
            to_label = "me",
            timestamp = "2026-11-24T10:00:00Z",
            body_text = "Hello",
            body_html = null,
            is_encrypted = true,
            is_read = true,
            raw_item = raw,
            has_attachments = flagged || (count ?: 0) > 0,
        )
    }

    @Test
    fun expected_count_prefers_the_declared_count() {
        assertEquals(3, expected_attachment_count(message("m", 3)))
        assertEquals(1, expected_attachment_count(message("m", null, flagged = true)))
        assertEquals(0, expected_attachment_count(message("m", null)))
    }

    @Test
    fun slots_take_names_from_hints_and_fill_the_rest() {
        val slots = pending_attachment_slots(
            "m1",
            3,
            listOf(InboundAttachmentEntry(key = "k", filename = " a.pdf ", content_type = "application/pdf", size = 10L)),
        )
        assertEquals(3, slots.size)
        assertEquals("a.pdf", slots[0].filename)
        assertEquals("application/pdf", slots[0].content_type)
        assertEquals(10L, slots[0].size_bytes)
        assertEquals("", slots[1].filename)
        assertTrue(slots.all { it.is_pending_slot() })
        assertEquals(3, slots.map { it.id }.toSet().size)
    }

    @Test
    fun slots_grow_to_the_hint_count_and_are_capped() {
        val hints = List(4) { InboundAttachmentEntry(key = "k$it") }
        assertEquals(4, pending_attachment_slots("m1", 1, hints).size)
        assertEquals(pending_attachment_max_slots, pending_attachment_slots("m1", 500, emptyList()).size)
        assertTrue(pending_attachment_slots("m1", 0, emptyList()).isEmpty())
    }

    @Test
    fun awaiting_ids_cover_loading_and_expected_messages_only() {
        val with_files = message("a", 2)
        val without = message("b", null)
        val loaded = message("c", 1)
        val messages = listOf(with_files, without, loaded)
        val result = attachments_awaiting_ids(
            messages = messages,
            loaded = mapOf("c" to listOf(MessageAttachment(id = "x", filename = "x", content_type = "t", size_bytes = 1))),
            loading = emptySet(),
            settled = emptySet(),
            thread_loading = true,
            failed = false,
        )
        assertEquals(setOf("a"), result)
    }

    @Test
    fun awaiting_ids_stop_once_settled_or_failed() {
        val messages = listOf(message("a", 2))
        assertTrue(attachments_awaiting_ids(messages, emptyMap(), emptySet(), setOf("a"), true, false).isEmpty())
        assertTrue(attachments_awaiting_ids(messages, emptyMap(), setOf("a"), emptySet(), true, true).isEmpty())
        assertEquals(setOf("a"), attachments_awaiting_ids(messages, emptyMap(), setOf("a"), setOf("a"), false, false))
    }
}
