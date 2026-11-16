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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionGroupsTest {

    private val me = "me@astermail.org"
    private val thumbs_up = "👍"
    private val thumbs_up_medium = "👍🏽"

    private fun reaction(id: String, emoji: String, reactor: String, is_own: Boolean = false) =
        DecryptedReaction(reaction_mail_item_id = id, emoji = emoji, reactor_email = reactor, is_own = is_own)

    @Test
    fun self_sent_reaction_counts_once() {
        val groups = group_reaction_chips(
            listOf(
                reaction("inbox_copy", thumbs_up_medium, me),
                reaction("sent_copy", thumbs_up_medium, me, is_own = true),
            ),
            me,
        )
        assertEquals(listOf(ReactionChipGroup(thumbs_up_medium, 1, true)), groups)
    }

    @Test
    fun same_person_with_one_emoji_counts_once() {
        val groups = group_reaction_chips(
            listOf(
                reaction("a", thumbs_up, "Ana@Example.com"),
                reaction("b", thumbs_up, "ana@example.com "),
            ),
            me,
        )
        assertEquals(1, groups.single().count)
    }

    @Test
    fun different_people_with_one_emoji_count_separately() {
        val groups = group_reaction_chips(
            listOf(
                reaction("a", thumbs_up, "ana@example.com"),
                reaction("b", thumbs_up, "ben@example.com"),
                reaction("c", thumbs_up, me, is_own = true),
            ),
            me,
        )
        assertEquals(3, groups.single().count)
        assertTrue(groups.single().includes_self)
    }

    @Test
    fun skin_tones_keep_their_own_chips() {
        val groups = group_reaction_chips(
            listOf(
                reaction("a", thumbs_up, "ana@example.com"),
                reaction("b", thumbs_up_medium, "ben@example.com"),
                reaction("c", thumbs_up_medium, me, is_own = true),
            ),
            me,
        )
        assertEquals(listOf(thumbs_up to 1, thumbs_up_medium to 2), groups.map { it.emoji to it.count })
    }

    @Test
    fun reactions_without_a_reactor_stay_separate() {
        val groups = group_reaction_chips(
            listOf(reaction("a", thumbs_up, ""), reaction("b", thumbs_up, "")),
            me,
        )
        assertEquals(2, groups.single().count)
    }
}
