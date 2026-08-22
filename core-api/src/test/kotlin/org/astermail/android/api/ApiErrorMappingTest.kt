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

package org.astermail.android.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorMappingTest {

    @Test
    fun fingerprint_mismatch_surfaces_as_step_up_instead_of_sign_out() {
        val err = map_unauthorized("FINGERPRINT_MISMATCH", "device changed")
        assertTrue(err is ApiError.StepUpRequired)
        assertEquals("device changed", (err as ApiError.StepUpRequired).detail)
        assertFalse(should_emit_unauthorized("FINGERPRINT_MISMATCH"))
    }

    @Test
    fun invalid_credentials_do_not_sign_out_either() {
        assertEquals(ApiError.InvalidCredentials, map_unauthorized("INVALID_CREDENTIALS", ""))
        assertFalse(should_emit_unauthorized("INVALID_CREDENTIALS"))
    }

    @Test
    fun other_unauthorized_responses_still_sign_out() {
        assertEquals(ApiError.UnauthorizedError, map_unauthorized(null, ""))
        assertTrue(should_emit_unauthorized(null))
        assertTrue(should_emit_unauthorized("SESSION_EXPIRED"))
    }
}
