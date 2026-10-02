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

import org.astermail.android.api.mail.ThreadMessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThreadOpenedMergeTest {

    private fun message(
        id: String,
        timestamp: String,
        body: String,
        is_body_pending: Boolean = false,
    ) = ThreadMessageDecrypted(
        id = id,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        to_label = "me",
        timestamp = timestamp,
        body_text = body,
        body_html = null,
        is_encrypted = true,
        is_read = false,
        raw_item = ThreadMessageItem(id = id),
        is_body_pending = is_body_pending,
    )

    private val older = message("old", "2026-10-01T08:00:00Z", "Earlier")
    private val opened = message("new", "2026-10-02T08:00:00Z", "New mail")

    @Test
    fun the_opened_message_is_added_when_the_thread_predates_it() {
        val result = include_opened_message(listOf(older), opened)

        assertEquals(listOf("old", "new"), result.map { it.id })
    }

    @Test
    fun the_opened_message_is_placed_by_time() {
        val later = message("later", "2026-10-03T08:00:00+00:00", "Later")
        val result = include_opened_message(listOf(older, later), opened)

        assertEquals(listOf("old", "new", "later"), result.map { it.id })
    }

    @Test
    fun an_empty_thread_copy_takes_the_opened_body() {
        val blank = message("new", "2026-10-02T08:00:00Z", "", is_body_pending = true)
        val result = include_opened_message(listOf(older, blank), opened)

        assertEquals("New mail", result[1].body_text)
        assertEquals(false, result[1].is_body_pending)
    }

    @Test
    fun a_readable_thread_copy_is_kept() {
        val list = listOf(older, message("new", "2026-10-02T08:00:00Z", "Server"))

        assertSame(list, include_opened_message(list, opened))
    }
}
