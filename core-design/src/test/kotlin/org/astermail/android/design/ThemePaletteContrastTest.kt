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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteContrastTest {

    private val strong_text_minimum = 7.0
    private val body_text_minimum = 4.5
    private val border_minimum = 1.5
    private val dark_surface_separation_minimum = 1.1
    private val brand_accent_ink_minimum = contrast_large_text

    private val custom_seeds = listOf(
        "#3b82f6", "#ffff00", "#00ff00", "#ff0000", "#7c3aed", "#000000", "#ffffff", "#808080",
    )

    private fun base_themes(): List<Pair<String, AsterSemanticColors>> {
        val named = ColorThemeId.entries.mapNotNull { id ->
            val palette = AsterColorThemes.palette_for(id) ?: return@mapNotNull null
            id.name to AsterColorThemes.semantic_colors_for(true, palette)
        }
        val defaults = listOf(
            "default_light" to AsterColorThemes.semantic_colors_for(false, null),
            "default_dark" to AsterColorThemes.semantic_colors_for(true, null),
        )
        val custom = custom_seeds.flatMap { seed ->
            listOf(true, false).map { dark ->
                val palette = MaterialThemeGenerator.to_palette(MaterialThemeGenerator.generate_material_theme(seed, dark))
                "custom_${seed}_${if (dark) "dark" else "light"}" to AsterColorThemes.semantic_colors_for(dark, palette)
            }
        }
        return defaults + named + custom
    }

    private fun all_themes(): List<Pair<String, AsterSemanticColors>> =
        base_themes().flatMap { (name, colors) ->
            listOf(name to colors, "${name}_high_contrast" to apply_high_contrast(colors))
        }

    private fun is_brand_accent_exception(name: String): Boolean =
        name == "default_dark" || name == "default_dark_high_contrast"

    private fun hex(color: Color): String =
        "#" + Integer.toHexString(color.toArgb()).padStart(8, '0').takeLast(6)

    private fun assert_min(label: String, fg: Color, bg: Color, minimum: Double, failures: MutableList<String>) {
        val ratio = contrast_ratio(fg.copy(alpha = 1f), bg.copy(alpha = 1f))
        if (ratio < minimum) failures += "$label ${hex(fg)} on ${hex(bg)} = ${"%.2f".format(ratio)} < $minimum"
    }

    @Test
    fun text_tokens_meet_minimums_on_every_surface() {
        val failures = mutableListOf<String>()
        all_themes().forEach { (name, colors) ->
            readable_text_surfaces(colors).forEachIndexed { index, surface ->
                assert_min("$name text_primary@$index", colors.text_primary, surface, strong_text_minimum, failures)
                assert_min("$name text_secondary@$index", colors.text_secondary, surface, strong_text_minimum, failures)
                assert_min("$name text_tertiary@$index", colors.text_tertiary, surface, body_text_minimum, failures)
                assert_min("$name text_muted@$index", colors.text_muted, surface, body_text_minimum, failures)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun on_accent_is_readable_on_accent_and_hover() {
        val failures = mutableListOf<String>()
        all_themes().forEach { (name, colors) ->
            val minimum = if (is_brand_accent_exception(name)) brand_accent_ink_minimum else body_text_minimum
            assert_min("$name on_accent", colors.on_accent, colors.accent_blue, minimum, failures)
            assert_min("$name on_accent_hover", colors.on_accent, colors.accent_blue_hover, minimum, failures)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun avatar_text_is_readable() {
        val failures = mutableListOf<String>()
        all_themes().forEach { (name, colors) ->
            assert_min("$name avatar_text", colors.avatar_text, colors.avatar_bg, body_text_minimum, failures)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun borders_and_fields_are_visible() {
        val failures = mutableListOf<String>()
        base_themes().forEach { (name, colors) ->
            assert_min("$name border_primary", colors.border_primary, colors.bg_primary, border_minimum, failures)
            assert_min("$name input_border", colors.input_border, colors.input_bg, border_minimum, failures)
            assert_min("$name thread_card_border", colors.thread_card_border, colors.thread_card_bg, border_minimum, failures)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun raised_surfaces_separate_from_the_page_in_dark_themes() {
        val failures = mutableListOf<String>()
        base_themes().filter { it.second.is_dark }.forEach { (name, colors) ->
            listOf(
                "bg_tertiary" to colors.bg_tertiary,
                "input_bg" to colors.input_bg,
                "thread_card_bg" to colors.thread_card_bg,
                "bg_hover" to colors.bg_hover,
                "bg_selected" to colors.bg_selected,
            ).forEach { (token, surface) ->
                assert_min("$name $token", surface, colors.bg_primary, dark_surface_separation_minimum, failures)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun high_contrast_never_lowers_muted_contrast() {
        val failures = mutableListOf<String>()
        base_themes().forEach { (name, colors) ->
            val boosted = apply_high_contrast(colors)
            val before = contrast_ratio(colors.text_muted, colors.bg_primary)
            val after = contrast_ratio(boosted.text_muted, boosted.bg_primary)
            if (after + 0.01 < before) failures += "$name high contrast muted ${"%.2f".format(after)} < ${"%.2f".format(before)}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
