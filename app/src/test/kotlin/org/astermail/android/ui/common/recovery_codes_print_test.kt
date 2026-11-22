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
import org.junit.Test

class recovery_codes_print_test {

    @Test
    fun all_blocks_fit_on_one_page() {
        val pages = paginate_blocks(List(14) { 20f }, 700f)
        assertEquals(listOf((0 until 14).toList()), pages)
    }

    @Test
    fun overflow_moves_to_a_new_page_without_losing_blocks() {
        val pages = paginate_blocks(listOf(40f, 40f, 40f, 40f, 40f), 100f)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), pages)
        assertEquals((0 until 5).toList(), pages.flatten())
    }

    @Test
    fun a_block_taller_than_the_page_still_gets_its_own_page() {
        val pages = paginate_blocks(listOf(10f, 500f, 10f), 100f)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), pages)
    }

    @Test
    fun no_blocks_still_produce_one_page() {
        assertEquals(listOf(emptyList<Int>()), paginate_blocks(emptyList(), 100f))
    }
}
