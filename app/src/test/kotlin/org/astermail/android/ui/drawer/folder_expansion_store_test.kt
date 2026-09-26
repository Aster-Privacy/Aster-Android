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

package org.astermail.android.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class folder_expansion_store_test {

    @Test
    fun keeps_valid_folder_tokens() {
        assertEquals(
            setOf("abc123", "Zm9v+/bar=", "a_b-c"),
            folder_expansion_store.sanitize(listOf("abc123", "Zm9v+/bar=", "a_b-c")),
        )
    }

    @Test
    fun drops_hostile_or_malformed_values() {
        assertEquals(
            setOf("news"),
            folder_expansion_store.sanitize(listOf("news", "<img src=x>", "", "a b", "x".repeat(129))),
        )
    }

    @Test
    fun treats_missing_values_as_empty() {
        assertEquals(emptySet<String>(), folder_expansion_store.sanitize(null))
    }

    @Test
    fun caps_the_number_of_tokens_and_keeps_the_newest() {
        val tokens = (0 until 600).map { "t$it" }
        val sanitized = folder_expansion_store.sanitize(tokens)
        assertEquals(500, sanitized.size)
        assertEquals("t599", sanitized.last())
        assertEquals("t100", sanitized.first())
    }
}
