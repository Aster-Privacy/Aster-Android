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

const val max_tag_depth = 9

data class tag_node(
    val tag: TagItem,
    val depth: Int,
    val trail: List<Boolean> = emptyList(),
    val has_next: Boolean = false,
    val has_children: Boolean = false,
)

private val tag_sibling_comparator = compareBy<TagItem>(
    { it.sort_order },
    { it.created_at.orEmpty() },
    { it.tag_token },
)

class tag_tree_index(tags: List<TagItem>) {
    private val built_nodes = mutableListOf<tag_node>()
    private val placed_parents = HashMap<String, String?>()
    private val sibling_groups = HashMap<String?, MutableList<TagItem>>()
    private val first_by_id = HashMap<String, TagItem>()
    private val by_token = HashMap<String, TagItem>()
    private val depth_by_token = HashMap<String, Int>()

    val nodes: List<tag_node> get() = built_nodes

    val depths: Map<String, Int> get() = depth_by_token

    init {
        for (tag in tags) {
            first_by_id.putIfAbsent(tag.id, tag)
            by_token.putIfAbsent(tag.tag_token, tag)
        }
        val by_parent = LinkedHashMap<String?, MutableList<TagItem>>()
        for (tag in tags) {
            val parent = tag.parent_token
                ?.takeIf { it.isNotBlank() && by_token.containsKey(it) && it != tag.tag_token }
            by_parent.getOrPut(parent) { mutableListOf() }.add(tag)
        }
        val visited = mutableSetOf<String>()
        fun place(node: tag_node, parent: String?) {
            built_nodes.add(node)
            placed_parents[node.tag.tag_token] = parent
            depth_by_token[node.tag.tag_token] = node.depth
            sibling_groups.getOrPut(parent) { mutableListOf() }.add(node.tag)
        }
        fun walk(parent: String?, depth: Int, trail: List<Boolean>) {
            val children = by_parent[parent] ?: return
            val sorted = children.sortedWith(tag_sibling_comparator)
            sorted.forEachIndexed { index, child ->
                if (!visited.add(child.tag_token)) return@forEachIndexed
                val has_next = index < sorted.lastIndex
                val can_descend = depth < max_tag_depth
                val has_children = can_descend && by_parent[child.tag_token]?.isNotEmpty() == true
                place(tag_node(child, depth, trail, has_next, has_children), parent)
                if (can_descend) walk(child.tag_token, depth + 1, trail + has_next)
            }
        }
        walk(null, 0, emptyList())
        for (tag in tags.sortedWith(tag_sibling_comparator)) {
            if (visited.add(tag.tag_token)) place(tag_node(tag, 0), null)
        }
    }

    fun parent_of(token: String): String? = placed_parents[token]

    fun children(parent_token: String?): List<TagItem> =
        sibling_groups[parent_token?.takeIf { it.isNotBlank() && by_token.containsKey(it) }].orEmpty()

    fun sibling_group(tag_id: String): List<TagItem> {
        val target = first_by_id[tag_id] ?: return emptyList()
        return sibling_groups[placed_parents[target.tag_token]].orEmpty()
    }

    fun path(token: String): List<TagItem> {
        val path = mutableListOf<TagItem>()
        val seen = mutableSetOf<String>()
        var current = by_token[token]
        while (current != null && seen.add(current.tag_token)) {
            path.add(current)
            current = placed_parents[current.tag_token]?.let { by_token[it] }
        }
        return path.reversed()
    }

    fun descendant_tokens(token: String): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(token)
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            for (child in sibling_groups[next].orEmpty()) {
                if (result.add(child.tag_token)) queue.add(child.tag_token)
            }
        }
        return result
    }

    fun subtree_height(token: String): Int {
        val base = depth_by_token[token] ?: return 0
        return descendant_tokens(token).maxOfOrNull { (depth_by_token[it] ?: base) - base } ?: 0
    }

    fun blocked_parent_tokens(token: String): Set<String> {
        val height = subtree_height(token)
        val too_deep = built_nodes
            .filter { it.depth + 1 + height > max_tag_depth }
            .map { it.tag.tag_token }
        return descendant_tokens(token) + too_deep + token
    }
}

fun flatten_tag_tree(tags: List<TagItem>): List<tag_node> = tag_tree_index(tags).nodes

fun remove_tag_reparenting_children(tags: List<TagItem>, tag_id: String): List<TagItem> {
    val removed = tags.firstOrNull { it.id == tag_id } ?: return tags
    val inherited = removed.parent_token?.takeIf { it.isNotBlank() }
    return tags.filter { it.id != tag_id }.map { tag ->
        if (tag.parent_token == removed.tag_token) tag.copy(parent_token = inherited) else tag
    }
}

fun place_tag_among_siblings(siblings: List<TagItem>, tag: TagItem): List<TagItem> =
    siblings.filter { it.id != tag.id } + tag
