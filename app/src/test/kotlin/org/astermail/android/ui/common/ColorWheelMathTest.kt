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

package org.astermail.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorWheelMathTest {

    @Test
    fun normalizes_six_and_three_digit_hex_to_lowercase() {
        assertEquals("#3b82f6", normalize_hex_color("#3B82F6"))
        assertEquals("#3b82f6", normalize_hex_color("3b82f6"))
        assertEquals("#aabbcc", normalize_hex_color("#ABC"))
        assertEquals("#112233", normalize_hex_color(" 123 "))
    }

    @Test
    fun rejects_invalid_hex() {
        assertNull(normalize_hex_color(null))
        assertNull(normalize_hex_color(""))
        assertNull(normalize_hex_color("#12"))
        assertNull(normalize_hex_color("#1234"))
        assertNull(normalize_hex_color("#gggggg"))
        assertNull(normalize_hex_color("#3b82f6ff"))
        assertNull(normalize_hex_color("blue"))
    }

    @Test
    fun primary_colors_convert_both_ways() {
        assertEquals("#ff0000", hsv_to_hex(hsv_color(0f, 1f, 1f)))
        assertEquals("#00ff00", hsv_to_hex(hsv_color(120f, 1f, 1f)))
        assertEquals("#0000ff", hsv_to_hex(hsv_color(240f, 1f, 1f)))
        assertEquals("#ffffff", hsv_to_hex(hsv_color(200f, 0f, 1f)))
        assertEquals("#000000", hsv_to_hex(hsv_color(200f, 1f, 0f)))
        val blue = hex_to_hsv("#0000ff")!!
        assertEquals(240f, blue.hue, 0.001f)
        assertEquals(1f, blue.saturation, 0.001f)
        assertEquals(1f, blue.value, 0.001f)
    }

    @Test
    fun hue_wraps_around_full_circle() {
        assertEquals("#ff0000", hsv_to_hex(hsv_color(360f, 1f, 1f)))
        assertEquals("#ff0000", hsv_to_hex(hsv_color(-360f, 1f, 1f)))
    }

    @Test
    fun every_hex_survives_hsv_round_trip() {
        var r = 0
        while (r <= 255) {
            var g = 0
            while (g <= 255) {
                var b = 0
                while (b <= 255) {
                    val rgb = (r shl 16) or (g shl 8) or b
                    val hex = rgb_to_hex(rgb)
                    assertEquals(hex, hsv_to_hex(hex_to_hsv(hex)!!))
                    b += 5
                }
                g += 5
            }
            r += 5
        }
    }

    @Test
    fun custom_hex_survives_round_trip_unchanged() {
        listOf("#123456", "#a1b2c3", "#fe01dc", "#3b82f7").forEach { hex ->
            assertEquals(hex, hsv_to_hex(hex_to_hsv(hex)!!))
            assertEquals(hex, normalize_hex_color(hex))
        }
    }

    @Test
    fun wheel_position_maps_to_hue_and_saturation() {
        val (h0, s0) = wheel_hue_saturation(100f, 0f, 100f)
        assertEquals(0f, h0, 0.001f)
        assertEquals(1f, s0, 0.001f)
        val (h90, s90) = wheel_hue_saturation(0f, 50f, 100f)
        assertEquals(90f, h90, 0.001f)
        assertEquals(0.5f, s90, 0.001f)
        val (h270, _) = wheel_hue_saturation(0f, -10f, 100f)
        assertEquals(270f, h270, 0.001f)
        val (_, clamped) = wheel_hue_saturation(300f, 400f, 100f)
        assertEquals(1f, clamped, 0.001f)
    }

    @Test
    fun wheel_offset_inverts_wheel_position() {
        listOf(0f, 45f, 135f, 210f, 330f).forEach { hue ->
            val (dx, dy) = wheel_offset(hue, 0.7f, 120f)
            val (h, s) = wheel_hue_saturation(dx, dy, 120f)
            assertEquals(hue, h, 0.01f)
            assertEquals(0.7f, s, 0.001f)
        }
    }

    @Test
    fun custom_detection_ignores_case_and_short_form() {
        val presets = listOf("#EF4444", "#3b82f6")
        assertFalse(is_custom_color("#ef4444", presets))
        assertFalse(is_custom_color("#3B82F6", presets))
        assertTrue(is_custom_color("#123456", presets))
        assertFalse(is_custom_color(null, presets))
        assertFalse(is_custom_color("not a color", presets))
    }

    @Test
    fun text_color_follows_luminance() {
        assertTrue(prefers_dark_text(0xFFFFFF))
        assertTrue(prefers_dark_text(0xEAB308))
        assertFalse(prefers_dark_text(0x000000))
        assertFalse(prefers_dark_text(0x1E3A8A))
        assertEquals(21.0, contrast_ratio(0xFFFFFF, 0x000000), 0.01)
    }
}
