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
package org.astermail.android.auth

import org.astermail.android.api.ApiError
import org.astermail.android.guard.guard_hold_kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryHeldTest {
    @Test
    fun `a held recovery returns to the password step with the hold`() {
        val start = RecoveryUiState(step = RecoveryStep.password, is_loading = true, processing_status = "x")
        val next = recovery_held_state(start, ApiError.GuardHeld("c1", "account_recovery", "2026-11-21T11:00:00Z"))

        assertEquals(guard_hold_kind.account_recovery, next?.held?.kind)
        assertEquals(RecoveryStep.password, next?.step)
        assertFalse(next!!.is_loading)
        assertNull(next.error)
    }

    @Test
    fun `other errors leave the state alone`() {
        assertNull(recovery_held_state(RecoveryUiState(), RuntimeException("x")))
    }
}
