//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

const val filter_kind_label = "label"
const val filter_kind_tag = "tag"
const val filter_kind_alias = "alias"
const val filter_kind_folder = "folder"

const val alias_direction_all = "all"
const val alias_direction_received = "received"
const val alias_direction_sent = "sent"

private const val routing_prefix = "routing:"
private const val direction_separator = "|"

data class alias_routing_scope(
    val routing_token: String,
    val direction: String,
)

fun normalize_alias_direction(direction: String?): String = when (direction) {
    alias_direction_received -> alias_direction_received
    alias_direction_sent -> alias_direction_sent
    else -> alias_direction_all
}

fun alias_routing_folder(routing_token: String, direction: String = alias_direction_all): String {
    val normalized = normalize_alias_direction(direction)
    return if (normalized == alias_direction_all) {
        "$routing_prefix$routing_token"
    } else {
        "$routing_prefix$routing_token$direction_separator$normalized"
    }
}

fun parse_alias_routing_folder(folder: String): alias_routing_scope? {
    if (!folder.startsWith(routing_prefix)) return null
    val body = folder.removePrefix(routing_prefix)
    val separator_index = body.lastIndexOf(direction_separator)
    if (separator_index < 0) return alias_routing_scope(body, alias_direction_all)
    return alias_routing_scope(
        routing_token = body.substring(0, separator_index),
        direction = normalize_alias_direction(body.substring(separator_index + 1)),
    )
}

fun alias_direction_query(direction: String): String = when (normalize_alias_direction(direction)) {
    alias_direction_received -> "received"
    alias_direction_sent -> "sent"
    else -> "either"
}

fun mail_folder_for_filter(
    filter_kind: String?,
    filter_value: String,
    alias_direction: String = alias_direction_all,
): String = when (filter_kind) {
    filter_kind_label -> "label:$filter_value"
    filter_kind_tag -> "tag:$filter_value"
    filter_kind_alias -> alias_routing_folder(filter_value, alias_direction)
    filter_kind_folder -> filter_value
    else -> "inbox"
}
