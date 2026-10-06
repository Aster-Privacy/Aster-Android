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

package org.astermail.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class tree_indent_test {

    @Test
    fun indent_depth_is_clamped_to_the_visual_cap() {
        assertEquals(0, indent_depth(-1))
        assertEquals(0, indent_depth(0))
        assertEquals(3, indent_depth(3))
        assertEquals(max_indent_depth, indent_depth(max_indent_depth))
        assertEquals(max_indent_depth, indent_depth(9))
    }

    @Test
    fun indent_trail_keeps_the_nearest_ancestors() {
        val trail = listOf(true, false, true, false, true, false, true, true, false)
        assertEquals(listOf(true, false), indent_trail(listOf(true, false), 2))
        assertEquals(trail.takeLast(max_indent_depth), indent_trail(trail, trail.size))
        assertEquals(emptyList<Boolean>(), indent_trail(emptyList(), 0))
    }
}
