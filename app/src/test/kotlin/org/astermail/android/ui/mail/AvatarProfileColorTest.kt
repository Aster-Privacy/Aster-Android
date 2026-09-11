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
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarProfileColorTest {

    @Test
    fun pink_profile_color_wins_over_generated_palette() {
        val (background, text) = avatar_colors_for("sher@aster.cx", "#ec4899")
        assertEquals(Color(0xFFEC4899), background)
        assertEquals(Color.White, text)
        assertEquals(avatar_colors_for("sher@aster.cx"), avatar_colors_for("sher@aster.cx", null))
    }

    @Test
    fun short_and_alpha_hex_forms_parse() {
        assertEquals(Color(0xFFFF00AA), parse_profile_color("f0a"))
        assertEquals(Color(0xFFEC4899), parse_profile_color("EC4899FF"))
        assertEquals(Color(0xFFEC4899), parse_profile_color("  #ec4899 "))
    }

    @Test
    fun invalid_profile_color_falls_back_to_generated_palette() {
        assertNull(parse_profile_color("not-a-color"))
        assertNull(parse_profile_color(""))
        assertNull(parse_profile_color(null))
        assertNull(parse_profile_color("#١٢٣٤٥٦"))
        assertEquals(avatar_colors_for("aster"), avatar_colors_for("aster", "zzzzzz"))
    }

    @Test
    fun light_profile_color_gets_dark_text() {
        assertEquals(Color(0xFFFDE047) to Color(0xFF111827), avatar_colors_for("aster", "#fde047"))
        assertEquals(Color(0xFFFFFFFF) to Color(0xFF111827), avatar_colors_for("aster", "#fff"))
    }

    @Test
    fun key_uses_the_raw_address_like_the_web_client() {
        assertEquals("Sher@Aster.cx", avatar_key_for("Sher@Aster.cx", "Sher Holmes"))
        assertEquals("Sher Holmes", avatar_key_for("", "Sher Holmes"))
        assertEquals("?", avatar_key_for("", ""))
    }
}
