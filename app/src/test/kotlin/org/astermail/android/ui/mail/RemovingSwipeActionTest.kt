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

import org.astermail.android.mail.SWIPE_ACTION_DELETE
import org.astermail.android.mail.normalize_swipe_action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovingSwipeActionTest {

    @Test
    fun normalized_delete_is_treated_as_removing() {
        assertEquals(SWIPE_ACTION_DELETE, normalize_swipe_action("trash"))
        assertTrue(is_removing_swipe_action(SWIPE_ACTION_DELETE))
    }

    @Test
    fun every_folder_swipe_action_that_moves_a_message_is_removing() {
        listOf(
            "delete",
            "delete_permanent",
            "archive",
            "unarchive",
            "restore_trash",
            "unmark_spam",
            "spam",
        ).forEach { action ->
            assertTrue(action, is_removing_swipe_action(action))
        }
    }

    @Test
    fun actions_that_keep_the_row_in_place_are_not_removing() {
        listOf("toggle_read", "star", "none").forEach { action ->
            assertFalse(action, is_removing_swipe_action(action))
        }
    }
}
