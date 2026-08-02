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

fun compute_received_on_alias(
    recipient_addresses: List<String>,
    alias_options: List<String>,
    user_email: String,
): String? {
    val options_by_lower = alias_options.associateBy { it.lowercase() }
    val matches = recipient_addresses
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { options_by_lower[it.lowercase()] }
    return matches.firstOrNull { !same_address_ignoring_dots(it, user_email) }
        ?: matches.firstOrNull()
}

data class ReactionSenderIdentity(
    val email: String,
    val alias_hash: String?,
)

fun resolve_reaction_sender_identity(
    own_recipient_addresses: List<String>,
    message_sender_email: String,
    is_own_message: Boolean,
    alias_options: List<String>,
    alias_hash_map: Map<String, String>,
    user_email: String,
): ReactionSenderIdentity {
    val resolved = if (is_own_message) {
        alias_options.firstOrNull { it.equals(message_sender_email.trim(), ignoreCase = true) }
    } else {
        compute_received_on_alias(own_recipient_addresses, alias_options, user_email)
    }
    val email = resolved?.takeIf { it.isNotBlank() } ?: user_email
    if (same_address_ignoring_dots(email, user_email)) {
        return ReactionSenderIdentity(user_email, null)
    }
    val hash = alias_hash_map.entries
        .firstOrNull { it.key.equals(email, ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }
        ?: return ReactionSenderIdentity(user_email, null)
    return ReactionSenderIdentity(email, hash)
}

fun reply_from_mismatch(
    mode: String?,
    received_on_alias: String?,
    from_alias: String,
): Boolean {
    if (mode != "reply" && mode != "reply_all") return false
    val received = received_on_alias?.trim().orEmpty()
    if (received.isEmpty()) return false
    return !received.equals(from_alias.trim(), ignoreCase = true)
}

fun resolve_reply_from_alias(
    received_on_alias: String?,
    thread_ghost_match: String?,
    primary_sender_email: String,
    alias_options: List<String>,
): String {
    return received_on_alias
        ?: thread_ghost_match?.takeIf { it in alias_options }
        ?: primary_sender_email.takeIf { it.isNotBlank() && it in alias_options }
        ?: alias_options.firstOrNull().orEmpty()
}
