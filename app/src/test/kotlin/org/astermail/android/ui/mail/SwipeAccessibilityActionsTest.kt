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

class SwipeAccessibilityActionsTest {

    @Test
    fun both_enabled_sides_are_exposed_with_their_swipe_labels() {
        var started = 0
        var ended = 0
        val actions = swipe_accessibility_actions(
            start_action = "archive",
            end_action = "delete",
            start_label = "Archive",
            end_label = "Delete",
            on_swipe_start = { started++ },
            on_swipe_end = { ended++ },
        )
        assertEquals(listOf("Archive", "Delete"), actions.map { it.label })
        assertTrue(actions[0].action())
        assertEquals(1, started)
        assertEquals(0, ended)
        assertTrue(actions[1].action())
        assertEquals(1, ended)
    }

    @Test
    fun disabled_sides_are_left_out() {
        val actions = swipe_accessibility_actions(
            start_action = "none",
            end_action = "star",
            start_label = "",
            end_label = "Star",
            on_swipe_start = {},
            on_swipe_end = {},
        )
        assertEquals(listOf("Star"), actions.map { it.label })
    }

    @Test
    fun no_actions_when_swipes_are_off() {
        val actions = swipe_accessibility_actions(
            start_action = "none",
            end_action = "none",
            start_label = "",
            end_label = "",
            on_swipe_start = {},
            on_swipe_end = {},
        )
        assertTrue(actions.isEmpty())
    }
}
