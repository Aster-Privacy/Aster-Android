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

package org.astermail.android.labels

import org.astermail.android.api.tags.TagItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class tag_tree_test {

    private fun tag(
        token: String,
        sort_order: Int = 0,
        parent_token: String? = null,
    ) = TagItem(
        id = "id_$token",
        tag_token = token,
        encrypted_name = token,
        name_nonce = "",
        sort_order = sort_order,
        parent_token = parent_token,
    )

    @Test
    fun deleting_a_nested_tag_moves_its_children_to_the_grandparent() {
        val remaining = remove_tag_reparenting_children(
            listOf(
                tag("root"),
                tag("middle", parent_token = "root"),
                tag("leaf", parent_token = "middle"),
                tag("deep", parent_token = "leaf"),
            ),
            "id_middle",
        )
        assertEquals(listOf("root", "leaf", "deep"), remaining.map { it.tag_token })
        assertEquals("root", remaining.first { it.tag_token == "leaf" }.parent_token)
        assertEquals("leaf", remaining.first { it.tag_token == "deep" }.parent_token)
    }

    @Test
    fun deleting_a_root_tag_moves_its_children_to_the_root() {
        val remaining = remove_tag_reparenting_children(
            listOf(tag("root"), tag("child", parent_token = "root"), tag("other")),
            "id_root",
        )
        assertEquals(listOf("child", "other"), remaining.map { it.tag_token })
        assertNull(remaining.first { it.tag_token == "child" }.parent_token)
    }

    @Test
    fun deleting_an_unknown_tag_leaves_the_list_unchanged() {
        val tags = listOf(tag("root"), tag("child", parent_token = "root"))
        assertEquals(tags, remove_tag_reparenting_children(tags, "id_missing"))
    }

    @Test
    fun children_follow_their_parent_in_sibling_order() {
        val nodes = flatten_tag_tree(
            listOf(
                tag("b", sort_order = 1),
                tag("a", sort_order = 0),
                tag("a2", sort_order = 1, parent_token = "a"),
                tag("a1", sort_order = 0, parent_token = "a"),
            ),
        )
        assertEquals(listOf("a", "a1", "a2", "b"), nodes.map { it.tag.tag_token })
        assertEquals(listOf(0, 1, 1, 0), nodes.map { it.depth })
        assertEquals(listOf(true, true, false, false), nodes.map { it.has_next })
        assertEquals(listOf(true, false, false, false), nodes.map { it.has_children })
        assertEquals(listOf(true), nodes[1].trail)
    }

    @Test
    fun tags_without_a_parent_field_stay_flat() {
        val nodes = flatten_tag_tree(listOf(tag("b", sort_order = 1), tag("a", sort_order = 0)))
        assertEquals(listOf("a", "b"), nodes.map { it.tag.tag_token })
        assertTrue(nodes.all { it.depth == 0 })
    }

    @Test
    fun missing_blank_and_self_parents_fall_back_to_the_root() {
        val index = tag_tree_index(
            listOf(
                tag("orphan", sort_order = 0, parent_token = "missing"),
                tag("blank", sort_order = 1, parent_token = ""),
                tag("self", sort_order = 2, parent_token = "self"),
            ),
        )
        assertEquals(listOf("orphan", "blank", "self"), index.nodes.map { it.tag.tag_token })
        assertTrue(index.nodes.all { it.depth == 0 })
        assertNull(index.parent_of("orphan"))
        assertNull(index.parent_of("self"))
    }

    @Test
    fun a_parent_cycle_keeps_every_tag_visible() {
        val index = tag_tree_index(
            listOf(
                tag("x", sort_order = 0, parent_token = "y"),
                tag("y", sort_order = 1, parent_token = "x"),
            ),
        )
        assertEquals(listOf("x", "y"), index.nodes.map { it.tag.tag_token })
        assertTrue(index.nodes.all { it.depth == 0 })
    }

    @Test
    fun blocked_parents_cover_self_descendants_and_the_depth_limit() {
        val index = tag_tree_index(
            listOf(
                tag("t0"),
                tag("t1", parent_token = "t0"),
                tag("t2", parent_token = "t1"),
                tag("t3", parent_token = "t2"),
                tag("t4", parent_token = "t3"),
                tag("t5", parent_token = "t4"),
                tag("t6", parent_token = "t5"),
                tag("t7", parent_token = "t6"),
                tag("t8", parent_token = "t7"),
                tag("t9", parent_token = "t8"),
                tag("r", sort_order = 1),
                tag("rc", parent_token = "r"),
            ),
        )
        assertEquals(10, max_tag_depth + 1)
        assertEquals(max_tag_depth, index.depths["t9"])
        assertEquals(1, index.subtree_height("r"))
        assertEquals(setOf("r", "rc", "t8", "t9"), index.blocked_parent_tokens("r"))
        assertEquals(setOf("rc", "t9"), index.blocked_parent_tokens("rc"))
        assertEquals(
            setOf("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9"),
            index.descendant_tokens("t0"),
        )
        assertEquals(listOf("t0", "t1", "t2"), index.path("t2").map { it.tag_token })
    }

    @Test
    fun sibling_groups_are_scoped_to_one_parent() {
        val a1 = tag("a1", sort_order = 0, parent_token = "a")
        val a2 = tag("a2", sort_order = 1, parent_token = "a")
        val index = tag_tree_index(listOf(tag("a"), tag("b", sort_order = 1), a2, a1))
        assertEquals(listOf("a1", "a2"), index.sibling_group("id_a1").map { it.tag_token })
        assertEquals(listOf("a", "b"), index.sibling_group("id_b").map { it.tag_token })
        assertEquals(listOf("a1", "a2"), index.children("a").map { it.tag_token })
        assertTrue(index.sibling_group("id_unknown").isEmpty())
        assertFalse(index.children("b").isNotEmpty())
    }

    @Test
    fun a_moved_tag_lands_after_its_new_siblings() {
        val a = tag("a")
        val b = tag("b", sort_order = 1)
        val c = tag("c", sort_order = 2)
        assertEquals(listOf("a", "b", "c"), place_tag_among_siblings(listOf(a, b), c).map { it.tag_token })
        assertEquals(listOf("b", "a"), place_tag_among_siblings(listOf(a, b), a).map { it.tag_token })
    }
}
