// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

import org.astermail.android.api.mail.MailItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderMatchesTest {

    private fun item(
        item_type: String? = null,
        is_trashed: Boolean = false,
        is_archived: Boolean = false,
        is_spam: Boolean = false,
        is_starred: Boolean = false,
        labels: List<String> = emptyList(),
    ) = InboxItem(
        id = "m1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        subject = "Subject",
        preview = "Preview",
        timestamp = "2026-07-26T00:00:00Z",
        is_read = true,
        is_starred = is_starred,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = is_trashed,
        is_archived = is_archived,
        is_spam = is_spam,
        labels = labels,
        raw_item = MailItem(id = "m1", item_type = item_type),
    )

    @Test
    fun sent_folder_matches_sent_items() {
        assertTrue(folder_matches_item("sent", item(item_type = "sent")))
    }

    @Test
    fun sent_folder_rejects_received_items() {
        assertFalse(folder_matches_item("sent", item(item_type = "received")))
        assertFalse(folder_matches_item("sent", item(item_type = null)))
    }

    @Test
    fun sent_folder_rejects_trashed_sent_items() {
        assertFalse(folder_matches_item("sent", item(item_type = "sent", is_trashed = true)))
    }

    @Test
    fun drafts_scheduled_and_outbox_match_their_item_types() {
        assertTrue(folder_matches_item("drafts", item(item_type = "draft")))
        assertTrue(folder_matches_item("scheduled", item(item_type = "scheduled")))
        assertTrue(folder_matches_item("outbox", item(item_type = "outbox")))
        assertFalse(folder_matches_item("drafts", item(item_type = "sent")))
        assertFalse(folder_matches_item("outbox", item(item_type = "scheduled")))
    }

    @Test
    fun sent_items_are_not_dropped_by_opaque_label_tokens() {
        assertTrue(folder_matches_item("sent", item(item_type = "sent", labels = listOf("dG9rZW4="))))
    }

    @Test
    fun archive_and_all_folders_still_behave() {
        assertTrue(folder_matches_item("archive", item(is_archived = true)))
        assertFalse(folder_matches_item("archive", item(is_archived = false)))
        assertTrue(folder_matches_item("all", item(is_archived = true)))
        assertFalse(folder_matches_item("all", item(is_trashed = true)))
    }

    @Test
    fun inbox_excludes_archived_spam_and_trash() {
        assertTrue(folder_matches_item("inbox", item()))
        assertFalse(folder_matches_item("inbox", item(is_archived = true)))
        assertFalse(folder_matches_item("inbox", item(is_spam = true)))
        assertFalse(folder_matches_item("inbox", item(is_trashed = true)))
    }

    @Test
    fun label_and_tag_prefixes_match_tokens() {
        assertTrue(folder_matches_item("label:abc", item(labels = listOf("abc"))))
        assertFalse(folder_matches_item("label:abc", item(labels = listOf("xyz"))))
    }
}
