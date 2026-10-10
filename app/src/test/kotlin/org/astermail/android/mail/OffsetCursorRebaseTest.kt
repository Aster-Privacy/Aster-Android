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


package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetCursorRebaseTest {

    @Test
    fun `offset paged folders are recognized`() {
        assertTrue(folder_uses_offset_cursor("label:work"))
        assertTrue(folder_uses_offset_cursor("tag:abc"))
        assertTrue(folder_uses_offset_cursor("routing:hash|received"))
        assertTrue(folder_uses_offset_cursor("scheduled"))
    }

    @Test
    fun `cursor paged folders keep their server cursor`() {
        listOf("inbox", "sent", "drafts", "starred", "trash", "spam", "archive", "snoozed").forEach {
            assertFalse(it, folder_uses_offset_cursor(it))
        }
    }

    @Test
    fun `load more pulls the offset back after rows left the view`() {
        assertEquals("260", rebase_offset_cursor("300", 260))
        assertEquals("0", rebase_offset_cursor("50", 0))
    }

    @Test
    fun `load more never moves past the recorded offset`() {
        assertEquals("100", rebase_offset_cursor("100", 140))
    }

    @Test
    fun `opaque cursors pass through untouched`() {
        assertEquals("eyJpZCI6MX0", rebase_offset_cursor("eyJpZCI6MX0", 10))
    }

    @Test
    fun `alias mark all keeps live loaded ids and adds unloaded ones`() {
        val merged = merge_scope_read_ids(
            live = listOf("a", "b"),
            loaded = setOf("a", "b", "c"),
            collected = listOf("a", "c", "d", "e", "d"),
        )

        assertEquals(listOf("a", "b", "d", "e"), merged)
    }
}
