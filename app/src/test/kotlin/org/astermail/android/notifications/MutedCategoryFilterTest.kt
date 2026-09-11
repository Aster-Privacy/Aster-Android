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

package org.astermail.android.notifications

import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.active_category_tabs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutedCategoryFilterTest {

    private fun item(category: String): InboxItem {
        return InboxItem(
            id = "item1",
            thread_token = "t1",
            thread_message_count = 1,
            sender_name = "Alice",
            sender_email = "alice@example.com",
            subject = "Subject",
            preview = "Preview",
            timestamp = "2026-07-25T10:00:00Z",
            is_read = false,
            is_starred = false,
            is_encrypted = true,
            has_attachments = false,
            is_trashed = false,
            is_archived = false,
            is_spam = false,
            labels = emptyList(),
            raw_item = MailItem(id = "item1"),
            category = category,
        )
    }

    @Test
    fun `disabled newsletters fold into muted deals`() {
        val tabs = active_category_tabs(listOf("promotions", "social", "updates"), emptyList(), -1)
        assertTrue(MailPollingWorker.is_item_in_muted_category(item("newsletters"), setOf("promotions"), tabs))
    }

    @Test
    fun `enabled newsletters are not muted by deals`() {
        val tabs = active_category_tabs(listOf("promotions", "newsletters"), emptyList(), -1)
        assertFalse(MailPollingWorker.is_item_in_muted_category(item("newsletters"), setOf("promotions"), tabs))
        assertTrue(MailPollingWorker.is_item_in_muted_category(item("newsletters"), setOf("newsletters"), tabs))
    }

    @Test
    fun `purchases fold to primary when notifications is off`() {
        val tabs = active_category_tabs(listOf("promotions"), emptyList(), -1)
        assertFalse(MailPollingWorker.is_item_in_muted_category(item("transactions"), setOf("updates"), tabs))
    }

    @Test
    fun `without stored tabs the raw category still mutes`() {
        assertTrue(MailPollingWorker.is_item_in_muted_category(item("custom:news"), setOf("custom:news")))
        assertTrue(MailPollingWorker.is_item_in_muted_category(item("forums"), setOf("updates")))
        assertFalse(MailPollingWorker.is_item_in_muted_category(item("social"), emptySet()))
    }
}
