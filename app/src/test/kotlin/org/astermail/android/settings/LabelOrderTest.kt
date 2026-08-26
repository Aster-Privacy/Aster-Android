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

package org.astermail.android.settings

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.tags.TagItem
import org.astermail.android.labels.label_reorder_entries
import org.astermail.android.labels.label_rows
import org.astermail.android.labels.move_row
import org.astermail.android.labels.tag_reorder_entries
import org.astermail.android.labels.tag_display_name
import org.astermail.android.labels.tag_rows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelOrderTest {

    private fun label(
        id: String,
        name: String? = id,
        sort_order: Int = 0,
        folder_type: String = "label",
        created_at: String? = null,
    ) = LabelItem(
        id = id,
        label_token = "token_$id",
        encrypted_name = name,
        sort_order = sort_order,
        folder_type = folder_type,
        created_at = created_at,
    )

    private fun tag(
        id: String,
        name: String = id,
        sort_order: Int = 0,
        created_at: String? = null,
    ) = TagItem(
        id = id,
        tag_token = "token_$id",
        encrypted_name = name,
        name_nonce = "",
        sort_order = sort_order,
        created_at = created_at,
    )

    @Test
    fun `label rows are ordered by sort_order regardless of server order`() {
        val rows = label_rows(
            listOf(
                label("c", sort_order = 2),
                label("a", sort_order = 0),
                label("b", sort_order = 1),
            ),
        )
        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
    }

    @Test
    fun `label rows exclude folders and system folder types`() {
        val rows = label_rows(
            listOf(
                label("keep", folder_type = "label"),
                label("folder", folder_type = "folder"),
                label("custom", folder_type = "custom"),
            ),
        )
        assertEquals(listOf("keep"), rows.map { it.id })
    }

    @Test
    fun `label rows exclude blank and undecrypted names`() {
        val rows = label_rows(
            listOf(
                label("readable", name = "Invoice"),
                label("blank", name = ""),
                label("null_name", name = null),
                label("encrypted", name = "aGVsbG8gd29ybGQgdGhpcyBpcyBiYXNlNjQgcGF5bG9hZA=="),
            ),
        )
        assertEquals(listOf("readable"), rows.map { it.id })
    }

    @Test
    fun `equal sort_order falls back to created_at then token`() {
        val rows = label_rows(
            listOf(
                label("second", sort_order = 0, created_at = "2026-02-01T00:00:00Z"),
                label("first", sort_order = 0, created_at = "2026-01-01T00:00:00Z"),
            ),
        )
        assertEquals(listOf("first", "second"), rows.map { it.id })

        val no_dates = label_rows(
            listOf(label("b", sort_order = 0), label("a", sort_order = 0)),
        )
        assertEquals(listOf("a", "b"), no_dates.map { it.id })
    }

    @Test
    fun `tag rows are ordered by sort_order and drop unreadable names`() {
        val rows = tag_rows(
            listOf(
                tag("b", sort_order = 1),
                tag("a", sort_order = 0),
                tag("blank", name = "", sort_order = 2),
                tag("encrypted", name = "aGVsbG8gd29ybGQgdGhpcyBpcyBiYXNlNjQgcGF5bG9hZA==", sort_order = 3),
            ),
        )
        assertEquals(listOf("a", "b"), rows.map { it.id })
    }

    @Test
    fun `tag rows keep an unreadable tag that is applied to the selection`() {
        val tags = listOf(
            tag("a", sort_order = 0),
            tag("encrypted", name = "aGVsbG8gd29ybGQgdGhpcyBpcyBiYXNlNjQgcGF5bG9hZA==", sort_order = 1),
        )
        assertEquals(listOf("a"), tag_rows(tags).map { it.id })
        assertEquals(
            listOf("a", "encrypted"),
            tag_rows(tags, setOf("token_encrypted")).map { it.id },
        )
    }

    @Test
    fun `tag display name falls back for an unreadable tag`() {
        assertEquals("a", tag_display_name(tag("a"), "Unknown"))
        assertEquals(
            "Unknown",
            tag_display_name(
                tag("encrypted", name = "aGVsbG8gd29ybGQgdGhpcyBpcyBiYXNlNjQgcGF5bG9hZA=="),
                "Unknown",
            ),
        )
        assertEquals("Unknown", tag_display_name(tag("blank", name = ""), "Unknown"))
    }

    @Test
    fun `move_row moves an item up and down`() {
        val rows = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), move_row(rows, 1, -1))
        assertEquals(listOf("a", "c", "b"), move_row(rows, 1, 1))
    }

    @Test
    fun `move_row refuses moves past either edge`() {
        val rows = listOf("a", "b", "c")
        assertNull(move_row(rows, 0, -1))
        assertNull(move_row(rows, 2, 1))
        assertNull(move_row(rows, -1, 1))
        assertNull(move_row(rows, 5, -1))
        assertNull(move_row(rows, 1, 0))
        assertNull(move_row(emptyList<String>(), 0, 1))
    }

    @Test
    fun `move_row spanning more than one position is supported`() {
        assertEquals(listOf("b", "c", "a"), move_row(listOf("a", "b", "c"), 0, 2))
    }

    @Test
    fun `label reorder entries only include positions that actually changed`() {
        val reordered = listOf(
            label("b", sort_order = 1),
            label("a", sort_order = 0),
            label("c", sort_order = 2),
        )
        val entries = label_reorder_entries(reordered)
        assertEquals(2, entries.size)
        val by_id = entries.associate { it.id to it.sort_order }
        assertEquals(0, by_id["b"])
        assertEquals(1, by_id["a"])
        assertTrue("c" !in by_id)
    }

    @Test
    fun `reorder entries are empty when nothing moved`() {
        val unchanged = listOf(label("a", sort_order = 0), label("b", sort_order = 1))
        assertTrue(label_reorder_entries(unchanged).isEmpty())
        assertTrue(tag_reorder_entries(listOf(tag("a", sort_order = 0), tag("b", sort_order = 1))).isEmpty())
    }

    @Test
    fun `tag reorder entries renumber from zero`() {
        val entries = tag_reorder_entries(
            listOf(tag("b", sort_order = 7), tag("a", sort_order = 3)),
        )
        val by_id = entries.associate { it.id to it.sort_order }
        assertEquals(0, by_id["b"])
        assertEquals(1, by_id["a"])
    }

    @Test
    fun `duplicate sort_order values still produce a stable renumbering`() {
        val rows = label_rows(
            listOf(
                label("a", sort_order = 0, created_at = "2026-01-01T00:00:00Z"),
                label("b", sort_order = 0, created_at = "2026-01-02T00:00:00Z"),
                label("c", sort_order = 0, created_at = "2026-01-03T00:00:00Z"),
            ),
        )
        val moved = move_row(rows, 2, -1)!!
        assertEquals(listOf("a", "c", "b"), moved.map { it.id })
        val by_id = label_reorder_entries(moved).associate { it.id to it.sort_order }
        assertEquals(1, by_id["c"])
        assertEquals(2, by_id["b"])
    }
}
