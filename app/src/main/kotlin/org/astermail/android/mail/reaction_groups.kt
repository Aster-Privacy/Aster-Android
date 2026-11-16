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

data class ReactionChipGroup(
    val emoji: String,
    val count: Int,
    val includes_self: Boolean,
)

fun is_self_reaction(reaction: DecryptedReaction, my_email: String): Boolean =
    reaction.is_own ||
        (my_email.isNotBlank() && reaction.reactor_email.trim().equals(my_email.trim(), ignoreCase = true))

fun reaction_reactor_key(reaction: DecryptedReaction, my_email: String): String = when {
    is_self_reaction(reaction, my_email) -> "self"
    reaction.reactor_email.isNotBlank() -> reaction.reactor_email.trim().lowercase()
    else -> "item:${reaction.reaction_mail_item_id}"
}

fun group_reaction_chips(
    reactions: List<DecryptedReaction>,
    my_email: String,
): List<ReactionChipGroup> = reactions
    .filter { it.emoji.isNotBlank() }
    .groupBy { it.emoji }
    .map { (emoji, list) ->
        ReactionChipGroup(
            emoji = emoji,
            count = list.map { reaction_reactor_key(it, my_email) }.distinct().size,
            includes_self = list.any { is_self_reaction(it, my_email) },
        )
    }
