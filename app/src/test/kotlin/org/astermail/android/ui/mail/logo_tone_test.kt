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

class logo_tone_test {
    private val transparent = 0x00000000
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val mid_gray = 0xFF8C8C8C.toInt()

    private fun mark(color: Int, mark_pixels: Int = 64, total: Int = 256): IntArray =
        IntArray(total) { index -> if (index < mark_pixels) color else transparent }

    @Test
    fun `classifies a dark mark on a transparent background as dark`() {
        assertEquals(LogoTone.dark, classify_logo_pixels(mark(black)))
        assertEquals(LogoTone.dark, classify_logo_pixels(mark(0xFF1F1F1F.toInt())))
    }

    @Test
    fun `classifies a light mark on a transparent background as light`() {
        assertEquals(LogoTone.light, classify_logo_pixels(mark(white)))
        assertEquals(LogoTone.light, classify_logo_pixels(mark(0xFFF0F0F0.toInt())))
    }

    @Test
    fun `leaves a full bleed opaque icon alone whatever its colour`() {
        assertEquals(LogoTone.none, classify_logo_pixels(IntArray(256) { white }))
        assertEquals(LogoTone.none, classify_logo_pixels(IntArray(256) { black }))
        assertEquals(LogoTone.none, classify_logo_pixels(IntArray(256) { index -> if (index < 240) white else transparent }))
    }

    @Test
    fun `leaves a mark that mixes dark and light alone`() {
        val pixels = IntArray(256) { index ->
            when {
                index < 50 -> black
                index < 100 -> white
                else -> transparent
            }
        }
        assertEquals(LogoTone.none, classify_logo_pixels(pixels))
    }

    @Test
    fun `leaves a mid tone mark alone`() {
        assertEquals(LogoTone.none, classify_logo_pixels(mark(mid_gray)))
    }

    @Test
    fun `leaves an empty or fully transparent logo alone`() {
        assertEquals(LogoTone.none, classify_logo_pixels(IntArray(0)))
        assertEquals(LogoTone.none, classify_logo_pixels(IntArray(256) { transparent }))
    }

    @Test
    fun `weights each pixel by its alpha`() {
        val faint_white = 0x10FFFFFF
        val pixels = IntArray(256) { index ->
            when {
                index < 64 -> black
                index < 128 -> faint_white
                else -> transparent
            }
        }
        assertEquals(LogoTone.dark, classify_logo_pixels(pixels))
    }

    @Test
    fun `puts a light circle behind a dark mark only in the dark theme`() {
        assertEquals(logo_light_backdrop, logo_contrast_backdrop(LogoTone.dark, dark_theme = true))
        assertNull(logo_contrast_backdrop(LogoTone.dark, dark_theme = false))
    }

    @Test
    fun `puts a dark circle behind a light mark only in the light theme`() {
        assertEquals(logo_dark_backdrop, logo_contrast_backdrop(LogoTone.light, dark_theme = false))
        assertNull(logo_contrast_backdrop(LogoTone.light, dark_theme = true))
    }

    @Test
    fun `gives no contrast circle to opaque, mid tone or unread logos`() {
        assertNull(logo_contrast_backdrop(LogoTone.none, dark_theme = true))
        assertNull(logo_contrast_backdrop(LogoTone.none, dark_theme = false))
        assertNull(logo_contrast_backdrop(null, dark_theme = true))
        assertNull(logo_contrast_backdrop(null, dark_theme = false))
    }

    @Test
    fun `keeps the white sender logo backdrop for dark marks in the light theme and unread logos`() {
        assertEquals(Color.White, sender_logo_backdrop(LogoTone.dark, dark_theme = false))
        assertEquals(Color.White, sender_logo_backdrop(null, dark_theme = false))
        assertEquals(Color.White, sender_logo_backdrop(null, dark_theme = true))
    }

    @Test
    fun `swaps the white sender logo backdrop for a contrasting or clear one`() {
        assertEquals(logo_light_backdrop, sender_logo_backdrop(LogoTone.dark, dark_theme = true))
        assertEquals(logo_dark_backdrop, sender_logo_backdrop(LogoTone.light, dark_theme = false))
        assertEquals(Color.Transparent, sender_logo_backdrop(LogoTone.light, dark_theme = true))
        assertEquals(Color.Transparent, sender_logo_backdrop(LogoTone.none, dark_theme = false))
        assertEquals(Color.Transparent, sender_logo_backdrop(LogoTone.none, dark_theme = true))
    }
}
