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

package org.astermail.android.ui.theme

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomThemeImageScalingTest {

    private val target_w = 1080
    private val target_h = 2400
    private val budget = 8_000_000L

    private fun decoded_pixels(width: Int, height: Int): Long {
        val sample = theme_image_sample_size(width, height, target_w, target_h, budget)
        return (width.toLong() / sample) * (height.toLong() / sample)
    }

    @Test
    fun small_image_is_decoded_at_full_size() {
        assertEquals(1, theme_image_sample_size(1080, 2400, target_w, target_h, budget))
        assertEquals(1, theme_image_sample_size(900, 1600, target_w, target_h, budget))
    }

    @Test
    fun oversized_portrait_is_halved_while_it_still_covers_the_frame() {
        assertEquals(4, theme_image_sample_size(4320, 9600, target_w, target_h, budget))
    }

    @Test
    fun wide_panorama_below_frame_height_still_respects_the_pixel_budget() {
        assertTrue(decoded_pixels(20_000, 1_000) <= budget)
        assertTrue(decoded_pixels(1_000, 20_000) <= budget)
    }

    @Test
    fun every_allowed_source_shape_decodes_within_the_pixel_budget() {
        val sides = listOf(1, 320, 1080, 2400, 4000, 9000, 16_000, 20_000)
        for (w in sides) {
            for (h in sides) {
                if (w.toLong() * h > 120_000_000L) continue
                assertTrue("$w x $h", decoded_pixels(w, h) <= budget)
            }
        }
    }

    @Test
    fun sample_size_is_never_zero_or_negative_for_degenerate_input() {
        assertEquals(1, theme_image_sample_size(0, 0, target_w, target_h, budget))
        assertEquals(1, theme_image_sample_size(-5, 10, target_w, target_h, budget))
        assertEquals(1, theme_image_sample_size(10, 10, 0, 0, budget))
    }

    @Test
    fun cap_factor_leaves_frame_sized_sources_untouched() {
        assertEquals(1f, theme_image_cap_factor(1080, 2400, target_w, target_h, budget))
        assertEquals(1f, theme_image_cap_factor(540, 1200, target_w, target_h, budget))
    }

    @Test
    fun cap_factor_keeps_the_working_bitmap_within_budget() {
        val shapes = listOf(20_000 to 1_000, 10_000 to 3_000, 6_000 to 6_000, 1_000 to 20_000, 4_320 to 9_600)
        for ((w, h) in shapes) {
            val factor = theme_image_cap_factor(w, h, target_w, target_h, budget)
            val out_w = (w * factor).roundToInt().coerceAtLeast(1)
            val out_h = (h * factor).roundToInt().coerceAtLeast(1)
            assertTrue("$w x $h", out_w.toLong() * out_h <= budget + budget / 100)
        }
    }

    @Test
    fun cap_factor_is_never_negative() {
        assertTrue(theme_image_cap_factor(0, 0, target_w, target_h, budget) >= 0f)
        assertTrue(theme_image_cap_factor(20_000, 20_000, target_w, target_h, budget) > 0f)
    }
}
