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

import compose.icons.TablerIcons
import compose.icons.tablericons.Archive
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MailOpened
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadToggleSwipeLabelTest {
    private fun email(id: String, thread_id: String = id, is_read: Boolean) = Email(
        id = id,
        sender_name = "Sender $id",
        sender_email = "$id@example.com",
        subject = "Subject $id",
        preview = "Preview $id",
        received_at = 0L,
        is_read = is_read,
        is_starred = false,
        has_attachment = false,
        thread_id = thread_id,
    )

    @Test
    fun unread_row_offers_read() {
        assertEquals("Read", read_toggle_swipe_label("toggle_read", "Read", marks_read = true, unread_label = "Unread"))
        assertEquals(TablerIcons.MailOpened, read_toggle_swipe_icon("toggle_read", TablerIcons.MailOpened, marks_read = true))
    }

    @Test
    fun read_row_offers_unread() {
        assertEquals("Unread", read_toggle_swipe_label("toggle_read", "Read", marks_read = false, unread_label = "Unread"))
        assertEquals(TablerIcons.Mail, read_toggle_swipe_icon("toggle_read", TablerIcons.MailOpened, marks_read = false))
    }

    @Test
    fun other_actions_keep_their_label() {
        assertEquals("Archive", read_toggle_swipe_label("archive", "Archive", marks_read = false, unread_label = "Unread"))
        assertEquals(TablerIcons.Archive, read_toggle_swipe_icon("archive", TablerIcons.Archive, marks_read = false))
    }

    @Test
    fun label_follows_the_row_after_each_toggle() {
        val emails = mutableListOf(email("a", is_read = false))
        assertEquals("Read", label_for(emails, "a", grouping_enabled = true))
        emails[0] = emails[0].copy(is_read = true)
        assertEquals("Unread", label_for(emails, "a", grouping_enabled = true))
        emails[0] = emails[0].copy(is_read = false)
        assertEquals("Read", label_for(emails, "a", grouping_enabled = true))
    }

    @Test
    fun thread_with_any_unread_message_offers_read() {
        val emails = listOf(
            email("m1", thread_id = "t", is_read = true),
            email("m2", thread_id = "t", is_read = false),
            email("other", is_read = false),
        )
        assertTrue(read_toggle_marks_read(emails, "t", grouping_enabled = true))
        assertTrue("t" in read_toggle_row_keys(emails, grouping_enabled = true))
        assertFalse(read_toggle_marks_read(emails, "m1", grouping_enabled = false))
        assertFalse("m1" in read_toggle_row_keys(emails, grouping_enabled = false))
        assertTrue("m2" in read_toggle_row_keys(emails, grouping_enabled = false))
    }

    @Test
    fun label_and_action_agree_for_every_row() {
        val emails = listOf(
            email("m1", thread_id = "t1", is_read = true),
            email("m2", thread_id = "t1", is_read = true),
            email("m3", thread_id = "t2", is_read = false),
            email("m4", thread_id = "t2", is_read = true),
            email("m5", is_read = false),
            email("m6", is_read = true),
        )
        for (grouping in listOf(true, false)) {
            val keys = read_toggle_row_keys(emails, grouping)
            val row_ids = if (grouping) emails.map { it.thread_id }.distinct() else emails.map { it.id }
            for (row in row_ids) {
                assertEquals("$row grouping=$grouping", read_toggle_marks_read(emails, row, grouping), row in keys)
            }
        }
    }

    private fun label_for(emails: List<Email>, row: String, grouping_enabled: Boolean): String =
        read_toggle_swipe_label(
            "toggle_read",
            "Read",
            marks_read = row in read_toggle_row_keys(emails, grouping_enabled),
            unread_label = "Unread",
        )
}
