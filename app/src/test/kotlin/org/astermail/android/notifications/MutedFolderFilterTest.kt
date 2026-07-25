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
import org.astermail.android.api.mail.MailItemFolder
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutedFolderFilterTest {

    private fun item(
        labels: List<String> = emptyList(),
        folder_token: String? = null,
        folders: List<MailItemFolder>? = null,
    ): InboxItem {
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
            labels = labels,
            raw_item = MailItem(
                id = "item1",
                folder_token = folder_token,
                folders = folders,
            ),
        )
    }

    @Test
    fun `empty muted set never matches`() {
        val foldered = item(labels = listOf("tokA"), folder_token = "tokA")
        assertFalse(MailPollingWorker.is_item_in_muted_folder(foldered, emptySet()))
    }

    @Test
    fun `item with muted label token is muted`() {
        val foldered = item(labels = listOf("tokA", "tokB"))
        assertTrue(MailPollingWorker.is_item_in_muted_folder(foldered, setOf("tokB")))
    }

    @Test
    fun `item with muted primary folder token is muted`() {
        val foldered = item(folder_token = "tokC")
        assertTrue(MailPollingWorker.is_item_in_muted_folder(foldered, setOf("tokC")))
    }

    @Test
    fun `item with muted folders entry is muted`() {
        val foldered = item(folders = listOf(MailItemFolder(folder_token = "tokD")))
        assertTrue(MailPollingWorker.is_item_in_muted_folder(foldered, setOf("tokD")))
    }

    @Test
    fun `inbox item without folder tokens is not muted`() {
        val plain = item()
        assertFalse(MailPollingWorker.is_item_in_muted_folder(plain, setOf("tokA", "tokB")))
    }

    @Test
    fun `item in a different folder is not muted`() {
        val foldered = item(
            labels = listOf("tokX"),
            folder_token = "tokX",
            folders = listOf(MailItemFolder(folder_token = "tokX")),
        )
        assertFalse(MailPollingWorker.is_item_in_muted_folder(foldered, setOf("tokY")))
    }
}
