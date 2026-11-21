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
package org.astermail.android.guard

import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardHeldOfTest {
    private val held = ApiError.GuardHeld("c1", "forwarding_change", "2026-11-14T11:00:00Z")

    @Test
    fun `a held error maps to its notice`() {
        val notice = guard_held_of(held)
        assertEquals("c1", notice?.id)
        assertEquals(guard_hold_kind.forwarding_change, notice?.kind)
    }

    @Test
    fun `a wrapped held error still maps`() {
        assertEquals("c1", guard_held_of(RuntimeException("x", held))?.id)
    }

    @Test
    fun `other errors and null map to nothing`() {
        assertNull(guard_held_of(ApiError.NotFoundError))
        assertNull(guard_held_of(RuntimeException("x")))
        assertNull(guard_held_of(null))
    }
}
