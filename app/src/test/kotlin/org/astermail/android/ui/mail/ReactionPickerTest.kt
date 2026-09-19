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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionPickerTest {

    @Test
    fun section_starts_account_for_one_header_per_section() {
        assertEquals(listOf(0, 4, 6), emoji_section_starts(listOf(3, 1, 5)))
    }

    @Test
    fun item_index_maps_to_its_section() {
        val starts = listOf(0, 4, 6)
        assertEquals(0, emoji_section_for_index(starts, 0))
        assertEquals(0, emoji_section_for_index(starts, 3))
        assertEquals(1, emoji_section_for_index(starts, 4))
        assertEquals(2, emoji_section_for_index(starts, 9))
        assertEquals(0, emoji_section_for_index(emptyList(), 5))
    }

    @Test
    fun grid_scrolled_to_end_selects_last_section() {
        val starts = listOf(0, 4, 6)
        assertEquals(2, emoji_section_for_index(starts, 3, at_end = true))
        assertEquals(0, emoji_section_for_index(emptyList(), 3, at_end = true))
    }

    @Test
    fun search_matches_every_term_and_deduplicates() {
        val entries = listOf(
            emoji_entry("👍", "thumbs up yes"),
            emoji_entry("👎", "thumbs down no"),
            emoji_entry("👍", "thumbs up yes"),
        )
        assertEquals(listOf("👍"), emoji_search(entries, " Thumbs  UP ").map { it.glyph })
        assertTrue(emoji_search(entries, "   ").isEmpty())
        assertTrue(emoji_search(entries, "banana").isEmpty())
    }

    @Test
    fun catalog_has_no_empty_group_and_ends_with_flags() {
        assertTrue(emoji_catalog.all { it.entries.isNotEmpty() })
        assertTrue(emoji_catalog.last().entries.all { it.keywords.contains("flag") })
    }

    @Test
    fun chip_palette_uses_solid_fills() {
        val light = reaction_chip_palette(is_dark = false)
        val dark = reaction_chip_palette(is_dark = true)
        assertEquals(Color(0xFFD3E3FD), light.own_fill)
        assertEquals(Color(0xFF0842A0), light.own_text)
        assertEquals(Color(0xFFECEEF1), light.other_fill)
        assertEquals(Color(0xFF004A77), dark.own_fill)
        assertEquals(Color(0xFFC2E7FF), dark.own_text)
        assertEquals(Color(0xFF282A2C), dark.other_fill)
        listOf(light, dark).forEach { palette ->
            assertEquals(1f, palette.own_fill.alpha)
            assertEquals(1f, palette.other_fill.alpha)
        }
    }
}
