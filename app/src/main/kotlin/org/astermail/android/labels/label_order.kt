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

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.ReorderLabelEntry
import org.astermail.android.api.tags.ReorderTagEntry
import org.astermail.android.api.tags.TagItem
import org.astermail.android.looks_encrypted

const val LABEL_FOLDER_TYPE = "label"

fun label_rows(labels: List<LabelItem>): List<LabelItem> = labels
    .filter { it.folder_type == LABEL_FOLDER_TYPE }
    .filter { !it.encrypted_name.isNullOrBlank() && !looks_encrypted(it.encrypted_name) }
    .sortedWith(compareBy({ it.sort_order }, { it.created_at.orEmpty() }, { it.label_token }))

fun tag_rows(tags: List<TagItem>): List<TagItem> = tags
    .filter { it.encrypted_name.isNotBlank() && !looks_encrypted(it.encrypted_name) }
    .sortedWith(compareBy({ it.sort_order }, { it.created_at.orEmpty() }, { it.tag_token }))

fun <T> move_row(rows: List<T>, index: Int, direction: Int): List<T>? {
    if (direction == 0) return null
    val target = index + direction
    if (index < 0 || index > rows.lastIndex) return null
    if (target < 0 || target > rows.lastIndex) return null
    return rows.toMutableList().apply { add(target, removeAt(index)) }
}

fun label_reorder_entries(rows: List<LabelItem>): List<ReorderLabelEntry> = rows
    .mapIndexedNotNull { position, label ->
        if (label.sort_order != position) ReorderLabelEntry(id = label.id, sort_order = position) else null
    }

fun tag_reorder_entries(rows: List<TagItem>): List<ReorderTagEntry> = rows
    .mapIndexedNotNull { position, tag ->
        if (tag.sort_order != position) ReorderTagEntry(id = tag.id, sort_order = position) else null
    }
