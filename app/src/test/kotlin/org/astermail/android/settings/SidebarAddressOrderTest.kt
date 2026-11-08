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

package org.astermail.android.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarAddressOrderTest {

    private fun entry(id: String, order: Int? = null, hidden: Boolean = false) =
        sidebar_address_entry(item = id, sidebar_order = order, sidebar_hidden = hidden)

    @Test
    fun ordered_entries_come_first_in_sidebar_order() {
        val result = order_sidebar_addresses(
            listOf(entry("a"), entry("b", order = 1), entry("c", order = 0), entry("d")),
        )

        assertEquals(listOf("c", "b", "a", "d"), result)
    }

    @Test
    fun unordered_entries_keep_their_original_order() {
        val result = order_sidebar_addresses(listOf(entry("x"), entry("y"), entry("z")))

        assertEquals(listOf("x", "y", "z"), result)
    }

    @Test
    fun hidden_entries_are_left_out() {
        val result = order_sidebar_addresses(
            listOf(entry("a", order = 0), entry("b", order = 1, hidden = true), entry("c")),
        )

        assertEquals(listOf("a", "c"), result)
    }
}
