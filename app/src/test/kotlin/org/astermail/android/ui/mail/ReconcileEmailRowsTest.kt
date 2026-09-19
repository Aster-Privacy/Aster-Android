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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileEmailRowsTest {

    private fun email(id: String, subject: String = "Subject $id") = Email(
        id = id,
        sender_name = "Sender $id",
        sender_email = "sender$id@astermail.org",
        subject = subject,
        preview = "Preview $id",
        received_at = 1_700_000_000_000,
        is_read = false,
        is_starred = false,
        has_attachment = false,
    )

    @Test
    fun removing_a_middle_row_keeps_every_other_row_instance() {
        val rows = mutableListOf(email("a"), email("b"), email("c"), email("d"))
        val kept = listOf(rows[0], rows[1], rows[3])

        reconcile_email_rows(rows, listOf(email("a"), email("b"), email("d")))

        assertEquals(listOf("a", "b", "d"), rows.map { it.id })
        kept.forEachIndexed { index, item -> assertSame(item, rows[index]) }
    }

    @Test
    fun a_changed_row_is_replaced_in_place() {
        val rows = mutableListOf(email("a"), email("b"))
        val first = rows[0]

        reconcile_email_rows(rows, listOf(email("a"), email("b", subject = "Renamed")))

        assertSame(first, rows[0])
        assertEquals("Renamed", rows[1].subject)
    }

    @Test
    fun a_new_row_arrives_at_the_top_without_touching_the_rest() {
        val rows = mutableListOf(email("b"), email("c"))
        val existing = rows.toList()

        reconcile_email_rows(rows, listOf(email("a"), email("b"), email("c")))

        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
        assertSame(existing[0], rows[1])
        assertSame(existing[1], rows[2])
    }

    @Test
    fun a_reordered_row_moves_without_duplicating() {
        val rows = mutableListOf(email("a"), email("b"), email("c"))

        reconcile_email_rows(rows, listOf(email("c"), email("a"), email("b")))

        assertEquals(listOf("c", "a", "b"), rows.map { it.id })
        assertTrue(rows.map { it.id }.distinct().size == rows.size)
    }

    @Test
    fun an_unchanged_list_is_left_alone() {
        val rows = mutableListOf(email("a"), email("b"))
        val existing = rows.toList()

        reconcile_email_rows(rows, listOf(email("a"), email("b")))

        existing.forEachIndexed { index, item -> assertSame(item, rows[index]) }
    }
}
