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

import java.text.Collator
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.ReorderLabelEntry

private val folder_name_collator: Collator by lazy {
    Collator.getInstance().apply { strength = Collator.PRIMARY }
}

private fun split_runs(value: String): List<String> {
    val runs = mutableListOf<String>()
    var start = 0
    for (index in 1..value.length) {
        if (index == value.length || value[index].isDigit() != value[start].isDigit()) {
            runs.add(value.substring(start, index))
            start = index
        }
    }
    return runs
}

private fun compare_digit_runs(a: String, b: String): Int {
    val a_digits = a.trimStart('0')
    val b_digits = b.trimStart('0')
    if (a_digits.length != b_digits.length) return a_digits.length.compareTo(b_digits.length)
    val by_value = a_digits.compareTo(b_digits)
    if (by_value != 0) return by_value
    return a.length.compareTo(b.length)
}

fun compare_folder_names(a: String, b: String): Int {
    val a_runs = split_runs(a.trim())
    val b_runs = split_runs(b.trim())
    for (index in 0 until minOf(a_runs.size, b_runs.size)) {
        val a_run = a_runs[index]
        val b_run = b_runs[index]
        val both_digits = a_run[0].isDigit() && b_run[0].isDigit()
        val result = if (both_digits) {
            compare_digit_runs(a_run, b_run)
        } else {
            folder_name_collator.compare(a_run, b_run)
        }
        if (result != 0) return result
    }
    return a_runs.size.compareTo(b_runs.size)
}

private fun folder_name(label: LabelItem): String = label.encrypted_name.orEmpty()

val folder_a_z_comparator: Comparator<LabelItem> = Comparator { a, b ->
    compare_folder_names(folder_name(a), folder_name(b)).takeIf { it != 0 }
        ?: (a.created_at ?: "").compareTo(b.created_at ?: "").takeIf { it != 0 }
        ?: a.label_token.compareTo(b.label_token)
}

fun is_sorted_a_z(ordered: List<LabelItem>): Boolean =
    ordered.zipWithNext().all { (a, b) -> compare_folder_names(folder_name(a), folder_name(b)) <= 0 }

private fun renumber(ordered: List<LabelItem>): List<ReorderLabelEntry> =
    ordered.mapIndexedNotNull { index, label ->
        if (label.sort_order != index) ReorderLabelEntry(id = label.id, sort_order = index) else null
    }

private fun effective_parents(folders: List<LabelItem>): Map<String, String?> {
    val tokens = folders.map { it.label_token }.toSet()
    return folders.associate { folder ->
        folder.id to folder.parent_token
            ?.takeIf { it.isNotBlank() && it in tokens && it != folder.label_token }
    }
}

fun folder_sibling_groups(labels: List<LabelItem>): List<List<LabelItem>> {
    val folders = labels.filter { is_custom_folder(it) }
    val parents = effective_parents(folders)
    return folders
        .groupBy { parents[it.id] }
        .values
        .map { group -> group.sortedWith(sibling_comparator) }
}

fun folder_children(labels: List<LabelItem>, parent_token: String?): List<LabelItem> {
    val folders = labels.filter { is_custom_folder(it) }
    val tokens = folders.map { it.label_token }.toSet()
    val parent = parent_token?.takeIf { it.isNotBlank() && it in tokens }
    val parents = effective_parents(folders)
    return folders
        .filter { parents[it.id] == parent }
        .sortedWith(sibling_comparator)
}

fun is_folder_tree_sorted_a_z(labels: List<LabelItem>): Boolean =
    folder_sibling_groups(labels).all { is_sorted_a_z(it) }

fun can_sort_folders_a_z(labels: List<LabelItem>): Boolean =
    labels.count { is_custom_folder(it) } >= 2 && !is_folder_tree_sorted_a_z(labels)

fun sort_folder_tree_a_z(labels: List<LabelItem>): List<ReorderLabelEntry> =
    folder_sibling_groups(labels).flatMap { group -> renumber(group.sortedWith(folder_a_z_comparator)) }

fun place_folder_among_siblings(siblings: List<LabelItem>, folder: LabelItem): List<ReorderLabelEntry> {
    val others = siblings.filter { it.id != folder.id }
    val placed = folder.copy(sort_order = -1)
    if (!is_sorted_a_z(others)) return renumber(others + placed)
    val index = others.indexOfFirst { folder_a_z_comparator.compare(placed, it) < 0 }
    val ordered = if (index < 0) {
        others + placed
    } else {
        others.subList(0, index) + placed + others.subList(index, others.size)
    }
    return renumber(ordered)
}

fun resort_after_rename(labels: List<LabelItem>, label_id: String, new_name: String): List<ReorderLabelEntry> {
    val siblings = folder_sibling_group(labels, label_id)
    if (siblings.size < 2 || !is_sorted_a_z(siblings)) return emptyList()
    val renamed = siblings.map { if (it.id == label_id) it.copy(encrypted_name = new_name) else it }
    return renumber(renamed.sortedWith(folder_a_z_comparator))
}

fun append_sort_order(siblings: List<LabelItem>): Int =
    siblings.fold(0) { max, label -> maxOf(max, label.sort_order + 1) }

fun previous_folder_orders(labels: List<LabelItem>, entries: List<ReorderLabelEntry>): Map<String, Int> {
    val touched = entries.map { it.id }.toSet()
    return labels.filter { it.id in touched }.associate { it.id to it.sort_order }
}

fun apply_folder_orders(labels: List<LabelItem>, entries: List<ReorderLabelEntry>): List<LabelItem> {
    if (entries.isEmpty()) return labels
    val orders = entries.associate { it.id to it.sort_order }
    return labels.map { label -> orders[label.id]?.let { label.copy(sort_order = it) } ?: label }
}

fun restore_folder_orders(labels: List<LabelItem>, previous: Map<String, Int>): List<LabelItem> {
    if (previous.isEmpty()) return labels
    return labels.map { label -> previous[label.id]?.let { label.copy(sort_order = it) } ?: label }
}
