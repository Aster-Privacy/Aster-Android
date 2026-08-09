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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelChipAlignmentTest {

    private fun email(
        id: String,
        thread_id: String,
        names: List<String>,
        icons: List<String>,
        colors: List<Color>,
        received_at: Long = 0L,
    ) = Email(
        id = id,
        thread_id = thread_id,
        sender_name = "Someone",
        sender_email = "someone@example.com",
        subject = "subject",
        preview = "preview",
        received_at = received_at,
        is_read = true,
        is_starred = false,
        has_attachment = false,
        is_encrypted = false,
        trackers_blocked = 0,
        label_colors = colors,
        label_names = names,
        label_icons = icons,
    )

    @Test
    fun a_thread_keeps_each_label_name_next_to_its_own_icon() {
        val rows = group_by_thread(
            listOf(
                email(
                    id = "a",
                    thread_id = "t1",
                    names = listOf("Home", "Work"),
                    icons = listOf("globe", "building"),
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF3B82F6)),
                ),
            ),
        )

        val row = rows.single()
        assertEquals(listOf("Home", "Work"), row.label_names)
        assertEquals(listOf("globe", "building"), row.label_icons)
        assertEquals(2, row.label_colors.size)
    }

    @Test
    fun labels_sharing_a_color_are_not_collapsed_into_one_chip() {
        val shared = Color(0xFF3B82F6)
        val rows = group_by_thread(
            listOf(
                email(
                    id = "a",
                    thread_id = "t1",
                    names = listOf("Home", "Work"),
                    icons = listOf("globe", "building"),
                    colors = listOf(shared, shared),
                ),
            ),
        )

        val row = rows.single()
        assertEquals(row.label_names.size, row.label_colors.size)
        assertEquals(row.label_names.size, row.label_icons.size)
        assertEquals("globe", row.label_icons[row.label_names.indexOf("Home")])
        assertEquals("building", row.label_icons[row.label_names.indexOf("Work")])
    }

    @Test
    fun labels_sharing_an_icon_across_messages_stay_aligned() {
        val rows = group_by_thread(
            listOf(
                email(
                    id = "a",
                    thread_id = "t1",
                    names = listOf("Home"),
                    icons = listOf("tag"),
                    colors = listOf(Color(0xFF3B82F6)),
                    received_at = 1L,
                ),
                email(
                    id = "b",
                    thread_id = "t1",
                    names = listOf("Work"),
                    icons = listOf("tag"),
                    colors = listOf(Color(0xFF10B981)),
                    received_at = 2L,
                ),
            ),
        )

        val row = rows.single()
        assertEquals(listOf("Home", "Work"), row.label_names)
        assertEquals(listOf("tag", "tag"), row.label_icons)
        assertEquals(listOf(Color(0xFF3B82F6), Color(0xFF10B981)), row.label_colors)
    }

    @Test
    fun the_same_label_on_two_messages_appears_once() {
        val rows = group_by_thread(
            listOf(
                email(
                    id = "a",
                    thread_id = "t1",
                    names = listOf("Home"),
                    icons = listOf("globe"),
                    colors = listOf(Color(0xFF3B82F6)),
                    received_at = 1L,
                ),
                email(
                    id = "b",
                    thread_id = "t1",
                    names = listOf("Home"),
                    icons = listOf("globe"),
                    colors = listOf(Color(0xFF3B82F6)),
                    received_at = 2L,
                ),
            ),
        )

        assertEquals(listOf("Home"), rows.single().label_names)
    }
}
