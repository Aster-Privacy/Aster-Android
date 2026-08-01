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

package org.astermail.android.mail

import org.astermail.android.api.mail.MailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllMailScopeTest {

    private fun item(spam: Boolean = false, trashed: Boolean = false) = InboxItem(
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
        is_trashed = trashed,
        is_archived = false,
        is_spam = spam,
        labels = emptyList(),
        raw_item = MailItem(id = "1"),
    )

    @Test
    fun folder_ids_encode_each_scope() {
        assertEquals("all", all_mail_folder_id(include_spam = false, include_trash = false))
        assertEquals("all+spam", all_mail_folder_id(include_spam = true, include_trash = false))
        assertEquals("all+trash", all_mail_folder_id(include_spam = false, include_trash = true))
        assertEquals("all+spam+trash", all_mail_folder_id(include_spam = true, include_trash = true))
        assertEquals(4, all_mail_folder_ids.distinct().size)
    }

    @Test
    fun scope_predicates_only_match_all_mail_folders() {
        assertTrue(is_all_mail_folder("all"))
        assertTrue(is_all_mail_folder("all+spam"))
        assertFalse(is_all_mail_folder("allowance"))
        assertFalse(is_all_mail_folder("inbox"))
        assertFalse(all_mail_includes_spam("all"))
        assertTrue(all_mail_includes_spam("all+spam+trash"))
        assertFalse(all_mail_includes_trash("all+spam"))
        assertTrue(all_mail_includes_trash("all+trash"))
    }

    @Test
    fun default_all_mail_excludes_spam_and_trash() {
        assertTrue(folder_matches_item("all", item()))
        assertFalse(folder_matches_item("all", item(spam = true)))
        assertFalse(folder_matches_item("all", item(trashed = true)))
    }

    @Test
    fun opting_in_includes_spam_and_trash() {
        assertTrue(folder_matches_item("all+spam", item(spam = true)))
        assertFalse(folder_matches_item("all+spam", item(trashed = true)))
        assertTrue(folder_matches_item("all+trash", item(trashed = true)))
        assertFalse(folder_matches_item("all+trash", item(spam = true)))
        assertTrue(folder_matches_item("all+spam+trash", item(spam = true)))
        assertTrue(folder_matches_item("all+spam+trash", item(trashed = true)))
    }

    @Test
    fun filter_kinds_map_to_mail_folders() {
        assertEquals("label:x", mail_folder_for_filter(filter_kind_label, "x"))
        assertEquals("tag:x", mail_folder_for_filter(filter_kind_tag, "x"))
        assertEquals("routing:x", mail_folder_for_filter(filter_kind_alias, "x"))
        assertEquals("x", mail_folder_for_filter(filter_kind_folder, "x"))
        assertEquals("inbox", mail_folder_for_filter(null, "x"))
    }
}
