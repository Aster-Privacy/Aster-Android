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

package org.astermail.android.ui.icons

import androidx.compose.ui.graphics.vector.addPathNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasIconsTest {
    @Test
    fun every_path_parses() {
        for (d in ALIAS_AT_PATHS + GHOST_MASK_PATHS) {
            assertTrue(d, addPathNodes(d).isNotEmpty())
        }
    }

    @Test
    fun icons_have_their_expected_strokes() {
        assertEquals(2, ALIAS_AT_PATHS.size)
        assertEquals(6, GHOST_MASK_PATHS.size)
    }

    @Test
    fun alias_and_ghost_icons_differ() {
        assertNotEquals(ALIAS_AT_PATHS, GHOST_MASK_PATHS)
    }
}
