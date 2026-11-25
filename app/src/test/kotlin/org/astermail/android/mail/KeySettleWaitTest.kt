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

package org.astermail.android.mail

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeySettleWaitTest {

    @Test
    fun a_decrypt_before_keys_load_waits_for_the_keys() = runTest {
        val start = currentTime
        val settled = await_keys_settled(
            settled = { currentTime - start >= 1_200L },
            locked = { false },
            timeout_ms = 8_000L,
        )
        assertTrue(settled)
        assertTrue(currentTime - start >= 1_200L)
    }

    @Test
    fun locked_keys_return_at_once() = runTest {
        val start = currentTime
        assertFalse(await_keys_settled(settled = { false }, locked = { true }, timeout_ms = 8_000L))
        assertEquals(start, currentTime)
    }

    @Test
    fun keys_that_never_load_give_up_at_the_timeout() = runTest {
        val start = currentTime
        assertFalse(await_keys_settled(settled = { false }, locked = { false }, timeout_ms = 8_000L))
        assertTrue(currentTime - start >= 8_000L)
    }
}
