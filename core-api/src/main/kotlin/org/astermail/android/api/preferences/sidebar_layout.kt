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

package org.astermail.android.api.preferences

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

const val SIDEBAR_SECTION_ORDER_KEY = "sidebar_section_order"
const val SIDEBAR_HIDDEN_ITEMS_KEY = "sidebar_hidden_items"

const val SIDEBAR_SECTION_MAIL = "mail"
const val SIDEBAR_SECTION_FOLDERS = "folders"
const val SIDEBAR_SECTION_LABELS = "labels"
const val SIDEBAR_SECTION_ALIASES = "aliases"

val SIDEBAR_SECTION_IDS: List<String> = listOf(
    SIDEBAR_SECTION_MAIL,
    SIDEBAR_SECTION_FOLDERS,
    SIDEBAR_SECTION_LABELS,
    SIDEBAR_SECTION_ALIASES,
)

val SIDEBAR_HIDEABLE_SECTION_IDS: List<String> = listOf(
    SIDEBAR_SECTION_FOLDERS,
    SIDEBAR_SECTION_LABELS,
    SIDEBAR_SECTION_ALIASES,
)

val SIDEBAR_HIDEABLE_MAIL_ITEM_IDS: List<String> = listOf(
    "sent",
    "scheduled",
    "snoozed",
    "drafts",
    "contacts",
    "starred",
    "all",
    "archive",
    "spam",
    "trash",
    "subscriptions",
)

const val MAX_SIDEBAR_LAYOUT_IDS = 32

private val sidebar_layout_id_pattern = Regex("^[a-z][a-z0-9_]{0,31}$")

private val hideable_ids: Set<String> =
    (SIDEBAR_HIDEABLE_SECTION_IDS + SIDEBAR_HIDEABLE_MAIL_ITEM_IDS).toSet()

private val known_section_ids: Set<String> = SIDEBAR_SECTION_IDS.toSet()

private fun sanitize_id_list(raw: List<String>?): List<String> {
    if (raw == null) return emptyList()
    val seen = HashSet<String>()
    val result = ArrayList<String>()
    for (entry in raw) {
        if (result.size >= MAX_SIDEBAR_LAYOUT_IDS) break
        if (!sidebar_layout_id_pattern.matches(entry)) continue
        if (!seen.add(entry)) continue
        result.add(entry)
    }
    return result
}

fun sanitize_sidebar_section_order(raw: List<String>?): List<String> {
    val listed = sanitize_id_list(raw)
    return listed + SIDEBAR_SECTION_IDS.filter { it !in listed }
}

fun sanitize_sidebar_hidden_items(raw: List<String>?): List<String> = sanitize_id_list(raw)

fun resolve_sidebar_section_order(raw: List<String>?): List<String> =
    sanitize_sidebar_section_order(raw).filter { it in known_section_ids }

fun is_sidebar_item_hideable(id: String): Boolean = id in hideable_ids

fun is_sidebar_item_hidden(hidden: List<String>?, id: String): Boolean =
    is_sidebar_item_hideable(id) && id in sanitize_sidebar_hidden_items(hidden)

fun set_sidebar_item_hidden(hidden: List<String>?, id: String, should_hide: Boolean): List<String> {
    val current = sanitize_sidebar_hidden_items(hidden)
    if (!is_sidebar_item_hideable(id)) return current
    val without = current.filter { it != id }
    if (!should_hide) return without
    if (without.size < MAX_SIDEBAR_LAYOUT_IDS) return without + id
    val evict_index = without.indexOfFirst { !is_sidebar_item_hideable(it) }
    if (evict_index == -1) return current
    return without.filterIndexed { index, _ -> index != evict_index } + id
}

fun reset_sidebar_section_order(order: List<String>?): List<String> {
    val defaults = SIDEBAR_SECTION_IDS.iterator()
    return sanitize_sidebar_section_order(order).map { if (it in known_section_ids) defaults.next() else it }
}

fun reset_sidebar_hidden_items(hidden: List<String>?): List<String> =
    sanitize_sidebar_hidden_items(hidden).filter { !is_sidebar_item_hideable(it) }

fun move_sidebar_section(order: List<String>?, id: String, direction: Int): List<String> {
    val full = sanitize_sidebar_section_order(order)
    if (id !in known_section_ids || (direction != -1 && direction != 1)) return full
    val from = full.indexOf(id)
    var to = from + direction
    while (to in full.indices && full[to] !in known_section_ids) to += direction
    if (to !in full.indices) return full
    val next = full.toMutableList()
    next[from] = full[to]
    next[to] = id
    return next
}

fun can_move_sidebar_section(order: List<String>?, id: String, direction: Int): Boolean {
    val resolved = resolve_sidebar_section_order(order)
    val index = resolved.indexOf(id)
    if (index == -1 || (direction != -1 && direction != 1)) return false
    return (index + direction) in resolved.indices
}

fun is_default_sidebar_layout(order: List<String>?, hidden: List<String>?): Boolean =
    resolve_sidebar_section_order(order) == SIDEBAR_SECTION_IDS &&
        sanitize_sidebar_hidden_items(hidden).none { is_sidebar_item_hideable(it) }

fun visible_sidebar_sections(preferences: UserPreferences?): List<String> {
    val hidden = preferences?.sidebar_hidden_items
    return resolve_sidebar_section_order(preferences?.sidebar_section_order)
        .filter { !is_sidebar_item_hidden(hidden, it) }
}

internal fun string_entries_only(element: JsonElement): JsonElement {
    if (element !is JsonArray) return element
    return JsonArray(element.filter { it is JsonPrimitive && it.isString })
}

fun normalize_sidebar_layout_preferences(preferences: UserPreferences): UserPreferences =
    preferences.copy(
        sidebar_section_order = sanitize_sidebar_section_order(preferences.sidebar_section_order),
        sidebar_hidden_items = sanitize_sidebar_hidden_items(preferences.sidebar_hidden_items),
    )
