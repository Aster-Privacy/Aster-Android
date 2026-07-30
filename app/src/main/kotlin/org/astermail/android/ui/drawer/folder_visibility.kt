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

const val folders_collapsed_root_count = 5

fun root_folder_count(items: List<drawer_folder_item>): Int =
    items.count { it.depth == 0 }

fun visible_folder_items(
    items: List<drawer_folder_item>,
    expanded_tokens: Set<String>,
    max_root_count: Int? = null,
): List<drawer_folder_item> {
    val visible = mutableListOf<drawer_folder_item>()
    var hidden_below_depth: Int? = null
    var roots_taken = 0
    for (item in items) {
        val hidden_depth = hidden_below_depth
        if (hidden_depth != null) {
            if (item.depth > hidden_depth) continue
            hidden_below_depth = null
        }
        if (item.depth == 0) {
            if (max_root_count != null && roots_taken >= max_root_count) {
                hidden_below_depth = 0
                continue
            }
            roots_taken++
        }
        visible.add(item)
        if (item.has_children && item.id !in expanded_tokens) {
            hidden_below_depth = item.depth
        }
    }
    return visible
}
