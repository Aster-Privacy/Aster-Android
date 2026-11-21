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

package org.astermail.android.crypto.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardCodeTest {
    private val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun code_vectors() {
        assertEquals("GG5F5", GuardCode.at_step(secret, 0))
        assertEquals("PV9M4", GuardCode.at_step(secret, 1))
        assertEquals("QTVBC", GuardCode.at_step(secret, 59))
        assertEquals("R87JJ", GuardCode.at_step(secret, 56666666))
        assertEquals("R87JJ", GuardCode.now(secret, 1700000000))
    }

    @Test
    fun seconds_left_counts_down_to_the_next_step() {
        assertEquals(30, GuardCode.seconds_left(60))
        assertEquals(1, GuardCode.seconds_left(89))
    }

    @Test
    fun rescue_normalizes_and_formats() {
        assertEquals("BCDFGHJKMN23456789BCDFGHJ", GuardCode.normalize_rescue("bcdfg-hjkmn 23456-789bc-dfghj"))
        assertEquals("BCDFG-HJKMN-23456-789BC-DFGHJ", GuardCode.format_rescue("BCDFGHJKMN23456789BCDFGHJ"))
    }

    @Test
    fun rescue_rejects_lookalike_characters_and_wrong_length() {
        assertNull(GuardCode.normalize_rescue("0CDFG-HJKMN-23456-789BC-DFGHJ"))
        assertNull(GuardCode.normalize_rescue("OCDFG-HJKMN-23456-789BC-DFGHJ"))
        assertNull(GuardCode.normalize_rescue("1CDFG-HJKMN-23456-789BC-DFGHJ"))
        assertNull(GuardCode.normalize_rescue("ICDFG-HJKMN-23456-789BC-DFGHJ"))
        assertNull(GuardCode.normalize_rescue("BCDFG-HJKMN-23456-789BC"))
    }
}
