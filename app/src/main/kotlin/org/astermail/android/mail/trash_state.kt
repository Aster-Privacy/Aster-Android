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

package org.astermail.android.mail

enum class DestructiveAction {
    move_to_trash,
    delete_forever,
    discard_draft,
}

fun is_message_trashed(server_flag: Boolean?, metadata_flag: Boolean?): Boolean =
    server_flag ?: metadata_flag ?: false

fun destructive_action_for(is_trashed: Boolean, is_draft: Boolean): DestructiveAction = when {
    is_trashed -> DestructiveAction.delete_forever
    is_draft -> DestructiveAction.discard_draft
    else -> DestructiveAction.move_to_trash
}

fun permanent_delete_targets(
    target_id: String,
    message_scope: Boolean,
    thread_trash_flags: List<Pair<String, Boolean>>,
): List<String> {
    if (message_scope) return listOf(target_id)
    val siblings = thread_trash_flags.filter { it.second }.map { it.first }
    return (listOf(target_id) + siblings).distinct()
}

fun list_destructive_action(action: String, all_trashed: Boolean): String = when {
    !all_trashed -> action
    action == "delete" || action == "trash" -> "delete_permanent"
    else -> action
}

fun all_ids_trashed(ids: List<String>, trashed_ids: Set<String>): Boolean =
    ids.isNotEmpty() && ids.all { it in trashed_ids }

fun untrashed_ids(ids: List<String>, trashed_ids: Set<String>): List<String> =
    ids.filter { it !in trashed_ids }

data class SelectionDeletePlan(
    val trash_ids: List<String>,
    val trash_row_count: Int,
    val permanent_ids: List<String>,
    val permanent_rows: List<String>,
) {
    val all_trashed: Boolean get() = trash_ids.isEmpty() && permanent_ids.isNotEmpty()
}

fun plan_selection_delete(
    rows: Map<String, List<String>>,
    shown_ids: Set<String>,
    trashed_ids: Set<String>,
): SelectionDeletePlan {
    val trash_ids = LinkedHashSet<String>()
    val permanent_ids = LinkedHashSet<String>()
    val permanent_rows = ArrayList<String>()
    var trash_row_count = 0
    for ((row_id, row_members) in rows) {
        val members = row_members.ifEmpty { listOf(row_id) }
        val shown = members.filter { it in shown_ids }.ifEmpty { members }
        if (shown.all { it in trashed_ids }) {
            permanent_ids.addAll(members.filter { it in trashed_ids })
            permanent_rows.add(row_id)
        } else if (trash_ids.addAll(members.filter { it !in trashed_ids })) {
            trash_row_count++
        }
    }
    return SelectionDeletePlan(trash_ids.toList(), trash_row_count, permanent_ids.toList(), permanent_rows)
}
