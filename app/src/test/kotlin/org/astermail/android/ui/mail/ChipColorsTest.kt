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

class ChipColorsTest {

    private val label_palette = listOf(
        Color(0xFFEA4335),
        Color(0xFF34A853),
        Color(0xFF4285F4),
        Color(0xFFFBBC04),
        Color(0xFFA855F7),
        Color(0xFF0EA5E9),
        Color(0xFF6B7280),
        Color(0xFF111111),
        Color(0xFFFFFFFF),
    )

    private val dark_surfaces = listOf(
        Color(0xFF121212),
        Color(0xFF1F1B1D),
        Color(0xFF2A0E14),
    )

    private val light_surfaces = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF1F3F4),
        Color(0xFFEDE7E9),
    )

    @Test
    fun every_label_color_is_readable_on_dark_surfaces() {
        for (surface in dark_surfaces) {
            for (label in label_palette) {
                val background = chip_background(label, surface, is_dark = true)
                val content = chip_content(label, background, is_dark = true)
                val ratio = contrast_ratio(content, background)
                assertTrue(
                    "label=$label surface=$surface ratio=$ratio",
                    ratio >= chip_min_contrast,
                )
            }
        }
    }

    @Test
    fun every_label_color_is_readable_on_light_surfaces() {
        for (surface in light_surfaces) {
            for (label in label_palette) {
                val background = chip_background(label, surface, is_dark = false)
                val content = chip_content(label, background, is_dark = false)
                val ratio = contrast_ratio(content, background)
                assertTrue(
                    "label=$label surface=$surface ratio=$ratio",
                    ratio >= chip_min_contrast,
                )
            }
        }
    }

    @Test
    fun chip_background_stays_close_to_the_surface() {
        val surface = Color(0xFF121212)
        val background = chip_background(Color(0xFFEA4335), surface, is_dark = true)
        assertTrue(contrast_ratio(background, surface) < 2.0)
    }

    @Test
    fun chip_background_is_fully_opaque() {
        val background = chip_background(Color(0xFF4285F4), Color(0xFFFFFFFF), is_dark = false)
        assertEquals(1f, background.alpha, 0.0001f)
    }

    @Test
    fun distinct_labels_produce_distinct_backgrounds() {
        val surface = Color(0xFF121212)
        val red = chip_background(Color(0xFFEA4335), surface, is_dark = true)
        val blue = chip_background(Color(0xFF4285F4), surface, is_dark = true)
        assertTrue(red != blue)
    }

    @Test
    fun white_on_white_still_reaches_contrast() {
        val background = chip_background(Color.White, Color.White, is_dark = false)
        val content = chip_content(Color.White, background, is_dark = false)
        assertTrue(contrast_ratio(content, background) >= chip_min_contrast)
    }

    @Test
    fun black_on_black_still_reaches_contrast() {
        val background = chip_background(Color.Black, Color.Black, is_dark = true)
        val content = chip_content(Color.Black, background, is_dark = true)
        assertTrue(contrast_ratio(content, background) >= chip_min_contrast)
    }

    @Test
    fun contrast_ratio_is_symmetric() {
        val a = Color(0xFF123456)
        val b = Color(0xFFABCDEF)
        assertEquals(contrast_ratio(a, b), contrast_ratio(b, a), 0.0001)
    }

    @Test
    fun white_against_black_is_the_maximum_ratio() {
        assertEquals(21.0, contrast_ratio(Color.White, Color.Black), 0.01)
    }
}
