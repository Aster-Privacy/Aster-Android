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

import org.astermail.android.api.preferences.LearnedCategorySender
import org.astermail.android.api.preferences.is_valid_learned_category
import org.astermail.android.api.preferences.normalize_learned_sender
import org.astermail.android.api.preferences.sanitize_learned_category_senders
import org.astermail.android.ui.mail.is_aster_system_address

const val MAX_LEARNED_SENDERS_PER_MOVE = 200

data class LearnedCategoryEdit(
    val upserts: List<LearnedCategorySender> = emptyList(),
    val removals: Set<String> = emptySet(),
) {
    fun is_empty(): Boolean = upserts.isEmpty() && removals.isEmpty()
}

data class LearnedCategoryMove(
    val apply: LearnedCategoryEdit,
    val undo: LearnedCategoryEdit,
)

data class LearnedMoveSource(
    val sender_email: String,
    val sender_verification: String?,
    val system_origin: Boolean,
    val is_external: Boolean,
)

fun learned_move_source(item: InboxItem): LearnedMoveSource =
    LearnedMoveSource(
        sender_email = item.sender_email,
        sender_verification = item.sender_verification,
        system_origin = item.raw_item.system_origin,
        is_external = item.raw_item.is_external,
    )

fun is_locked_learned_source(source: LearnedMoveSource): Boolean =
    source.system_origin && !source.is_external && is_aster_system_address(source.sender_email)

fun learned_senders_for_move(sources: List<LearnedMoveSource>): List<String> {
    val senders = LinkedHashSet<String>()
    for (source in sources) {
        if (senders.size >= MAX_LEARNED_SENDERS_PER_MOVE) break
        if (source.sender_verification == "invalid") continue
        if (is_locked_learned_source(source)) continue
        val sender = normalize_learned_sender(source.sender_email) ?: continue
        senders.add(sender)
    }
    return senders.toList()
}

fun learn_from_category_move(
    current: List<LearnedCategorySender>,
    learning_enabled: Boolean,
    sources: List<LearnedMoveSource>,
    category: String,
    now_seconds: Long,
): LearnedCategoryMove? {
    if (!learning_enabled) return null
    if (!is_valid_learned_category(category)) return null
    val senders = learned_senders_for_move(sources)
    if (senders.isEmpty()) return null
    val existing = current.associateBy { it.sender }
    val upserts = senders.map { LearnedCategorySender(it, category, now_seconds) }
    val undo_upserts = senders.mapNotNull { existing[it] }
    val undo_removals = senders.filterTo(LinkedHashSet()) { existing[it] == null }
    return LearnedCategoryMove(
        apply = LearnedCategoryEdit(upserts = upserts),
        undo = LearnedCategoryEdit(upserts = undo_upserts, removals = undo_removals),
    )
}

fun apply_learned_category_edit(
    current: List<LearnedCategorySender>,
    edit: LearnedCategoryEdit,
): List<LearnedCategorySender> {
    if (edit.is_empty()) return current
    val touched = HashSet<String>(edit.removals)
    edit.upserts.forEach { touched.add(it.sender) }
    val kept = current.filter { it.sender !in touched }
    return sanitize_learned_category_senders(kept + edit.upserts)
}

fun learned_category_lookup(entries: List<LearnedCategorySender>): Map<String, String> {
    val lookup = LinkedHashMap<String, String>()
    for (entry in sanitize_learned_category_senders(entries)) {
        lookup.putIfAbsent(entry.sender, entry.category)
    }
    return lookup
}
