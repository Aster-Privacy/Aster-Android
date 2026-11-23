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

import org.astermail.android.ui.mail.is_removing_swipe_action
import org.astermail.android.ui.mail.swipe_action_removes_row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeMoveToFolderTest {

    @Test
    fun move_to_folder_is_a_known_swipe_action() {
        assertTrue(SWIPE_ACTION_IDS.contains(SWIPE_ACTION_MOVE_TO_FOLDER))
        assertEquals(
            SWIPE_ACTION_MOVE_TO_FOLDER,
            normalize_swipe_action("move_to_folder", DEFAULT_SWIPE_LEFT_ACTION),
        )
    }

    @Test
    fun legacy_move_id_maps_to_move_to_folder() {
        assertEquals(
            SWIPE_ACTION_MOVE_TO_FOLDER,
            normalize_swipe_action("move", DEFAULT_SWIPE_LEFT_ACTION),
        )
    }

    @Test
    fun unknown_synced_action_falls_back_to_the_default() {
        assertEquals(
            DEFAULT_SWIPE_LEFT_ACTION,
            normalize_swipe_action("future_action", DEFAULT_SWIPE_LEFT_ACTION),
        )
        assertEquals(
            DEFAULT_SWIPE_RIGHT_ACTION,
            normalize_swipe_action("future_action", DEFAULT_SWIPE_RIGHT_ACTION),
        )
    }

    @Test
    fun move_to_folder_keeps_the_row_until_a_folder_is_picked() {
        assertFalse(is_removing_swipe_action(SWIPE_ACTION_MOVE_TO_FOLDER))
        assertFalse(swipe_action_removes_row(SWIPE_ACTION_MOVE_TO_FOLDER, "inbox"))
    }

    @Test
    fun undo_strips_the_folder_only_from_items_that_did_not_have_it() {
        val strip = folder_move_undo_strip_ids(
            ids = listOf("a", "b", "c"),
            folder_token = "work",
            labels_before = mapOf(
                "a" to emptyList(),
                "b" to listOf("work"),
                "c" to listOf("personal"),
            ),
        )
        assertEquals(listOf("a", "c"), strip)
    }

    @Test
    fun undo_strips_items_with_no_known_labels() {
        assertEquals(
            listOf("x"),
            folder_move_undo_strip_ids(listOf("x"), "work", emptyMap()),
        )
    }
}
