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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageThemeContrastTest {

    private val tints: List<Color> = listOf(
        0x000000, 0x07080C, 0x060A16, 0x0B0714, 0x050E0D, 0x100908, 0x0B0C0F,
        0x222222, 0x220000, 0x002200, 0x000022, 0x222200, 0x002222, 0x220022,
        0x051022, 0x0C2220, 0x22140F, 0x221C0E, 0x20221D, 0x130C22, 0x221F22,
        0x404040, 0x663300, 0x113366,
    ).map { Color(0xFF000000 or it.toLong()) } + Color.Unspecified

    private val palettes: List<Pair<String, ColorThemePalette?>> =
        ColorThemeId.entries.map { it.name to AsterColorThemes.palette_for(it) } +
            listOf("#3b82f6", "#ffff00", "#00ff00", "#ff0000", "#7c3aed", "#000000", "#ffffff", "#808080").map { seed ->
                "custom:$seed" to MaterialThemeGenerator.to_palette(
                    MaterialThemeGenerator.compute_custom_theme_vars(seed, true, emptyMap()),
                )
            }

    private fun surfaces(c: AsterSemanticColors): List<Pair<String, Color>> = listOf(
        "bg_primary" to c.bg_primary,
        "bg_secondary" to c.bg_secondary,
        "bg_tertiary" to c.bg_tertiary,
        "bg_hover" to c.bg_hover,
        "bg_selected" to c.bg_selected,
        "bg_card" to c.bg_card,
        "sidebar_bg" to c.sidebar_bg,
        "sidebar_hover" to c.sidebar_hover,
        "modal_bg" to c.modal_bg,
        "dropdown_bg" to c.dropdown_bg,
        "dropdown_hover" to c.dropdown_hover,
        "input_bg" to c.input_bg,
        "indicator_bg" to c.indicator_bg,
        "thread_card_bg" to c.thread_card_bg,
        "thread_card_bg_hover" to c.thread_card_bg_hover,
        "thread_header_bg" to c.thread_header_bg,
        "thread_content_bg" to c.thread_content_bg,
        "secondary_control_bg" to c.secondary_control_bg,
    )

    private fun body_text(c: AsterSemanticColors): List<Pair<String, Color>> = listOf(
        "text_primary" to c.text_primary,
        "text_secondary" to c.text_secondary,
        "text_tertiary" to c.text_tertiary,
        "text_muted" to c.text_muted,
        "accent_blue" to c.accent_blue,
        "accent_blue_hover" to c.accent_blue_hover,
        "danger" to c.danger,
        "warning" to c.warning,
        "success" to c.success,
        "info" to c.info,
    )

    private fun each_combination(block: (String, AsterSemanticColors) -> Unit) {
        for ((name, palette) in palettes) {
            for (tint in tints) {
                val colors = image_theme_colors(AsterColorThemes.semantic_colors_for(true, palette), tint)
                block("$name on $tint", colors)
            }
        }
    }

    @Test
    fun every_surface_and_border_is_opaque() {
        each_combination { label, colors ->
            for ((surface, color) in surfaces(colors)) {
                assertEquals("$label $surface alpha", 1f, color.alpha)
            }
            assertEquals("$label border_primary alpha", 1f, colors.border_primary.alpha)
            assertEquals("$label border_secondary alpha", 1f, colors.border_secondary.alpha)
        }
    }

    @Test
    fun body_text_and_accent_meet_4_5_on_every_surface() {
        each_combination { label, colors ->
            for ((fg_name, fg) in body_text(colors)) {
                for ((bg_name, bg) in surfaces(colors)) {
                    val ratio = contrast_ratio(fg, bg)
                    assertTrue("$label $fg_name on $bg_name is $ratio", ratio >= contrast_body_text)
                }
            }
        }
    }

    @Test
    fun icons_and_large_text_meet_3_on_every_surface() {
        each_combination { label, colors ->
            for ((bg_name, bg) in surfaces(colors)) {
                val ratio = contrast_ratio(colors.star, bg)
                assertTrue("$label star on $bg_name is $ratio", ratio >= contrast_large_text)
            }
        }
    }

    @Test
    fun text_on_accent_fills_meets_4_5() {
        each_combination { label, colors ->
            val on_accent = contrast_ratio(colors.on_accent, colors.accent_blue)
            val on_hover = contrast_ratio(colors.on_accent, colors.accent_blue_hover)
            val avatar = contrast_ratio(colors.avatar_text, colors.avatar_bg)
            assertTrue("$label on_accent is $on_accent", on_accent >= contrast_body_text)
            assertTrue("$label on_accent hover is $on_hover", on_hover >= contrast_body_text)
            assertTrue("$label avatar_text is $avatar", avatar >= contrast_body_text)
        }
    }

    @Test
    fun island_cards_stay_readable_over_the_brightest_possible_photo() {
        val veil_min_alpha = 0.24f
        each_combination { label, colors ->
            val island = island_surface_color(colors)
            val backdrop = mix_rgb(Color.White, colors.bg_primary, veil_min_alpha)
            val composite = mix_rgb(backdrop, island, island.alpha)
            for ((fg_name, fg) in body_text(colors).filter { it.first.startsWith("text_") }) {
                val ratio = contrast_ratio(fg, composite)
                assertTrue("$label $fg_name on island is $ratio", ratio >= contrast_body_text)
            }
        }
    }

    @Test
    fun ensure_contrast_keeps_passing_colors_and_fixes_failing_ones() {
        assertEquals(Color.White, ensure_contrast(Color.White, listOf(Color.Black), contrast_body_text))
        val background = Color(0xFF101010)
        val fixed = ensure_contrast(Color(0xFF202020), listOf(background), contrast_body_text)
        assertTrue(contrast_ratio(fixed, background) >= contrast_body_text)
    }
}
