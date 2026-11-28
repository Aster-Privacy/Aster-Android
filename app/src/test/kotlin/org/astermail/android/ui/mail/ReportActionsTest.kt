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
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.folder_offers_report_actions
import org.astermail.android.mail.gather_selection_ids
import org.astermail.android.mail.is_reportable
import org.astermail.android.mail.is_reportable_item_type
import org.astermail.android.mail.report_selection_of
import org.astermail.android.mail.selection_action_needs_ids
import org.astermail.android.mail.selection_offers_report_actions
import org.astermail.android.mail.unmark_spam_selection_of
import org.astermail.android.ui.search.search_report_mode
import org.astermail.android.ui.search.search_report_mode_of
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportActionsTest {

    private fun item(id: String = "1", item_type: String? = null, sender: String = "a@b.c") = InboxItem(
        id = id,
        thread_token = null,
        thread_message_count = 1,
        sender_name = "a",
        sender_email = sender,
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
        raw_item = MailItem(id = id, item_type = item_type),
    )

    private fun message(id: String, item_type: String, sender: String) = ThreadMessage(
        id = id,
        sender_name = "x",
        sender_email = sender,
        to_label = "me",
        timestamp = 0L,
        body = "",
        item_type = item_type,
    )

    @Test
    fun received_folders_offer_report_actions() {
        listOf("inbox", "archive", "spam", "trash", "starred", "all", "custom-folder").forEach {
            assertTrue(it, folder_offers_report_actions(it))
        }
        listOf("sent", "drafts", "scheduled", "outbox").forEach {
            assertFalse(it, folder_offers_report_actions(it))
        }
    }

    @Test
    fun missing_item_type_counts_as_received() {
        assertTrue(is_reportable_item_type(null))
        assertTrue(is_reportable_item_type("received"))
        assertFalse(is_reportable_item_type("sent"))
        assertFalse(is_reportable_item_type("draft"))
        assertTrue(item().is_reportable)
        assertFalse(item(item_type = "sent").is_reportable)
    }

    @Test
    fun selection_needs_one_received_item() {
        assertTrue(selection_offers_report_actions("inbox", listOf(item(item_type = "sent"), item())))
        assertFalse(selection_offers_report_actions("inbox", listOf(item(item_type = "sent"))))
        assertFalse(selection_offers_report_actions("sent", listOf(item())))
    }

    @Test
    fun thread_target_uses_last_received_sender() {
        val messages = listOf(
            message("m1", "received", "them@x.y"),
            message("m2", "sent", "me@aster.cx"),
        )
        val target = thread_report_target(messages, "m2", null, "inbox")
        assertEquals("them@x.y", target?.sender_email)
    }

    @Test
    fun own_threads_offer_no_report_target() {
        val messages = listOf(message("m1", "sent", "me@aster.cx"))
        assertNull(thread_report_target(messages, "m1", null, "inbox"))
        val mixed = listOf(message("m1", "received", "them@x.y"), message("m2", "sent", "me@aster.cx"))
        assertNull(thread_report_target(mixed, "m2", null, "sent"))
    }

    @Test
    fun loading_thread_falls_back_to_list_item() {
        assertEquals("a@b.c", thread_report_target(emptyList(), "1", item(), "inbox")?.sender_email)
        assertNull(thread_report_target(emptyList(), "1", item(item_type = "sent"), "inbox"))
        assertNotNull(thread_report_target(emptyList(), "1", null, "inbox"))
        assertNull(thread_report_target(emptyList(), "1", null, "drafts"))
    }

    @Test
    fun message_target_needs_a_received_message() {
        assertNotNull(message_report_target(message("m1", "received", "them@x.y")))
        assertNull(message_report_target(message("m1", "sent", "me@aster.cx")))
        assertNull(message_report_target(null))
    }

    @Test
    fun spam_swipe_turns_off_outside_received_folders() {
        assertEquals("spam", folder_swipe_action("inbox", "spam"))
        assertEquals("none", folder_swipe_action("sent", "spam"))
        assertEquals("archive", folder_swipe_action("sent", "archive"))
    }

    @Test
    fun search_mode_matches_selection() {
        assertEquals(search_report_mode.report, search_report_mode_of(any_reportable = true, all_spam = false))
        assertEquals(search_report_mode.not_spam, search_report_mode_of(any_reportable = true, all_spam = true))
        assertEquals(search_report_mode.hidden, search_report_mode_of(any_reportable = false, all_spam = false))
        assertFalse(search_report_mode.hidden.toolbar_allows("spam"))
        assertFalse(search_report_mode.hidden.toolbar_allows("phishing"))
        assertFalse(search_report_mode.not_spam.toolbar_allows("spam"))
        assertTrue(search_report_mode.not_spam.toolbar_allows("phishing"))
    }

    @Test
    fun mixed_all_mail_selection_reports_only_received_messages() {
        val known = listOf(
            item("r1", "received", "one@x.org"),
            item("s1", "sent", "me@aster.cx"),
            item("r2", null, "two@x.org"),
            item("d1", "draft", "me@aster.cx"),
        ).associateBy { it.id }
        val picked = report_selection_of(listOf("r1", "s1", "r2", "d1"), known)
        assertEquals(listOf("r1", "r2"), picked.ids)
        assertEquals(listOf("one@x.org", "two@x.org"), picked.sender_emails)
        assertTrue(selection_offers_report_actions("all", known.values))
    }

    @Test
    fun all_sent_selection_reports_nothing() {
        val known = listOf(item("s1", "sent"), item("s2", "scheduled")).associateBy { it.id }
        assertTrue(report_selection_of(listOf("s1", "s2"), known).ids.isEmpty())
    }

    @Test
    fun select_all_report_keeps_unloaded_ids_and_known_senders() {
        val known = listOf(item("r1", "received", "one@x.org"), item("r2", "received", "one@x.org"))
            .associateBy { it.id }
        val picked = report_selection_of(listOf("r1", "r2", "scope-3", "r1"), known)
        assertEquals(listOf("r1", "r2", "scope-3"), picked.ids)
        assertEquals(listOf("one@x.org"), picked.sender_emails)
    }

    @Test
    fun select_all_report_actions_skip_server_scope() {
        assertTrue(selection_action_needs_ids("spam"))
        assertTrue(selection_action_needs_ids("phishing"))
        listOf("not_spam", "archive", "trash", "read").forEach { assertFalse(it, selection_action_needs_ids(it)) }
    }

    @Test
    fun unmark_spam_selection_collects_scope_ids() {
        val collected = mutableListOf<List<String>>()
        var requested = 0
        gather_selection_ids(
            loaded = listOf("a", "b"),
            scope_selection = true,
            collect_scope = { deliver ->
                requested++
                deliver(listOf("b", "c", "d"))
            },
            on_ready = { collected += it },
        )
        assertEquals(1, requested)
        assertEquals(listOf(listOf("a", "b", "c", "d")), collected)
        val known = listOf(item("a", "received", "one@x.org"), item("c", "received", "two@x.org")).associateBy { it.id }
        val picked = unmark_spam_selection_of(collected.single(), known)
        assertEquals(listOf("a", "b", "c", "d"), picked.ids)
        assertEquals(listOf("one@x.org", "two@x.org"), picked.sender_emails)
    }

    @Test
    fun loaded_selection_skips_scope_collection() {
        var requested = 0
        var result: List<String>? = null
        gather_selection_ids(listOf("a"), false, { requested++ }, { result = it })
        assertEquals(0, requested)
        assertEquals(listOf("a"), result)
    }
}
