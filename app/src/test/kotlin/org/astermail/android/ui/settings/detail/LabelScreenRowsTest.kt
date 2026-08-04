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

package org.astermail.android.ui.settings.detail

import androidx.compose.ui.graphics.Color
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.tags.TagItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelScreenRowsTest {

    private fun tag(
        id: String,
        name: String = id,
        color: String? = null,
        sort_order: Int = 0,
        item_count: Long? = null,
    ) = TagItem(
        id = id,
        tag_token = "token_$id",
        encrypted_name = name,
        name_nonce = "",
        encrypted_color = color,
        sort_order = sort_order,
        item_count = item_count,
    )

    private fun label(
        id: String,
        name: String? = id,
        color: String? = null,
        sort_order: Int = 0,
        is_system: Boolean = false,
        is_locked: Boolean = false,
    ) = LabelItem(
        id = id,
        label_token = "token_$id",
        encrypted_name = name,
        encrypted_color = color,
        is_system = is_system,
        is_locked = is_locked,
        folder_type = "label",
        sort_order = sort_order,
    )

    @Test
    fun `tags come before labels`() {
        val rows = label_screen_rows(
            tags = listOf(tag("t_b", sort_order = 1), tag("t_a", sort_order = 0)),
            labels = listOf(label("l_b", sort_order = 1), label("l_a", sort_order = 0)),
        )
        assertEquals(listOf("t_a", "t_b", "l_a", "l_b"), rows.map { it.id })
        assertEquals(listOf(true, true, false, false), rows.map { it.is_tag })
    }

    @Test
    fun `move edges are computed per kind`() {
        val rows = label_screen_rows(
            tags = listOf(tag("t_a", sort_order = 0), tag("t_b", sort_order = 1)),
            labels = listOf(label("l_a", sort_order = 0), label("l_b", sort_order = 1)),
        )
        val by_id = rows.associateBy { it.id }
        assertFalse(by_id.getValue("t_a").can_move_up)
        assertTrue(by_id.getValue("t_a").can_move_down)
        assertTrue(by_id.getValue("t_b").can_move_up)
        assertFalse(by_id.getValue("t_b").can_move_down)
        assertFalse(by_id.getValue("l_a").can_move_up)
        assertTrue(by_id.getValue("l_a").can_move_down)
        assertTrue(by_id.getValue("l_b").can_move_up)
        assertFalse(by_id.getValue("l_b").can_move_down)
    }

    @Test
    fun `a single row of each kind has both arrows disabled`() {
        val rows = label_screen_rows(listOf(tag("t")), listOf(label("l")))
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.can_move_up || it.can_move_down })
    }

    @Test
    fun `tags are always deletable and system or locked labels are not`() {
        val rows = label_screen_rows(
            tags = listOf(tag("t")),
            labels = listOf(
                label("plain", sort_order = 0),
                label("system", sort_order = 1, is_system = true),
                label("locked", sort_order = 2, is_locked = true),
            ),
        )
        val by_id = rows.associateBy { it.id }
        assertTrue(by_id.getValue("t").can_delete)
        assertTrue(by_id.getValue("plain").can_delete)
        assertFalse(by_id.getValue("system").can_delete)
        assertFalse(by_id.getValue("locked").can_delete)
    }

    @Test
    fun `colors fall back when missing or malformed`() {
        val fallback = Color(0xFF6B7280)
        val rows = label_screen_rows(
            tags = listOf(
                tag("good", color = "#FF0000", sort_order = 0),
                tag("bad", color = "not-a-color", sort_order = 1),
                tag("none", sort_order = 2),
            ),
            labels = emptyList(),
        )
        val by_id = rows.associateBy { it.id }
        assertEquals(Color(0xFFFF0000), by_id.getValue("good").color)
        assertEquals(fallback, by_id.getValue("bad").color)
        assertEquals(fallback, by_id.getValue("none").color)
    }

    @Test
    fun `counts are carried through untouched`() {
        val rows = label_screen_rows(
            tags = listOf(tag("counted", item_count = 12)),
            labels = listOf(label("uncounted")),
        )
        val by_id = rows.associateBy { it.id }
        assertEquals(12L, by_id.getValue("counted").count)
        assertEquals(null, by_id.getValue("uncounted").count)
    }

    @Test
    fun `unreadable names are dropped by the underlying row helpers`() {
        val rows = label_screen_rows(
            tags = listOf(
                tag("keep", name = "Work", sort_order = 0),
                tag("blank", name = "", sort_order = 1),
                tag("cipher", name = "aGVsbG8gd29ybGQgdGhpcyBpcyBiYXNlNjQgcGF5bG9hZA==", sort_order = 2),
            ),
            labels = listOf(label("null_name", name = null)),
        )
        assertEquals(listOf("keep"), rows.map { it.id })
        assertEquals("Work", rows.single().name)
    }

    @Test
    fun `an empty model yields no rows`() {
        assertTrue(label_screen_rows(emptyList(), emptyList()).isEmpty())
    }
}
