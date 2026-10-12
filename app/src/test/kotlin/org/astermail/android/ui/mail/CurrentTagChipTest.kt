//
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.mail

import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.tags.TagItem
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentTagChipTest {

    private fun tag(token: String, name: String) = TagItem(
        id = "id_$token",
        tag_token = token,
        encrypted_name = name,
        name_nonce = "nonce_$token",
        encrypted_color = "#FF0000",
        encrypted_icon = "star",
    )

    private val tags = listOf(tag("receipts", "Receipts"), tag("travel", "Travel"))

    private fun item() = InboxItem(
        id = "1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = "a",
        sender_email = "a@b.c",
        subject = "s",
        preview = "p",
        timestamp = "2026-08-01T00:00:00Z",
        is_read = true,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        tag_tokens = listOf("receipts", "travel"),
        raw_item = MailItem(id = "1"),
    )

    @Test
    fun only_tag_views_have_a_current_tag() {
        assertEquals("receipts", current_tag_token("tag:receipts"))
        assertNull(current_tag_token("tag:"))
        assertNull(current_tag_token("inbox"))
        assertNull(current_tag_token("all"))
        assertNull(current_tag_token("starred"))
        assertNull(current_tag_token("label:receipts"))
        assertNull(current_tag_token("receipts"))
    }

    @Test
    fun rows_outside_a_tag_view_keep_every_tag_chip() {
        for (folder in listOf("inbox", "all", "starred", "label:receipts", "receipts")) {
            val email = inbox_item_to_email(item(), tags, hidden_tag_token = current_tag_token(folder))
            assertEquals(listOf("Receipts", "Travel"), email.label_names)
            assertEquals(2, email.label_colors.size)
            assertEquals(2, email.label_icons.size)
        }
    }

    @Test
    fun rows_in_a_tag_view_drop_only_that_tag_chip() {
        val email = inbox_item_to_email(item(), tags, hidden_tag_token = current_tag_token("tag:receipts"))
        assertEquals(listOf("Travel"), email.label_names)
        assertEquals(1, email.label_colors.size)
        assertEquals(listOf("star"), email.label_icons)
    }

    @Test
    fun a_tag_view_for_a_tag_the_row_lacks_changes_nothing() {
        val email = inbox_item_to_email(item(), tags, hidden_tag_token = current_tag_token("tag:other"))
        assertEquals(listOf("Receipts", "Travel"), email.label_names)
    }
}
