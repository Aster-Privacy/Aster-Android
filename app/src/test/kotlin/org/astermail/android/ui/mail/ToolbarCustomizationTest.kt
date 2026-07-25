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
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarCustomizationTest {

    @Test
    fun reading_defaults_match_legacy_hardcoded_bar() {
        assertEquals(listOf("read", "trash", "folder", "label"), parse_toolbar_actions(null))
        assertEquals(listOf("read", "trash", "folder", "label"), parse_toolbar_actions(""))
    }

    @Test
    fun selection_defaults_put_delete_first_and_read_last() {
        assertEquals(listOf("trash", "folder", "label", "read"), parse_selection_toolbar_actions(null))
        assertEquals(listOf("trash", "folder", "label", "read"), parse_selection_toolbar_actions(""))
    }

    @Test
    fun custom_order_is_preserved() {
        assertEquals(
            listOf("star", "archive", "snooze", "spam"),
            parse_selection_toolbar_actions("star,archive,snooze,spam"),
        )
    }

    @Test
    fun invalid_ids_are_dropped_and_backfilled_from_defaults() {
        val parsed = parse_selection_toolbar_actions("bogus,star,also_bad")
        assertEquals(4, parsed.size)
        assertEquals("star", parsed[0])
        assertTrue(parsed.containsAll(listOf("trash", "folder", "label")))
    }

    @Test
    fun reply_and_forward_are_reading_only() {
        val selection_ids = selection_toolbar_action_catalog.map { it.id }
        assertTrue("reply" !in selection_ids)
        assertTrue("forward" !in selection_ids)
        val reading_ids = toolbar_action_catalog.map { it.id }
        assertTrue("reply" in reading_ids)
        assertTrue("forward" in reading_ids)
    }

    @Test
    fun duplicates_collapse_to_first_occurrence() {
        val parsed = parse_selection_toolbar_actions("trash,trash,star,star")
        assertEquals("trash", parsed[0])
        assertEquals("star", parsed[1])
        assertEquals(4, parsed.distinct().size)
    }

    @Test
    fun selection_reply_ids_fall_back_to_defaults() {
        val parsed = parse_selection_toolbar_actions("reply,forward")
        assertEquals(listOf("trash", "folder", "label", "read"), parsed)
    }

    @Test
    fun unread_is_valid_for_selection_only() {
        assertEquals("unread", parse_selection_toolbar_actions("unread,trash,folder,label")[0])
        assertTrue("unread" !in parse_toolbar_actions("unread,trash,folder,label"))
    }
}
