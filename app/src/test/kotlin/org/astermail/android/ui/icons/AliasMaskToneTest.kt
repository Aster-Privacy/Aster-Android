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

package org.astermail.android.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasMaskToneTest {
    @Test
    fun known_addresses_match_the_web_tones() {
        assertEquals(0, alias_mask_tone_index("work@astermail.org"))
        assertEquals(1, alias_mask_tone_index("work.dana@astermail.org"))
        assertEquals(2, alias_mask_tone_index("newsletters@astermail.org"))
        assertEquals(3, alias_mask_tone_index("travel@astermail.org"))
        assertEquals(4, alias_mask_tone_index("shopping@astermail.org"))
    }

    @Test
    fun non_ascii_and_empty_addresses_match_the_web_tones() {
        assertEquals(2, alias_mask_tone_index("ü@aster.cx"))
        assertEquals(0, alias_mask_tone_index("名前@astermail.org"))
        assertEquals(1, alias_mask_tone_index(""))
    }

    @Test
    fun case_and_spaces_do_not_change_the_tone() {
        assertEquals(
            alias_mask_tone_index("travel@astermail.org"),
            alias_mask_tone_index("  Travel@AsterMail.org "),
        )
    }

    @Test
    fun light_and_dark_tones_differ() {
        for (address in listOf("work@astermail.org", "travel@astermail.org", "shopping@astermail.org")) {
            assertNotEquals(alias_mask_tone(address, is_dark = false), alias_mask_tone(address, is_dark = true))
        }
        assertTrue(alias_mask_tone_index("x7k2qm9p@aster.cx") in 0..4)
    }
}
