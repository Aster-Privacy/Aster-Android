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

package org.astermail.android.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialThemeGeneratorTest {

    private data class Reference(
        val seed: String,
        val dark_accent: String,
        val dark_hover: String,
        val light_accent: String,
        val light_hover: String,
    )

    private val references = listOf(
        Reference("#3b82f6", "#4087fc", "#73a8fd", "#084198", "#00327d"),
        Reference("#ff0000", "#e95142", "#f97e6d", "#880f09", "#6f0101"),
        Reference("#10b981", "#18a573", "#2ac48b", "#11553b", "#05432d"),
        Reference("#8b5cf6", "#936ff3", "#ac93ff", "#5120a2", "#401286"),
        Reference("#f59e0b", "#bf7b14", "#e29524", "#623f0d", "#4f3003"),
        Reference("#000000", "#8c8c8c", "#a8a8a8", "#484848", "#383838"),
        Reference("#ffffff", "#8c8c8c", "#a8a8a8", "#484848", "#383838"),
    )

    @Test
    fun generate_material_theme_matches_ts_reference() {
        for (ref in references) {
            val dark = MaterialThemeGenerator.generate_material_theme(ref.seed, true)
            val light = MaterialThemeGenerator.generate_material_theme(ref.seed, false)
            assertEquals("dark accent for ${ref.seed}", ref.dark_accent, dark.accent_color)
            assertEquals("dark hover for ${ref.seed}", ref.dark_hover, dark.accent_color_hover)
            assertEquals("light accent for ${ref.seed}", ref.light_accent, light.accent_color)
            assertEquals("light hover for ${ref.seed}", ref.light_hover, light.accent_color_hover)
        }
    }

    @Test
    fun mix_hex_srgb_matches_ts_reference() {
        assertEquals("#76a8f9", MaterialThemeGenerator.mix_hex_srgb("#3b82f6", "#ffffff", 0.7))
    }

    @Test
    fun is_valid_hex_color_accepts_and_rejects() {
        assertTrue(MaterialThemeGenerator.is_valid_hex_color("#3b82f6"))
        assertTrue(MaterialThemeGenerator.is_valid_hex_color("#fff"))
        assertFalse(MaterialThemeGenerator.is_valid_hex_color("3b82f6"))
        assertFalse(MaterialThemeGenerator.is_valid_hex_color("#zzzzzz"))
        assertFalse(MaterialThemeGenerator.is_valid_hex_color(""))
    }
}
