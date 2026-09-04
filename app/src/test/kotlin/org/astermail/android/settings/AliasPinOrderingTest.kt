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

import org.astermail.android.api.settings.AliasInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasPinOrderingTest {

    private fun alias(id: String, pinned: Boolean = false): AliasInfo =
        AliasInfo(id = id, encrypted_local_part = id, domain = "astermail.org", is_pinned = pinned)

    @Test
    fun pinning_moves_the_alias_to_the_top() {
        val aliases = listOf(alias("a"), alias("b"), alias("c"))

        val result = apply_alias_pin(aliases, "c", true)

        assertEquals(listOf("c", "a", "b"), result.map { it.id })
        assertTrue(result.first().is_pinned)
    }

    @Test
    fun unpinning_restores_the_original_order() {
        val aliases = listOf(alias("c", pinned = true), alias("a"), alias("b"))

        val result = apply_alias_pin(aliases, "c", false)

        assertEquals(listOf("c", "a", "b"), result.map { it.id })
        assertEquals(false, result.first().is_pinned)
    }

    @Test
    fun pinning_keeps_the_relative_order_of_other_pinned_aliases() {
        val aliases = listOf(alias("a", pinned = true), alias("b"), alias("c"))

        val result = apply_alias_pin(aliases, "c", true)

        assertEquals(listOf("a", "c", "b"), result.map { it.id })
    }

    @Test
    fun an_unknown_alias_id_leaves_the_list_untouched() {
        val aliases = listOf(alias("a", pinned = true), alias("b"))

        val result = apply_alias_pin(aliases, "missing", true)

        assertEquals(aliases.map { it.id }, result.map { it.id })
        assertEquals(listOf(true, false), result.map { it.is_pinned })
    }
}
