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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryAddressValidationTest {
    @Test
    fun accepts_addresses_the_server_accepts() {
        assertTrue(primary_local_part_valid("newname"))
        assertTrue(primary_local_part_valid("new.name"))
        assertTrue(primary_local_part_valid("a.b.c"))
        assertTrue(primary_local_part_valid("a".repeat(40)))
    }

    @Test
    fun rejects_addresses_the_server_rejects() {
        assertFalse(primary_local_part_valid("ab"))
        assertFalse(primary_local_part_valid(".lead"))
        assertFalse(primary_local_part_valid("trail."))
        assertFalse(primary_local_part_valid("two..dots"))
        assertFalse(primary_local_part_valid("up_score"))
        assertFalse(primary_local_part_valid("dash-ed"))
        assertFalse(primary_local_part_valid("Upper"))
        assertFalse(primary_local_part_valid("a".repeat(41)))
    }

    @Test
    fun rejects_a_typed_local_part_over_sixty_four_characters() {
        val at_cap = "a.".repeat(24) + "a".repeat(16)
        val over_cap = "a.".repeat(25) + "a".repeat(15)

        assertTrue(at_cap.length == 64)
        assertTrue(over_cap.length == 65)
        assertTrue(primary_local_part_valid(at_cap))
        assertFalse(primary_local_part_valid(over_cap))
    }
}
