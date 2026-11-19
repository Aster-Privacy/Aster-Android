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

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailCustomFolderTest {

    private fun item(labels: List<String>) = InboxItem(
        id = "m1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        subject = "Subject",
        preview = "Preview",
        timestamp = "2026-07-26T00:00:00Z",
        is_read = true,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = labels,
        routing_token = null,
        raw_item = MailItem(id = "m1", item_type = "received"),
    )

    private fun folder(token: String, name: String?) = LabelItem(
        id = token,
        label_token = token,
        encrypted_name = name,
        name_nonce = "n",
        folder_type = "folder",
    )

    @Test
    fun long_folder_names_are_shown() {
        listOf(
            "Newsletters and promotions",
            "Rachunkiiumowy2026Firma",
            "Zamówienia internetowe2026",
            "Faktury i rachunki firmowe",
        ).forEach { name ->
            val found = detail_custom_folder(item(listOf("f1")), listOf(folder("f1", name)))
            assertEquals(name, found?.encrypted_name)
        }
    }

    @Test
    fun folders_without_a_name_are_not_shown() {
        assertNull(detail_custom_folder(item(listOf("f1")), listOf(folder("f1", null))))
        assertNull(detail_custom_folder(item(listOf("f1")), listOf(folder("f1", ""))))
    }
}
