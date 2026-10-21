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
import org.astermail.android.api.mail.MailItemMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadCategoryTest {

    private fun item(
        id: String,
        thread: String,
        category: String,
        timestamp: String,
        pinned: Boolean = false,
        is_read: Boolean = false,
    ) = InboxItem(
        id = id,
        thread_token = thread,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.test",
        subject = "Subject",
        preview = "Preview",
        timestamp = timestamp,
        is_read = is_read,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        category = category,
        raw_item = MailItem(
            id = id,
            item_type = "received",
            metadata = if (pinned) MailItemMetadata(category = category, category_pinned = true) else null,
        ),
    )

    @Test
    fun the_newest_pinned_message_decides_the_thread() {
        val items = listOf(
            item("old", "t1", "primary", "2026-10-01T08:00:00Z", pinned = true),
            item("new", "t1", "promotions", "2026-10-02T08:00:00Z"),
        )
        val result = thread_categories(
            items,
            thread_key = { it.thread_token!! },
            order = compareBy { it.timestamp },
            category = { it.category },
            pinned = { is_category_pinned(it) },
        )
        assertEquals(mapOf("t1" to "primary"), result)
    }

    @Test
    fun without_a_pin_the_newest_message_decides_the_thread() {
        val items = listOf(
            item("old", "t1", "primary", "2026-10-01T08:00:00Z"),
            item("new", "t1", "promotions", "2026-10-02T08:00:00Z"),
        )
        val result = thread_categories(
            items,
            thread_key = { it.thread_token!! },
            order = compareBy { it.timestamp },
            category = { it.category },
            pinned = { is_category_pinned(it) },
        )
        assertEquals(mapOf("t1" to "promotions"), result)
    }

    @Test
    fun unread_counts_follow_the_pinned_category_of_the_thread() {
        val items = listOf(
            item("old", "t1", "primary", "2026-10-01T08:00:00Z", pinned = true),
            item("new", "t1", "promotions", "2026-10-02T08:00:00Z"),
        )
        val grouped = category_unread_counts(items, CATEGORY_TABS)
        assertEquals(1, grouped["primary"])
        assertEquals(0, grouped["promotions"])

        val flat = category_unread_counts(items, CATEGORY_TABS, grouping_enabled = false)
        assertEquals(1, flat["primary"])
        assertEquals(1, flat["promotions"])
    }
}
