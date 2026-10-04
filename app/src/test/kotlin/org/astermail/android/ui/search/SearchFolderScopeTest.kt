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


package org.astermail.android.ui.search

import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.all_mail_folder_id
import org.astermail.android.mail.carry_search_folder_items
import org.astermail.android.mail.replace_search_folder_items
import org.astermail.android.mail.search_folder_for_scope
import org.astermail.android.mail.search_folder_item_type
import org.astermail.android.mail.search_scope_query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFolderScopeTest {

    private fun item(
        id: String,
        item_type: String? = "received",
        is_trashed: Boolean = false,
        is_archived: Boolean = false,
        is_spam: Boolean = false,
        is_starred: Boolean = false,
    ) = InboxItem(
        id = id,
        thread_token = null,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        subject = "Quarterly report",
        preview = "Preview",
        timestamp = "2026-08-01T00:00:00Z",
        is_read = true,
        is_starred = is_starred,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = is_trashed,
        is_archived = is_archived,
        is_spam = is_spam,
        labels = emptyList(),
        raw_item = MailItem(id = id, item_type = item_type),
    )

    private val corpus = listOf(
        item("inbox"),
        item("indexed", item_type = null),
        item("archived", is_archived = true),
        item("starred", is_starred = true),
        item("trashed", is_trashed = true),
        item("spam", is_spam = true),
        item("sent", item_type = "sent"),
        item("draft", item_type = "draft"),
    )

    private fun search(query: String): List<String> {
        val parsed = parse_query(query)
        return corpus.filter { matches_item(it, parsed, filter = null) }.map { it.id }
    }

    private fun search_in(folder: String, text: String = "report"): List<String> {
        val scope = search_scope_query(folder)
        return search(listOfNotNull(scope, text).joinToString(" "))
    }

    @Test
    fun sent_scopes_to_sent_items() {
        assertEquals(listOf("sent"), search_in("sent"))
    }

    @Test
    fun drafts_scope_to_draft_items() {
        assertEquals(listOf("draft"), search_in("drafts"))
    }

    @Test
    fun trash_scopes_to_trashed_items() {
        assertEquals(listOf("trashed"), search_in("trash"))
    }

    @Test
    fun spam_scopes_to_spam_items() {
        assertEquals(listOf("spam"), search_in("spam"))
    }

    @Test
    fun archive_scopes_to_archived_items() {
        assertEquals(listOf("archived"), search_in("archive"))
    }

    @Test
    fun starred_scopes_to_starred_items() {
        assertEquals(listOf("starred"), search_in("starred"))
    }

    @Test
    fun all_mail_stays_unscoped() {
        val everything = listOf("inbox", "indexed", "archived", "starred", "sent", "draft")
        assertNull(search_scope_query(all_mail_folder_id(include_spam = false, include_trash = false)))
        assertEquals(everything, search_in(all_mail_folder_id(include_spam = false, include_trash = false)))
    }

    @Test
    fun in_sent_and_in_drafts_match_by_item_type() {
        assertEquals(listOf("sent"), search("in:sent"))
        assertEquals(listOf("draft"), search("in:drafts"))
        assertEquals(listOf("draft"), search("in:draft"))
    }

    @Test
    fun in_inbox_excludes_sent_and_drafts() {
        assertEquals(listOf("inbox", "indexed", "starred"), search("in:inbox"))
    }

    @Test
    fun explicit_in_all_overrides_the_folder_scope() {
        assertEquals(
            listOf("inbox", "indexed", "archived", "starred", "sent", "draft"),
            search("${search_scope_query("sent")} in:all report"),
        )
    }

    @Test
    fun explicit_in_folder_overrides_the_folder_scope() {
        assertEquals(listOf("sent"), search("${search_scope_query("drafts")} in:sent"))
        assertEquals(listOf("trashed"), search("${search_scope_query("sent")} in:trash"))
    }

    @Test
    fun negated_in_is_kept_alongside_the_scope() {
        assertEquals(listOf("inbox", "indexed", "archived"), search("in:all -in:starred -in:sent -in:drafts"))
    }

    @Test
    fun scope_values_map_to_fetchable_folders() {
        assertEquals("sent", search_folder_for_scope("sent"))
        assertEquals("drafts", search_folder_for_scope("drafts"))
        assertEquals("drafts", search_folder_for_scope("draft"))
        assertNull(search_folder_for_scope("trash"))
        assertEquals("sent", search_folder_item_type("sent"))
        assertEquals("draft", search_folder_item_type("drafts"))
        assertNull(search_folder_item_type("inbox"))
    }

    @Test
    fun replace_swaps_the_folder_items_and_keeps_the_rest() {
        val current = listOf(item("inbox"), item("old_draft", item_type = "draft"), item("sent", item_type = "sent"))
        val fetched = listOf(item("new_draft", item_type = "draft"))
        assertEquals(
            listOf("inbox", "sent", "new_draft"),
            replace_search_folder_items(current, "draft", fetched).map { it.id },
        )
    }

    @Test
    fun index_refresh_keeps_fetched_sent_and_draft_items() {
        val previous = listOf(item("inbox"), item("sent", item_type = "sent"), item("draft", item_type = "draft"))
        val fresh = listOf(item("inbox", item_type = null), item("new"))
        assertEquals(
            listOf("inbox", "new", "sent", "draft"),
            carry_search_folder_items(fresh, previous).map { it.id },
        )
    }
}
