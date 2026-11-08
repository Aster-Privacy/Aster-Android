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

class TrashStateTest {

    @Test
    fun server_flag_wins_over_metadata() {
        assertTrue(is_message_trashed(server_flag = true, metadata_flag = false))
        assertFalse(is_message_trashed(server_flag = false, metadata_flag = true))
    }

    @Test
    fun metadata_is_the_fallback_when_server_is_silent() {
        assertTrue(is_message_trashed(server_flag = null, metadata_flag = true))
        assertFalse(is_message_trashed(server_flag = null, metadata_flag = false))
        assertFalse(is_message_trashed(server_flag = null, metadata_flag = null))
    }

    @Test
    fun trashed_message_gets_delete_forever_never_move_to_trash() {
        assertEquals(DestructiveAction.delete_forever, destructive_action_for(is_trashed = true, is_draft = false))
        assertEquals(DestructiveAction.delete_forever, destructive_action_for(is_trashed = true, is_draft = true))
    }

    @Test
    fun untrashed_message_keeps_move_to_trash_or_discard() {
        assertEquals(DestructiveAction.move_to_trash, destructive_action_for(is_trashed = false, is_draft = false))
        assertEquals(DestructiveAction.discard_draft, destructive_action_for(is_trashed = false, is_draft = true))
    }

    @Test
    fun conversation_delete_includes_trashed_siblings_only() {
        val flags = listOf("a" to true, "b" to false, "c" to true)
        assertEquals(listOf("c", "a"), permanent_delete_targets("c", message_scope = false, thread_trash_flags = flags))
    }

    @Test
    fun conversation_delete_keeps_target_when_flags_are_missing() {
        assertEquals(listOf("x"), permanent_delete_targets("x", message_scope = false, thread_trash_flags = emptyList()))
    }

    @Test
    fun message_scope_deletes_only_that_message() {
        val flags = listOf("a" to true, "b" to true)
        assertEquals(listOf("b"), permanent_delete_targets("b", message_scope = true, thread_trash_flags = flags))
    }

    @Test
    fun list_delete_on_trashed_rows_becomes_delete_forever() {
        assertEquals("delete_permanent", list_destructive_action("delete", all_trashed = true))
        assertEquals("delete_permanent", list_destructive_action("trash", all_trashed = true))
        assertEquals("archive", list_destructive_action("archive", all_trashed = true))
    }

    @Test
    fun list_delete_on_live_rows_stays_trash() {
        assertEquals("delete", list_destructive_action("delete", all_trashed = false))
        assertEquals("trash", list_destructive_action("trash", all_trashed = false))
    }

    @Test
    fun mixed_selection_trashes_only_live_items() {
        assertEquals(listOf("b"), untrashed_ids(listOf("a", "b", "c"), setOf("a", "c")))
    }
}
