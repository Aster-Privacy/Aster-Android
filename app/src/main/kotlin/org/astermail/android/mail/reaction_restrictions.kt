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

import org.astermail.android.R

const val MAX_REACTION_RECIPIENTS = 20
const val MAX_REACTION_EMOJIS = 20

enum class ReactionRestriction {
    disabled,
    own_message,
    draft,
    spam_or_trash,
    reply_to,
    too_many_recipients,
    bcc,
    too_many_emojis,
    no_recipient,
}

fun reaction_restriction_string(restriction: ReactionRestriction): Int = when (restriction) {
    ReactionRestriction.disabled -> R.string.reactions_disabled
    ReactionRestriction.own_message -> R.string.cannot_react_own_message
    ReactionRestriction.draft -> R.string.cannot_react_draft
    ReactionRestriction.spam_or_trash -> R.string.cannot_react_spam_or_trash
    ReactionRestriction.reply_to -> R.string.cannot_react_reply_to
    ReactionRestriction.too_many_recipients -> R.string.cannot_react_too_many_recipients
    ReactionRestriction.bcc -> R.string.cannot_react_bcc
    ReactionRestriction.too_many_emojis -> R.string.cannot_react_too_many_emojis
    ReactionRestriction.no_recipient -> R.string.cannot_react_no_recipient
}

private fun normalize(value: String?): String = value?.trim()?.lowercase().orEmpty()

private val ANGLED_ADDRESS = Regex("<([^>]+)>")

fun extract_reaction_addresses(value: String): List<String> = value
    .split(",")
    .map { part ->
        val angled = ANGLED_ADDRESS.find(part)?.groupValues?.getOrNull(1)
        normalize(angled ?: part)
    }
    .filter { it.contains("@") }

private fun header_value(raw_headers: List<Pair<String, String>>, name: String): String? =
    raw_headers.firstOrNull { normalize(it.first) == name }?.second

private fun has_distinct_reply_to(
    raw_headers: List<Pair<String, String>>,
    sender_email: String,
): Boolean {
    val reply_to = header_value(raw_headers, "reply-to") ?: return false
    val addresses = extract_reaction_addresses(reply_to)
    if (addresses.isEmpty()) return false
    val sender = normalize(sender_email)
    return addresses.any { it != sender }
}

private fun is_addressed_to(
    to_addresses: List<String>,
    cc_addresses: List<String>,
    user_email: String,
    is_own_address: (String) -> Boolean,
): Boolean {
    val me = normalize(user_email)
    if (me.isEmpty()) return true
    val visible = to_addresses + cc_addresses
    if (visible.isEmpty()) return true
    return visible.any { recipient ->
        val email = normalize(recipient)
        email == me || is_own_address(email)
    }
}

fun reaction_restriction(
    item_type: String,
    sender_email: String,
    to_addresses: List<String>,
    cc_addresses: List<String>,
    raw_headers: List<Pair<String, String>>,
    reactions: List<DecryptedReaction>,
    user_email: String,
    is_spam: Boolean,
    is_trashed: Boolean,
    reactions_enabled: Boolean,
    is_own_address: (String) -> Boolean = { false },
): ReactionRestriction? {
    if (!reactions_enabled) return ReactionRestriction.disabled
    if (item_type == "sent") return ReactionRestriction.own_message
    if (item_type == "draft" || item_type == "scheduled") return ReactionRestriction.draft
    if (is_spam || is_trashed) return ReactionRestriction.spam_or_trash
    if (has_distinct_reply_to(raw_headers, sender_email)) return ReactionRestriction.reply_to
    if (to_addresses.size + cc_addresses.size > MAX_REACTION_RECIPIENTS) {
        return ReactionRestriction.too_many_recipients
    }
    if (!is_addressed_to(to_addresses, cc_addresses, user_email, is_own_address)) {
        return ReactionRestriction.bcc
    }
    if (reactions.map { it.emoji }.filter { it.isNotBlank() }.toSet().size >= MAX_REACTION_EMOJIS) {
        return ReactionRestriction.too_many_emojis
    }
    if (normalize(sender_email).isEmpty()) return ReactionRestriction.no_recipient
    return null
}
