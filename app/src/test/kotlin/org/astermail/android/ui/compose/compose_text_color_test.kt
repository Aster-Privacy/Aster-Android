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

package org.astermail.android.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class compose_text_color_test {
    private val dark_bg = 0xFF121212.toInt()
    private val light_bg = 0xFFFFFFFF.toInt()
    private val dark_readable = 0xFFE8E8E8.toInt()
    private val light_readable = 0xFF1A1A1A.toInt()

    @Test
    fun dark_color_on_dark_theme_uses_readable_color() {
        assertEquals(dark_readable, compose_display_text_color(0xFF1F2A44.toInt(), dark_bg, dark_readable))
        assertEquals(dark_readable, compose_display_text_color(0xFF000000.toInt(), dark_bg, dark_readable))
    }

    @Test
    fun dark_color_on_light_theme_is_kept() {
        assertEquals(0xFF1F2A44.toInt(), compose_display_text_color(0xFF1F2A44.toInt(), light_bg, light_readable))
    }

    @Test
    fun light_color_on_light_theme_uses_readable_color() {
        assertEquals(light_readable, compose_display_text_color(0xFFFFFFFF.toInt(), light_bg, light_readable))
        assertEquals(light_readable, compose_display_text_color(0xFFFFEB3B.toInt(), light_bg, light_readable))
    }

    @Test
    fun readable_accent_color_on_dark_theme_is_kept() {
        assertEquals(0xFFFFEB3B.toInt(), compose_display_text_color(0xFFFFEB3B.toInt(), dark_bg, dark_readable))
    }

}
