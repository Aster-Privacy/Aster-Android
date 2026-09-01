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


package org.astermail.android

import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ServerCodeErrorTest {
    @Test
    fun maps_every_account_state_code_to_a_string_resource() {
        val codes = listOf(
            "ACCOUNT_SUSPENDED",
            "ACCOUNT_LOCKED",
            "ACCOUNT_PROBATION",
            "VERIFICATION_REQUIRED",
            "CLIENT_UPGRADE_REQUIRED",
            "ALIAS_REENCRYPTION_INCOMPLETE",
        )
        val seen = mutableSetOf<Int>()
        for (code in codes) {
            val res = server_code_string_res(code)
            assertNotNull(code, res)
            seen.add(res!!)
        }
        assertEquals(codes.size, seen.size)
    }

    @Test
    fun leaves_an_unknown_code_to_the_server_message() {
        assertNull(server_code_string_res("SOMETHING_ELSE"))
        assertNull(server_code_string_res(""))
    }

    @Test
    fun carries_the_server_code_on_forbidden_and_conflict() {
        assertEquals("ACCOUNT_SUSPENDED", ApiError.ForbiddenError("nope", "ACCOUNT_SUSPENDED").code)
        assertEquals("CLIENT_UPGRADE_REQUIRED", ApiError.Conflict("nope", "CLIENT_UPGRADE_REQUIRED").code)
        assertNull(ApiError.ForbiddenError("nope").code)
        assertNull(ApiError.Conflict("nope").code)
    }
}
