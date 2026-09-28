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

package org.astermail.android.folders

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.ReorderLabelEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class folder_sort_test {

    private fun folder(
        token: String,
        name: String = token,
        sort_order: Int = 0,
        parent_token: String? = null,
        folder_type: String = "folder",
        is_system: Boolean = false,
        created_at: String? = null,
    ) = LabelItem(
        id = "id_$token",
        label_token = token,
        encrypted_name = name,
        is_system = is_system,
        folder_type = folder_type,
        sort_order = sort_order,
        parent_token = parent_token,
        created_at = created_at,
    )

    private fun entry(token: String, sort_order: Int) = ReorderLabelEntry(id = "id_$token", sort_order = sort_order)

    @Test
    fun compare_is_numeric_aware() {
        assertTrue(compare_folder_names("Folder 2", "Folder 10") < 0)
        assertTrue(compare_folder_names("Folder 10", "Folder 9") > 0)
        assertTrue(compare_folder_names("2024", "999") > 0)
        assertTrue(compare_folder_names("Folder", "Folder 1") < 0)
    }

    @Test
    fun compare_is_case_accent_and_whitespace_insensitive() {
        assertEquals(0, compare_folder_names("apple", "APPLE"))
        assertEquals(0, compare_folder_names("resume", "Résumé"))
        assertEquals(0, compare_folder_names("  Work ", "work"))
        assertTrue(compare_folder_names("banana", "Apple") > 0)
    }

    @Test
    fun equal_names_break_ties_on_created_at_then_token() {
        val older = folder("z", name = "Same", created_at = "2026-01-01")
        val newer = folder("a", name = "same", created_at = "2026-02-01")
        val same_time = folder("b", name = "SAME", created_at = "2026-02-01")
        val sorted = listOf(same_time, newer, older).sortedWith(folder_a_z_comparator)
        assertEquals(listOf("z", "a", "b"), sorted.map { it.label_token })
    }

    @Test
    fun detects_already_sorted_trees() {
        val sorted = listOf(
            folder("a", name = "Alpha", sort_order = 0),
            folder("b", name = "beta", sort_order = 1),
            folder("c", name = "Child 2", sort_order = 0, parent_token = "a"),
            folder("d", name = "Child 10", sort_order = 1, parent_token = "a"),
        )
        assertTrue(is_folder_tree_sorted_a_z(sorted))
        assertFalse(can_sort_folders_a_z(sorted))

        val unsorted_child = sorted.map {
            when (it.label_token) {
                "c" -> it.copy(sort_order = 1)
                "d" -> it.copy(sort_order = 0)
                else -> it
            }
        }
        assertFalse(is_folder_tree_sorted_a_z(unsorted_child))
        assertTrue(can_sort_folders_a_z(unsorted_child))
    }

    @Test
    fun cannot_sort_with_fewer_than_two_custom_folders() {
        val labels = listOf(
            folder("inbox", name = "Zeta", is_system = true, sort_order = 0),
            folder("tag", name = "Aardvark", folder_type = "label", sort_order = 1),
            folder("only", name = "Middle", sort_order = 2),
        )
        assertFalse(can_sort_folders_a_z(labels))
        assertEquals(listOf(entry("only", 0)), sort_folder_tree_a_z(labels))
    }

    @Test
    fun full_tree_sort_emits_only_changed_entries_across_levels() {
        val labels = listOf(
            folder("zeta", name = "Zeta", sort_order = 0),
            folder("alpha", name = "alpha", sort_order = 1),
            folder("mid", name = "Mid", sort_order = 2),
            folder("c10", name = "Child 10", sort_order = 0, parent_token = "alpha"),
            folder("c2", name = "Child 2", sort_order = 1, parent_token = "alpha"),
            folder("g1", name = "Grand A", sort_order = 0, parent_token = "c2"),
            folder("g2", name = "Grand B", sort_order = 1, parent_token = "c2"),
            folder("sys", name = "Aaa", is_system = true, sort_order = 5),
            folder("tag", name = "Aab", folder_type = "label", sort_order = 7),
        )
        val entries = sort_folder_tree_a_z(labels)
        assertEquals(
            setOf(
                entry("alpha", 0),
                entry("mid", 1),
                entry("zeta", 2),
                entry("c2", 0),
                entry("c10", 1),
            ),
            entries.toSet(),
        )
        assertEquals(5, entries.size)
        assertTrue(is_folder_tree_sorted_a_z(apply_folder_orders(labels, entries)))
        assertTrue(sort_folder_tree_a_z(apply_folder_orders(labels, entries)).isEmpty())
    }

    @Test
    fun orphaned_parent_counts_as_root() {
        val labels = listOf(
            folder("b", name = "Beta", sort_order = 0),
            folder("a", name = "Alpha", sort_order = 1, parent_token = "missing"),
        )
        assertEquals(setOf(entry("a", 0), entry("b", 1)), sort_folder_tree_a_z(labels).toSet())
        assertEquals(listOf("b", "a"), folder_children(labels, null).map { it.label_token })
    }

    @Test
    fun legacy_all_zero_orders_get_renumbered() {
        val labels = listOf(
            folder("c", name = "Gamma", created_at = "2026-01-01"),
            folder("a", name = "Alpha", created_at = "2026-01-02"),
            folder("b", name = "Beta", created_at = "2026-01-03"),
        )
        assertFalse(is_folder_tree_sorted_a_z(labels))
        assertEquals(
            setOf(entry("b", 1), entry("c", 2)),
            sort_folder_tree_a_z(labels).toSet(),
        )
    }

    @Test
    fun placement_inserts_alphabetically_when_group_is_sorted() {
        val siblings = listOf(
            folder("a", name = "Alpha", sort_order = 0),
            folder("c", name = "Charlie", sort_order = 1),
            folder("d", name = "Delta", sort_order = 2),
        )
        val entries = place_folder_among_siblings(siblings, folder("b", name = "bravo"))
        assertEquals(listOf(entry("b", 1), entry("c", 2), entry("d", 3)), entries)
    }

    @Test
    fun placement_appends_when_group_is_not_sorted() {
        val siblings = listOf(
            folder("c", name = "Charlie", sort_order = 0),
            folder("a", name = "Alpha", sort_order = 1),
        )
        val entries = place_folder_among_siblings(siblings, folder("b", name = "Bravo"))
        assertEquals(listOf(entry("b", 2)), entries)
        assertEquals(2, append_sort_order(siblings))
        assertEquals(0, append_sort_order(emptyList()))
    }

    @Test
    fun placement_into_empty_group_uses_index_zero() {
        assertEquals(listOf(entry("n", 0)), place_folder_among_siblings(emptyList(), folder("n", sort_order = 4)))
    }

    @Test
    fun placement_on_legacy_zero_orders_renumbers_group() {
        val siblings = listOf(
            folder("a", name = "Alpha", created_at = "2026-01-01"),
            folder("c", name = "Charlie", created_at = "2026-01-02"),
        )
        val entries = place_folder_among_siblings(siblings, folder("b", name = "Bravo"))
        assertEquals(listOf(entry("b", 1), entry("c", 2)), entries)
    }

    @Test
    fun move_includes_the_moved_folder_and_excludes_it_from_siblings() {
        val labels = listOf(
            folder("p", name = "Parent", sort_order = 0),
            folder("a", name = "Alpha", sort_order = 0, parent_token = "p"),
            folder("c", name = "Charlie", sort_order = 1, parent_token = "p"),
            folder("b", name = "Bravo", sort_order = 1),
        )
        val moving = labels.first { it.label_token == "b" }
        val entries = place_folder_among_siblings(folder_children(labels, "p"), moving)
        assertEquals(listOf(entry("b", 1), entry("c", 2)), entries)
    }

    @Test
    fun rename_resorts_only_when_group_was_sorted() {
        val sorted = listOf(
            folder("a", name = "Alpha", sort_order = 0),
            folder("b", name = "Bravo", sort_order = 1),
            folder("c", name = "Charlie", sort_order = 2),
        )
        assertEquals(
            setOf(entry("a", 2), entry("b", 0), entry("c", 1)),
            resort_after_rename(sorted, "id_a", "Zulu").toSet(),
        )

        val unsorted = listOf(
            folder("c", name = "Charlie", sort_order = 0),
            folder("a", name = "Alpha", sort_order = 1),
        )
        assertTrue(resort_after_rename(unsorted, "id_a", "Zulu").isEmpty())

        val single = listOf(folder("a", name = "Alpha"))
        assertTrue(resort_after_rename(single, "id_a", "Zulu").isEmpty())
    }

    @Test
    fun rename_that_keeps_position_emits_nothing() {
        val sorted = listOf(
            folder("a", name = "Alpha", sort_order = 0),
            folder("b", name = "Bravo", sort_order = 1),
        )
        assertTrue(resort_after_rename(sorted, "id_a", "Able").isEmpty())
    }

    @Test
    fun rollback_restores_only_touched_folders() {
        val labels = listOf(
            folder("a", name = "Beta", sort_order = 0),
            folder("b", name = "Alpha", sort_order = 1),
            folder("c", name = "Gamma", sort_order = 2),
        )
        val entries = sort_folder_tree_a_z(labels)
        val previous = previous_folder_orders(labels, entries)
        assertEquals(mapOf("id_a" to 0, "id_b" to 1), previous)

        val applied = apply_folder_orders(labels, entries)
        val concurrent = applied.map {
            if (it.label_token == "c") it.copy(sort_order = 9, encrypted_name = "Renamed") else it
        } + folder("new", name = "New", sort_order = 3)
        val restored = restore_folder_orders(concurrent, previous)

        assertEquals(0, restored.first { it.label_token == "a" }.sort_order)
        assertEquals(1, restored.first { it.label_token == "b" }.sort_order)
        assertEquals(9, restored.first { it.label_token == "c" }.sort_order)
        assertEquals("Renamed", restored.first { it.label_token == "c" }.encrypted_name)
        assertTrue(restored.any { it.label_token == "new" })
    }
}
