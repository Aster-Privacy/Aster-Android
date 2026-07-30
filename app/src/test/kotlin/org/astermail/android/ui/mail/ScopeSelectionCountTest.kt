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

import org.junit.Assert.assertEquals
import org.junit.Test

class ScopeSelectionCountTest {

    @Test
    fun `scope selection reports the folder total when it exceeds the loaded page`() {
        assertEquals(2000, scope_selection_count(true, 2000, 100))
    }

    @Test
    fun `unknown folder total falls back to the loaded selection`() {
        assertEquals(100, scope_selection_count(true, -1, 100))
    }

    @Test
    fun `stale folder total below the loaded page falls back to the loaded selection`() {
        assertEquals(100, scope_selection_count(true, 40, 100))
    }

    @Test
    fun `without scope selection only the loaded selection counts`() {
        assertEquals(100, scope_selection_count(false, 2000, 100))
    }

    @Test
    fun `empty selection stays zero`() {
        assertEquals(0, scope_selection_count(false, 2000, 0))
    }
}
