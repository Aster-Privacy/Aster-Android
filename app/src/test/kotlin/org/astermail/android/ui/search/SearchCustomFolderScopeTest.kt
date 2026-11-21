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
import org.astermail.android.mail.filter_kind_alias
import org.astermail.android.mail.filter_kind_folder
import org.astermail.android.mail.filter_kind_label
import org.astermail.android.mail.filter_kind_tag
import org.astermail.android.mail.search_filter_scope_query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCustomFolderScopeTest {

    private fun item(
        id: String,
        folders: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        is_trashed: Boolean = false,
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
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = is_trashed,
        is_archived = false,
        is_spam = false,
        labels = folders,
        tag_tokens = tags,
        raw_item = MailItem(id = id, item_type = "received"),
    )

    private val index = SearchLabelIndex(
        listOf(
            SearchLabelEntry("Receipts", "folder_receipts", is_folder = true),
            SearchLabelEntry("Receipts 2026", "folder_receipts_child", is_folder = true),
            SearchLabelEntry("Tax \"final\" papers", "folder_tax", is_folder = true),
            SearchLabelEntry("Receipts", "tag_receipts"),
        ),
    )

    private val corpus = listOf(
        item("loose"),
        item("in_folder", folders = listOf("folder_receipts")),
        item("in_child", folders = listOf("folder_receipts_child")),
        item("in_tax", folders = listOf("folder_tax")),
        item("tagged", tags = listOf("tag_receipts")),
        item("trashed_in_folder", folders = listOf("folder_receipts"), is_trashed = true),
    )

    private fun search(query: String): List<String> {
        val parsed = parse_query(query)
        val env = current_search_env(index)
        return corpus.filter { matches_item(it, parsed, env = env) }.map { it.id }
    }

    private fun opened_from_folder(name: String, text: String): List<String> =
        search(listOfNotNull(search_filter_scope_query(filter_kind_folder, name), text).joinToString(" "))

    @Test
    fun a_custom_folder_opens_search_with_its_folder_operator() {
        assertEquals("folder:Receipts", search_filter_scope_query(filter_kind_folder, "Receipts"))
        assertEquals("folder:\"Receipts 2026\"", search_filter_scope_query(filter_kind_folder, " Receipts 2026 "))
        assertEquals(
            "folder:\"Tax final papers\"",
            search_filter_scope_query(filter_kind_folder, "Tax \"final\" papers"),
        )
    }

    @Test
    fun a_label_view_opens_search_with_its_label_operator() {
        assertEquals("label:Receipts", search_filter_scope_query(filter_kind_tag, "Receipts"))
        assertEquals("label:\"Old mail\"", search_filter_scope_query(filter_kind_label, "Old mail"))
    }

    @Test
    fun views_without_a_readable_name_or_folder_kind_stay_unscoped() {
        assertNull(search_filter_scope_query(filter_kind_folder, "  "))
        assertNull(search_filter_scope_query(filter_kind_alias, "me@example.com"))
        assertNull(search_filter_scope_query(null, "Inbox"))
    }

    @Test
    fun the_scope_query_parses_back_into_one_folder_operator() {
        val parsed = parse_query(search_filter_scope_query(filter_kind_folder, "Receipts 2026").orEmpty())
        assertEquals(listOf(SearchOperator(false, "folder", "receipts 2026")), parsed.operators)
        assertEquals("", parsed.free_text)
    }

    @Test
    fun search_opened_in_a_folder_finds_only_that_folder() {
        assertEquals(listOf("in_folder"), opened_from_folder("Receipts", "report"))
    }

    @Test
    fun child_folders_are_not_part_of_the_scope() {
        assertEquals(listOf("in_child"), opened_from_folder("Receipts 2026", "report"))
        assertEquals(listOf("in_folder"), search("folder:receipts"))
    }

    @Test
    fun a_folder_operator_ignores_labels_with_the_same_name() {
        assertEquals(listOf("in_folder"), search("folder:Receipts"))
        assertEquals(listOf("in_folder", "tagged"), search("label:Receipts"))
    }

    @Test
    fun a_folder_name_with_quotes_still_matches() {
        assertEquals(listOf("in_tax"), opened_from_folder("Tax \"final\" papers", "report"))
    }

    @Test
    fun removing_the_folder_operator_widens_to_all_mail() {
        assertEquals(listOf("loose", "in_folder", "in_child", "in_tax", "tagged"), search("report"))
    }

    @Test
    fun trashed_mail_in_the_folder_needs_a_trash_scope() {
        assertEquals(listOf("in_folder", "trashed_in_folder"), search("folder:Receipts in:anywhere"))
    }

    @Test
    fun an_unknown_folder_name_matches_nothing() {
        assertEquals(emptyList<String>(), search("folder:Missing report"))
    }

    @Test
    fun a_folder_operator_keeps_the_typed_text_filter() {
        assertEquals(emptyList<String>(), opened_from_folder("Receipts", "invoice"))
    }

    @Test
    fun a_folder_shown_under_a_placeholder_name_stays_unscoped() {
        assertNull(search_filter_scope_query(filter_kind_folder, "Unreadable", placeholder_name = "Unreadable"))
        assertEquals(
            "folder:Receipts",
            search_filter_scope_query(filter_kind_folder, "Receipts", placeholder_name = "Unreadable"),
        )
    }
}
