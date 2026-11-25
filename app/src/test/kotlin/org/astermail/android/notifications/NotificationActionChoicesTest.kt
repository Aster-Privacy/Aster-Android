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

package org.astermail.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionChoicesTest {

    @Test
    fun default_matches_the_actions_shown_before_the_setting() {
        assertEquals(listOf("archive", "trash", "mark_read"), NotificationActionChoices.DEFAULT)
        assertEquals(NotificationActionChoices.DEFAULT, NotificationActionChoices.sanitize(null))
    }

    @Test
    fun sanitize_drops_unknown_and_duplicate_entries_and_caps_at_three() {
        assertEquals(
            listOf("archive", "spam"),
            NotificationActionChoices.sanitize(listOf("spam", "bogus", "archive", "spam")),
        )
        assertEquals(
            listOf("archive", "trash", "mark_read"),
            NotificationActionChoices.sanitize(listOf("spam", "mark_read", "trash", "archive")),
        )
    }

    @Test
    fun sanitize_keeps_an_empty_choice() {
        assertEquals(emptyList<String>(), NotificationActionChoices.sanitize(emptyList()))
    }

    @Test
    fun toggle_adds_in_canonical_order_and_removes() {
        assertEquals(
            listOf("archive", "spam"),
            NotificationActionChoices.toggle(listOf("spam"), "archive"),
        )
        assertEquals(
            listOf("archive", "mark_read"),
            NotificationActionChoices.toggle(listOf("archive", "trash", "mark_read"), "trash"),
        )
    }

    @Test
    fun toggle_refuses_a_fourth_action() {
        val full = listOf("archive", "trash", "mark_read")
        assertEquals(full, NotificationActionChoices.toggle(full, "spam"))
        assertFalse(NotificationActionChoices.can_select(full, "spam"))
        assertTrue(NotificationActionChoices.can_select(full, "trash"))
        assertTrue(NotificationActionChoices.can_select(listOf("archive"), "spam"))
    }

    @Test
    fun a_sign_in_code_takes_one_slot() {
        val chosen = listOf("trash", "mark_read", "spam")
        assertEquals(chosen, NotificationActionChoices.visible(chosen, has_code = false))
        assertEquals(listOf("trash", "mark_read"), NotificationActionChoices.visible(chosen, has_code = true))
        assertEquals(listOf("spam"), NotificationActionChoices.visible(listOf("spam"), has_code = true))
    }

    @Test
    fun request_offsets_are_unique_and_avoid_reserved_slots() {
        val offsets = NotificationActionChoices.ALL.map { NotificationActionChoices.request_offset(it) }
        assertEquals(offsets.size, offsets.toSet().size)
        assertTrue(offsets.none { it == 3 || it == 7 })
        assertTrue(offsets.all { it in 0 until 8 })
    }
}
