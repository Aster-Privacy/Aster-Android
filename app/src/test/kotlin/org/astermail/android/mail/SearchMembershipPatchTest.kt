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

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchMembershipPatchTest {
    private fun item(id: String, tags: List<String> = emptyList(), labels: List<String> = emptyList()) = InboxItem(
        id = id,
        thread_token = "",
        thread_message_count = 1,
        sender_name = "News",
        sender_email = "news@example.com",
        subject = "Subject $id",
        preview = "",
        timestamp = "2026-04-26T10:00:00Z",
        is_read = true,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = labels,
        tag_tokens = tags,
        raw_item = mockk(relaxed = true),
    )

    @Test
    fun `adds a label only to the chosen results`() {
        val items = listOf(item("a"), item("b", listOf("t2")), item("c"))

        val next = with_tag_token(items, setOf("a", "b"), "t1", add = true)

        assertEquals(listOf(listOf("t1"), listOf("t2", "t1"), emptyList()), next.map { it.tag_tokens })
        assertSame(items[2], next[2])
    }

    @Test
    fun `removes a label without touching other labels`() {
        val items = listOf(item("a", listOf("t1", "t2")), item("b", listOf("t1")))

        val next = with_tag_token(items, setOf("a", "b"), "t1", add = false)

        assertEquals(listOf(listOf("t2"), emptyList()), next.map { it.tag_tokens })
    }

    @Test
    fun `returns the same list when nothing changes`() {
        val items = listOf(item("a", listOf("t1")))

        assertSame(items, with_tag_token(items, setOf("a"), "t1", add = true))
        assertSame(items, with_tag_token(items, setOf("z"), "t1", add = false))
    }

    @Test
    fun `restores the labels each result had before`() {
        val items = listOf(item("a", listOf("t1")), item("b", listOf("t1", "t2")))

        val next = with_restored_tag_tokens(items, mapOf("a" to emptyList()))

        assertEquals(listOf(emptyList(), listOf("t1", "t2")), next.map { it.tag_tokens })
    }

    @Test
    fun `moves results to a folder in place`() {
        val items = listOf(item("a", labels = listOf("inbox")), item("b"))

        val next = with_folder_labels(items, mapOf("a" to listOf("f1")))

        assertEquals(listOf(listOf("f1"), emptyList()), next.map { it.labels })
        assertSame(items[1], next[1])
    }
}
