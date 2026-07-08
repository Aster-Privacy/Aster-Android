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

private val delivered_to_separator = Regex("[\\s,;]+")

fun extract_delivered_to(raw_headers: List<Pair<String, String>>): String? {
    val value = raw_headers
        .firstOrNull { it.first.equals("Delivered-To", ignoreCase = true) }
        ?.second
        ?.trim()
    if (value.isNullOrEmpty()) return null
    return value
        .split(delivered_to_separator)
        .asSequence()
        .map { it.trimStart('<').trimEnd('>') }
        .firstOrNull { it.contains("@") && it.length > 2 }
        ?.lowercase()
}

fun resolve_received_on_address(
    raw_headers: List<Pair<String, String>>,
    visible_addresses: List<String>,
    sender_email: String,
): String? {
    val delivered = extract_delivered_to(raw_headers) ?: return null
    val visible = (visible_addresses + sender_email).map { it.trim().lowercase() }
    return if (delivered in visible) null else delivered
}
