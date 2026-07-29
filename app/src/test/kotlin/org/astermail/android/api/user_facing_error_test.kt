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

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class user_facing_error_test {

    @Test
    fun `api error detail is surfaced`() {
        val text = user_facing_error(ApiError.UnknownError("mailbox is full"), "fallback")
        assertEquals("mailbox is full", text)
    }

    @Test
    fun `internal exception message is not surfaced`() {
        val text = user_facing_error(
            java.io.IOException("failed to open /data/user/0/org.astermail.android/files/db"),
            "fallback",
        )
        assertEquals("fallback", text)
    }

    @Test
    fun `blank api detail falls back`() {
        val text = user_facing_error(ApiError.UnknownError("   "), "fallback")
        assertEquals("fallback", text)
    }

    @Test
    fun `cancellation is rethrown`() {
        assertThrows(CancellationException::class.java) {
            user_facing_error(CancellationException("StandaloneCoroutine was cancelled"), "fallback")
        }
    }
}
