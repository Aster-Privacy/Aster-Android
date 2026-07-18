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

const val max_folder_depth = 4

data class folder_node(
    val label: LabelItem,
    val depth: Int,
)

fun is_custom_folder(label: LabelItem): Boolean =
    !label.is_system && (label.folder_type == "folder" || label.folder_type == "custom")

private val sibling_comparator = compareBy<LabelItem>(
    { it.sort_order },
    { it.created_at ?: "" },
    { it.label_token },
)

fun flatten_folder_tree(labels: List<LabelItem>): List<folder_node> {
    val folders = labels.filter { is_custom_folder(it) }
    val tokens = folders.map { it.label_token }.toSet()
    val by_parent = LinkedHashMap<String?, MutableList<LabelItem>>()
    for (folder in folders) {
        val parent = folder.parent_token
            ?.takeIf { it.isNotBlank() && it in tokens && it != folder.label_token }
        by_parent.getOrPut(parent) { mutableListOf() }.add(folder)
    }
    val result = mutableListOf<folder_node>()
    val visited = mutableSetOf<String>()
    fun walk(parent: String?, depth: Int) {
        val children = by_parent[parent] ?: return
        for (child in children.sortedWith(sibling_comparator)) {
            if (!visited.add(child.label_token)) continue
            result.add(folder_node(child, depth))
            if (depth < max_folder_depth) walk(child.label_token, depth + 1)
        }
    }
    walk(null, 0)
    for (folder in folders.sortedWith(sibling_comparator)) {
        if (visited.add(folder.label_token)) result.add(folder_node(folder, 0))
    }
    return result
}

fun folder_sibling_group(labels: List<LabelItem>, label_id: String): List<LabelItem> {
    val folders = labels.filter { is_custom_folder(it) }
    val target = folders.firstOrNull { it.id == label_id } ?: return emptyList()
    val tokens = folders.map { it.label_token }.toSet()
    fun effective_parent(label: LabelItem): String? = label.parent_token
        ?.takeIf { it.isNotBlank() && it in tokens && it != label.label_token }
    val parent = effective_parent(target)
    return folders
        .filter { effective_parent(it) == parent }
        .sortedWith(sibling_comparator)
}

fun folder_path(labels: List<LabelItem>, token: String): List<String> {
    val folders = labels.filter { is_custom_folder(it) }
    val by_token = folders.associateBy { it.label_token }
    val path = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var current = by_token[token]
    while (current != null && seen.add(current.label_token)) {
        path.add(current.encrypted_name.orEmpty())
        current = current.parent_token
            ?.takeIf { it.isNotBlank() }
            ?.let { by_token[it] }
    }
    return path.reversed()
}

fun descendant_tokens(labels: List<LabelItem>, token: String): Set<String> {
    val folders = labels.filter { is_custom_folder(it) }
    val result = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(token)
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        for (folder in folders) {
            if (folder.parent_token == next && result.add(folder.label_token)) {
                queue.add(folder.label_token)
            }
        }
    }
    return result
}
