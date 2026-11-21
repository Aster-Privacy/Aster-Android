//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardCodeScreenStateTest {
    private val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun codes_follow_the_vector_steps() {
        assertEquals("GG5F5", guard_code_state(secret, 0).code)
        assertEquals("PV9M4", guard_code_state(secret, 30).code)
        assertEquals("QTVBC", guard_code_state(secret, 1770).code)
    }

    @Test
    fun seconds_left_counts_down() {
        assertEquals(30, guard_code_state(secret, 0).seconds_left)
        assertEquals(1, guard_code_state(secret, 29).seconds_left)
    }

    @Test
    fun present_secret_is_not_missing() {
        assertFalse(guard_code_state(secret, 0).missing)
    }

    @Test
    fun null_secret_is_missing() {
        val view = guard_code_state(null, 0)
        assertTrue(view.missing)
        assertNull(view.code)
    }

    @Test
    fun empty_secret_is_missing() {
        assertTrue(guard_code_state(ByteArray(0), 0).missing)
    }
}
