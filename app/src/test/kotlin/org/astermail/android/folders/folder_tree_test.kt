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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class folder_tree_test {

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

    @Test
    fun sorts_roots_by_sort_order() {
        val labels = listOf(
            folder("b", sort_order = 2),
            folder("a", sort_order = 0),
            folder("c", sort_order = 1),
        )
        val nodes = flatten_folder_tree(labels)
        assertEquals(listOf("a", "c", "b"), nodes.map { it.label.label_token })
        assertTrue(nodes.all { it.depth == 0 })
    }

    @Test
    fun breaks_sort_order_ties_by_created_at() {
        val labels = listOf(
            folder("newer", sort_order = 0, created_at = "2026-02-01T00:00:00Z"),
            folder("older", sort_order = 0, created_at = "2026-01-01T00:00:00Z"),
        )
        val nodes = flatten_folder_tree(labels)
        assertEquals(listOf("older", "newer"), nodes.map { it.label.label_token })
    }

    @Test
    fun nests_children_under_parents_in_order() {
        val labels = listOf(
            folder("root", sort_order = 0),
            folder("child_b", sort_order = 1, parent_token = "root"),
            folder("child_a", sort_order = 0, parent_token = "root"),
            folder("grandchild", sort_order = 0, parent_token = "child_a"),
            folder("other_root", sort_order = 1),
        )
        val nodes = flatten_folder_tree(labels)
        assertEquals(
            listOf("root", "child_a", "grandchild", "child_b", "other_root"),
            nodes.map { it.label.label_token },
        )
        assertEquals(listOf(0, 1, 2, 1, 0), nodes.map { it.depth })

        val by_token = nodes.associateBy { it.label.label_token }
        assertTrue(by_token.getValue("root").has_next)
        assertTrue(by_token.getValue("child_a").has_next)
        assertEquals(listOf(true), by_token.getValue("child_a").trail)
        assertEquals(listOf(true, true), by_token.getValue("grandchild").trail)
        assertTrue(!by_token.getValue("grandchild").has_next)
        assertTrue(!by_token.getValue("child_b").has_next)
        assertTrue(!by_token.getValue("other_root").has_next)
    }

    @Test
    fun treats_dangling_parent_as_root() {
        val labels = listOf(
            folder("orphan", sort_order = 5, parent_token = "deleted"),
            folder("normal", sort_order = 0),
        )
        val nodes = flatten_folder_tree(labels)
        assertEquals(listOf("normal", "orphan"), nodes.map { it.label.label_token })
        assertTrue(nodes.all { it.depth == 0 })
    }

    @Test
    fun survives_parent_cycles() {
        val labels = listOf(
            folder("x", parent_token = "y"),
            folder("y", parent_token = "x"),
            folder("z"),
        )
        val nodes = flatten_folder_tree(labels)
        assertEquals(3, nodes.size)
        assertEquals(setOf("x", "y", "z"), nodes.map { it.label.label_token }.toSet())
    }

    @Test
    fun ignores_self_referencing_parent() {
        val labels = listOf(folder("selfie", parent_token = "selfie"))
        val nodes = flatten_folder_tree(labels)
        assertEquals(1, nodes.size)
        assertEquals(0, nodes.first().depth)
    }

    @Test
    fun caps_depth_at_max() {
        var parent: String? = null
        val labels = mutableListOf<LabelItem>()
        for (i in 0..6) {
            labels.add(folder("f$i", parent_token = parent))
            parent = "f$i"
        }
        val nodes = flatten_folder_tree(labels)
        assertEquals(7, nodes.size)
        assertTrue(nodes.all { it.depth <= max_folder_depth })
    }

    @Test
    fun excludes_system_and_label_types() {
        val labels = listOf(
            folder("keep"),
            folder("sys", is_system = true),
            folder("tag", folder_type = "label"),
            folder("legacy", folder_type = "custom"),
        )
        val nodes = flatten_folder_tree(labels)
        assertEquals(setOf("keep", "legacy"), nodes.map { it.label.label_token }.toSet())
    }

    @Test
    fun sibling_group_scopes_to_same_parent() {
        val labels = listOf(
            folder("root_a", sort_order = 0),
            folder("root_b", sort_order = 1),
            folder("child_a", sort_order = 1, parent_token = "root_a"),
            folder("child_b", sort_order = 0, parent_token = "root_a"),
        )
        val group = folder_sibling_group(labels, "id_child_a")
        assertEquals(listOf("child_b", "child_a"), group.map { it.label_token })
        val roots = folder_sibling_group(labels, "id_root_b")
        assertEquals(listOf("root_a", "root_b"), roots.map { it.label_token })
    }

    @Test
    fun sibling_group_for_unknown_id_is_empty() {
        assertTrue(folder_sibling_group(listOf(folder("a")), "missing").isEmpty())
    }

    @Test
    fun builds_display_path_root_first() {
        val labels = listOf(
            folder("top", name = "Test 1"),
            folder("mid", name = "Boy", parent_token = "top"),
            folder("leaf", name = "Cat", parent_token = "mid"),
        )
        assertEquals(listOf("Test 1", "Boy", "Cat"), folder_path(labels, "leaf"))
        assertEquals(listOf("Test 1"), folder_path(labels, "top"))
    }

    @Test
    fun path_survives_cycles() {
        val labels = listOf(
            folder("x", parent_token = "y"),
            folder("y", parent_token = "x"),
        )
        assertEquals(2, folder_path(labels, "x").size)
    }

    @Test
    fun collects_descendants_transitively() {
        val labels = listOf(
            folder("root"),
            folder("child", parent_token = "root"),
            folder("grandchild", parent_token = "child"),
            folder("unrelated"),
        )
        assertEquals(setOf("child", "grandchild"), descendant_tokens(labels, "root"))
        assertTrue(descendant_tokens(labels, "unrelated").isEmpty())
    }

    @Test
    fun marks_only_parents_as_having_children() {
        val labels = listOf(
            folder("root"),
            folder("child", parent_token = "root"),
            folder("grandchild", parent_token = "child"),
            folder("leaf_root"),
        )
        val by_token = flatten_folder_tree(labels).associateBy { it.label.label_token }
        assertTrue(by_token.getValue("root").has_children)
        assertTrue(by_token.getValue("child").has_children)
        assertFalse(by_token.getValue("grandchild").has_children)
        assertFalse(by_token.getValue("leaf_root").has_children)
    }

    @Test
    fun deepest_rendered_folder_is_not_marked_expandable() {
        var labels = listOf(folder("d0"))
        var parent = "d0"
        repeat(max_folder_depth + 1) { index ->
            val token = "d${index + 1}"
            labels = labels + folder(token, parent_token = parent)
            parent = token
        }
        val nodes = flatten_folder_tree(labels)
        val deepest = nodes.maxByOrNull { it.depth }!!
        assertEquals(max_folder_depth, deepest.depth)
        assertFalse(deepest.has_children)
    }
}
