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

package org.astermail.android.ui.compose

import org.astermail.android.crypto.same_address_ignoring_dots

private val address_shape = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

private val angle_form = Regex("""^(.*?)<\s*([^<>\s]+)\s*>\s*$""", RegexOption.DOT_MATCHES_ALL)

data class ParsedReplyAddress(val name: String?, val email: String)

fun parse_header_address(value: String): ParsedReplyAddress? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val angle = angle_form.find(trimmed)
    if (angle != null) {
        val raw_name = angle.groupValues[1].trim().trim('"').trim()
        val email = angle.groupValues[2].trim()
        if (!address_shape.matches(email)) return null
        return ParsedReplyAddress(raw_name.ifBlank { null }, email)
    }
    val bare = trimmed.trim('<', '>', '"', ' ')
    if (!address_shape.matches(bare)) return null
    return ParsedReplyAddress(null, bare)
}

fun first_header_address(value: String): ParsedReplyAddress? {
    var in_quote = false
    var split_at = value.length
    for ((index, character) in value.withIndex()) {
        if (character == '"') in_quote = !in_quote
        if (character == ',' && !in_quote) {
            split_at = index
            break
        }
    }
    return parse_header_address(value.substring(0, split_at))
}

fun extract_reply_to(raw_headers: List<Pair<String, String>>): ParsedReplyAddress? {
    val header = raw_headers.firstOrNull { it.first.equals("reply-to", ignoreCase = true) } ?: return null
    return first_header_address(header.second)
}

fun build_reply_recipient(
    sender_email: String,
    first_to: String?,
    reply_to: ParsedReplyAddress?,
    display_sender_email: String?,
    own_addresses: List<String>,
    is_own_message: Boolean,
): String {
    if (is_own_message) {
        val target = first_to?.trim().orEmpty()
        return target.ifBlank { sender_email }
    }
    if (!display_sender_email.isNullOrBlank()) return sender_email
    val candidate = reply_to?.email?.trim().orEmpty()
    if (candidate.isNotBlank() && own_addresses.none { same_address_ignoring_dots(it, candidate) }) {
        return candidate
    }
    return sender_email
}
