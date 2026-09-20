//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.design

import androidx.compose.ui.graphics.Color
import org.astermail.android.design.components.aster_menu_border_color
import org.astermail.android.design.components.aster_menu_surface_color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class aster_menu_colors_test {

    @Test
    fun surface_is_opaque_when_the_theme_color_is_translucent() {
        val translucent = Color(0x40101820)
        assertEquals(1f, aster_menu_surface_color(translucent, is_dark = true).alpha, 0f)
        assertEquals(1f, aster_menu_surface_color(translucent, is_dark = false).alpha, 0f)
    }

    @Test
    fun border_is_opaque_on_every_theme() {
        val translucent = Color(0x40101820)
        assertEquals(1f, aster_menu_border_color(translucent, is_dark = true).alpha, 0f)
        assertEquals(1f, aster_menu_border_color(Color.White, is_dark = false).alpha, 0f)
    }

    @Test
    fun amoled_surface_lifts_above_pure_black() {
        val surface = aster_menu_surface_color(Color.Black, is_dark = true)
        assertTrue(surface.red > 0f)
        assertNotEquals(Color.Black, surface)
    }

    @Test
    fun light_surface_keeps_the_theme_color() {
        assertEquals(Color.White, aster_menu_surface_color(Color.White, is_dark = false))
    }

    @Test
    fun border_reads_against_the_surface() {
        val surface = aster_menu_surface_color(Color.Black, is_dark = true)
        val border = aster_menu_border_color(Color.Black, is_dark = true)
        assertTrue(border.red > surface.red)
    }
}
