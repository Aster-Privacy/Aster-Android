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

package org.astermail.android.labels

import org.astermail.android.api.tags.TagItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TagResolutionTest {

    private fun tag(
        token: String,
        name: String,
        color: String? = "#FF0000",
        icon: String? = "star",
    ): TagItem = TagItem(
        id = "id_$token",
        tag_token = token,
        encrypted_name = name,
        name_nonce = "nonce_$token",
        encrypted_color = color,
        encrypted_icon = icon,
    )

    @Test
    fun keeps_the_resolved_name_when_a_reload_cannot_decrypt() {
        val previous = listOf(tag("work", "Work"))
        val incoming = listOf(tag("work", "", color = "ciphertext", icon = "ciphertext"))
        val merged = merge_tag_snapshot(previous, incoming)
        assertEquals("Work", merged.single().encrypted_name)
        assertEquals("#FF0000", merged.single().encrypted_color)
        assertEquals("star", merged.single().encrypted_icon)
    }

    @Test
    fun takes_the_new_name_when_a_reload_can_decrypt() {
        val merged = merge_tag_snapshot(listOf(tag("work", "Work")), listOf(tag("work", "Office")))
        assertEquals("Office", merged.single().encrypted_name)
    }

    @Test
    fun drops_a_tag_the_server_no_longer_returns() {
        val merged = merge_tag_snapshot(listOf(tag("work", "Work")), listOf(tag("home", "Home")))
        assertEquals(listOf("home"), merged.map { it.tag_token })
        assertEquals("Home", merged.single().encrypted_name)
    }

    @Test
    fun keeps_a_first_load_unchanged() {
        val incoming = listOf(tag("work", ""))
        assertEquals(incoming, merge_tag_snapshot(emptyList(), incoming))
        assertEquals(emptyList<TagItem>(), merge_tag_snapshot(listOf(tag("work", "Work")), emptyList()))
    }

    @Test
    fun keeps_an_added_token_until_the_server_returns_it() {
        assertEquals(
            listOf("home", "work"),
            merge_tag_tokens(listOf("home"), mapOf("work" to true)),
        )
        assertEquals(
            listOf("home", "work"),
            merge_tag_tokens(listOf("home", "work"), mapOf("work" to true)),
        )
    }

    @Test
    fun keeps_a_removed_token_off_until_the_server_drops_it() {
        assertEquals(
            listOf("home"),
            merge_tag_tokens(listOf("home", "work"), mapOf("work" to false)),
        )
    }

    @Test
    fun leaves_the_server_tokens_alone_without_pending_edits() {
        val tokens = listOf("home", "work")
        assertEquals(tokens, merge_tag_tokens(tokens, emptyMap()))
    }
}
