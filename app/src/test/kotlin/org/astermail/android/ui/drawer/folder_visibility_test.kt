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

package org.astermail.android.ui.drawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import org.junit.Assert.assertEquals
import org.junit.Test

class folder_visibility_test {

    private fun item(id: String, depth: Int, has_children: Boolean = false) = drawer_folder_item(
        id = id,
        label = id,
        icon = Icons.Outlined.Folder,
        depth = depth,
        has_children = has_children,
    )

    private val tree = listOf(
        item("root", depth = 0, has_children = true),
        item("child_a", depth = 1, has_children = true),
        item("grandchild", depth = 2),
        item("child_b", depth = 1),
        item("other_root", depth = 0),
    )

    private fun visible(expanded: Set<String>, max_root_count: Int? = null) =
        visible_folder_items(tree, expanded, max_root_count).map { it.id }

    @Test
    fun hides_all_descendants_when_nothing_is_expanded() {
        assertEquals(listOf("root", "other_root"), visible(emptySet()))
    }

    @Test
    fun expanding_a_parent_reveals_only_direct_children() {
        assertEquals(listOf("root", "child_a", "child_b", "other_root"), visible(setOf("root")))
    }

    @Test
    fun expanding_nested_parents_reveals_deeper_levels() {
        assertEquals(
            listOf("root", "child_a", "grandchild", "child_b", "other_root"),
            visible(setOf("root", "child_a")),
        )
    }

    @Test
    fun expanded_child_stays_hidden_while_its_parent_is_collapsed() {
        assertEquals(listOf("root", "other_root"), visible(setOf("child_a")))
    }

    @Test
    fun truncates_roots_without_dropping_visible_descendants() {
        assertEquals(listOf("root", "child_a", "child_b"), visible(setOf("root"), max_root_count = 1))
    }

    @Test
    fun counts_roots_only() {
        assertEquals(2, root_folder_count(tree))
    }

    @Test
    fun unknown_expanded_tokens_are_ignored() {
        assertEquals(listOf("root", "other_root"), visible(setOf("does_not_exist")))
    }

    @Test
    fun empty_input_yields_empty_output() {
        assertEquals(emptyList<String>(), visible_folder_items(emptyList(), setOf("root")).map { it.id })
    }
}
