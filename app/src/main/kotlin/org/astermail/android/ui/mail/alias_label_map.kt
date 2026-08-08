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

package org.astermail.android.ui.mail

data class AliasLabelEntry(
    val address: String?,
    val address_hash: String? = null,
)

fun normalize_alias_key(address: String?): String {
    return address?.trim()?.lowercase().orEmpty()
}

private fun alias_label_of(entry: AliasLabelEntry): String? {
    val key = normalize_alias_key(entry.address)
    if (key.isEmpty() || !key.contains("@")) return null
    val local_part = key.substringBefore("@")
    if (local_part.isEmpty()) return null
    return local_part
}

fun build_alias_label_map(entries: List<AliasLabelEntry>): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    for (entry in entries) {
        val key = normalize_alias_key(entry.address)
        if (map.containsKey(key)) continue
        val label = alias_label_of(entry) ?: continue
        map[key] = label
    }
    return map
}

fun build_alias_token_label_map(entries: List<AliasLabelEntry>): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    for (entry in entries) {
        val token = entry.address_hash?.trim().orEmpty()
        if (token.isEmpty()) continue
        if (map.containsKey(token)) continue
        val label = alias_label_of(entry) ?: continue
        map[token] = label
    }
    return map
}

fun resolve_alias_delivery_label(
    token_labels: Map<String, String>,
    labels: Map<String, String>,
    routing_token: String?,
    address: String?,
): String? {
    val token = routing_token?.trim().orEmpty()
    if (token.isNotEmpty()) {
        token_labels[token]?.let { return it }
    }
    val key = normalize_alias_key(address)
    if (key.isEmpty()) return null
    return labels[key]
}
