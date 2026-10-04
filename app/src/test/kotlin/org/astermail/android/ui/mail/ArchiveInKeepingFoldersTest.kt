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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveInKeepingFoldersTest {

    private fun email(id: String, archived: Boolean, folder: String = "sent") = Email(
        id = id,
        sender_name = "Me",
        sender_email = "me@astermail.org",
        subject = "Subject $id",
        preview = "Preview $id",
        received_at = 1_700_000_000_000,
        is_read = true,
        is_starred = false,
        has_attachment = false,
        folder_chip = archived_folder_chip(folder, archived, "Archived"),
    )

    @Test
    fun archiving_keeps_the_row_where_archived_mail_stays_listed() {
        listOf("sent", "starred", "snoozed", "all", "label:work", "tag:urgent").forEach { folder ->
            assertFalse(folder, archive_removes_row(folder))
        }
    }

    @Test
    fun archiving_removes_the_row_from_the_inbox() {
        assertTrue(archive_removes_row("inbox"))
    }

    @Test
    fun archive_swipe_in_sent_does_not_dismiss_the_row() {
        assertFalse(swipe_action_removes_row("archive", "sent"))
        assertFalse(swipe_action_removes_row("archive", "starred"))
        assertTrue(swipe_action_removes_row("archive", "inbox"))
    }

    @Test
    fun other_moving_swipes_in_sent_still_dismiss_the_row() {
        listOf("delete", "spam").forEach { action ->
            assertTrue(action, swipe_action_removes_row(action, "sent"))
        }
        assertFalse(swipe_action_removes_row("star", "sent"))
        assertFalse(swipe_action_removes_row("toggle_read", "sent"))
    }

    @Test
    fun archived_sent_row_shows_an_archived_chip() {
        val chip = archived_folder_chip("sent", is_archived = true, name = "Archived")
        assertNotNull(chip)
        assertEquals("Archived", chip!!.name)
        assertEquals("archive", chip.icon)
    }

    @Test
    fun no_archived_chip_where_it_adds_nothing() {
        assertNull(archived_folder_chip("sent", is_archived = false, name = "Archived"))
        assertNull(archived_folder_chip("inbox", is_archived = true, name = "Archived"))
        assertNull(archived_folder_chip("archive", is_archived = true, name = "Archived"))
    }

    @Test
    fun archive_and_undo_both_reach_the_sent_rows() {
        val rows = mutableListOf(email("a", archived = false), email("b", archived = false))

        val archived = listOf(email("a", archived = true), email("b", archived = false))
        assertNotEquals(rows.toList(), archived)
        reconcile_email_rows(rows, archived)
        assertEquals(listOf("a", "b"), rows.map { it.id })
        assertNotNull(rows[0].folder_chip)

        val undone = listOf(email("a", archived = false), email("b", archived = false))
        assertNotEquals(rows.toList(), undone)
        reconcile_email_rows(rows, undone)
        assertEquals(listOf("a", "b"), rows.map { it.id })
        assertNull(rows[0].folder_chip)
    }
}
