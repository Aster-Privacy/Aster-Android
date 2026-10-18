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
import org.astermail.android.mail.search_corpus_folders
import org.astermail.android.mail.search_folder_item_type
import org.astermail.android.mail.search_narrow_query
import org.astermail.android.mail.search_scope_query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        return corpus.filter { matches_item(it, parsed) }.map { it.id }
    }

    private fun search_in(folder: String, text: String = "report"): List<String> {
        val scope = search_scope_query(folder)
        return search(listOfNotNull(scope, text).joinToString(" "))
    }

    private val all_mail = listOf("inbox", "indexed", "archived", "starred", "sent", "draft")

    private fun search_ops(ops: List<SearchOperator>, text: String = "report"): List<String> =
        search((ops.map { "${if (it.negated) "-" else ""}${it.key}:${it.value}" } + text).joinToString(" "))

    private fun opened_from(folder: String): List<SearchOperator> =
        parse_query(search_scope_query(folder).orEmpty()).operators

    @Test
    fun search_defaults_to_all_mail_outside_trash_and_spam() {
        val unscoped = listOf(
            "inbox",
            "sent",
            "drafts",
            "archive",
            "starred",
            all_mail_folder_id(include_spam = false, include_trash = false),
        )
        for (folder in unscoped) {
            assertNull(folder, search_scope_query(folder))
            assertEquals(folder, all_mail, search_in(folder))
        }
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
    fun several_in_operators_are_any_of() {
        assertEquals(listOf("trashed", "sent"), search("in:trash in:sent report"))
        assertEquals(listOf("spam", "draft"), search("in:spam in:drafts report"))
        assertEquals(
            listOf("inbox", "indexed", "archived", "starred", "trashed", "sent", "draft"),
            search("in:trash in:all report"),
        )
    }

    @Test
    fun everywhere_includes_spam_and_trash_and_all_excludes_them() {
        assertEquals(corpus.map { it.id }, search("in:anywhere report"))
        assertEquals(all_mail, search("in:all report"))
    }

    @Test
    fun negated_in_is_kept_alongside_the_scope() {
        assertEquals(listOf("inbox", "indexed", "archived"), search("in:all -in:starred -in:sent -in:drafts"))
    }

    @Test
    fun narrow_queries_cover_the_system_folders() {
        assertEquals("in:inbox", search_narrow_query("inbox"))
        assertEquals("in:sent", search_narrow_query("sent"))
        assertEquals("in:drafts", search_narrow_query("drafts"))
        assertEquals("in:archive", search_narrow_query("archive"))
        assertEquals("is:starred", search_narrow_query("starred"))
        assertEquals("in:trash", search_narrow_query("trash"))
        assertEquals("in:spam", search_narrow_query("spam"))
        assertNull(search_narrow_query(all_mail_folder_id(include_spam = false, include_trash = false)))
    }

    @Test
    fun default_scope_follows_trash_and_spam_only() {
        assertEquals("trash", default_search_scope("trash"))
        assertEquals("spam", default_search_scope("spam"))
        assertEquals("all", default_search_scope("inbox"))
        assertEquals("all", default_search_scope("folder-token"))
        assertEquals("all", default_search_scope(null))
        assertEquals("all", default_search_scope(all_mail_folder_id(include_spam = false, include_trash = false)))
    }

    @Test
    fun query_scope_reads_a_single_positive_in_operator() {
        assertEquals(QueryScope("all", true), query_search_scope(emptyList(), "inbox"))
        assertEquals(QueryScope("trash", true), query_search_scope(opened_from("trash"), "trash"))
        assertEquals(QueryScope("all", false), query_search_scope(parse_query("in:all").operators, "trash"))
        assertEquals(QueryScope("sent", false), query_search_scope(parse_query("in:sent").operators, "inbox"))
        assertEquals(QueryScope("drafts", false), query_search_scope(parse_query("in:draft").operators, null))
        assertEquals(QueryScope("archive", false), query_search_scope(parse_query("in:archived").operators, null))
        assertNull(query_search_scope(parse_query("in:trash in:sent").operators, "trash"))
        assertNull(query_search_scope(parse_query("-in:spam").operators, null))
    }

    @Test
    fun setting_a_scope_writes_one_in_operator_only_when_needed() {
        assertEquals(emptyList<SearchOperator>(), set_search_scope(opened_from("trash"), "inbox", "all", true))
        assertEquals(listOf(SearchOperator(false, "in", "all")), set_search_scope(emptyList(), "inbox", "all", false))
        assertEquals(listOf(SearchOperator(false, "in", "all")), set_search_scope(emptyList(), "trash", "all", true))
        assertEquals(
            listOf(SearchOperator(false, "from", "ann"), SearchOperator(false, "in", "sent")),
            set_search_scope(parse_query("in:inbox in:trash -in:spam from:ann").operators, null, "sent", false),
        )
        assertEquals(corpus.map { it.id }, search_ops(set_search_scope(opened_from("trash"), "trash", "anywhere", false)))
        assertEquals(listOf("sent"), search_ops(set_search_scope(emptyList(), "sent", "sent", false)))
        assertEquals(listOf("trashed"), search_ops(set_search_scope(emptyList(), "inbox", "trash", true)))
    }

    @Test
    fun clearing_filters_returns_to_the_default_scope() {
        assertEquals(emptyList<SearchOperator>(), clear_search_filters("inbox", true))
        assertEquals(listOf(SearchOperator(false, "in", "all")), clear_search_filters("inbox", false))
        assertEquals(listOf(SearchOperator(false, "in", "trash")), clear_search_filters("trash", true))
        assertEquals(listOf(SearchOperator(false, "in", "spam")), clear_search_filters("spam", false))
        assertEquals(listOf("trashed"), search_ops(clear_search_filters("trash", true)))
    }

    @Test
    fun active_filters_exclude_the_default_scope() {
        assertFalse(has_active_filters(emptyList(), "inbox"))
        assertFalse(has_active_filters(opened_from("trash"), "trash"))
        assertFalse(has_active_filters(parse_query("in:all").operators, "inbox"))
        assertTrue(has_active_filters(parse_query("in:all").operators, "trash"))
        assertTrue(has_active_filters(parse_query("in:anywhere").operators, "trash"))
        assertTrue(has_active_filters(parse_query("is:unread").operators, "inbox"))
        assertTrue(has_active_filters(parse_query("-in:spam").operators, "inbox"))
        assertTrue(scope_chip_active(parse_query("in:trash in:sent").operators, "trash"))
    }

    @Test
    fun sent_and_drafts_are_loaded_into_the_search_corpus() {
        assertEquals(listOf("sent", "drafts"), search_corpus_folders)
        assertEquals(listOf("sent", "draft"), search_corpus_folders.map { search_folder_item_type(it) })
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
