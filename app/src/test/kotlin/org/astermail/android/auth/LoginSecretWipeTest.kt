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

package org.astermail.android.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSecretWipeTest {

    @Test
    fun `secrets are zeroed after a completed sign-in`() = runTest {
        val password = byteArrayOf(1, 2, 3)
        val proof = byteArrayOf(4, 5, 6)
        val seen = with_wiped_login_secrets(password, proof) { password.sum() + proof.sum() }
        assertEquals(21, seen)
        assertTrue(password.all { it == 0.toByte() })
        assertTrue(proof.all { it == 0.toByte() })
    }

    @Test
    fun `secrets are zeroed when the sign-in step throws`() = runTest {
        val password = byteArrayOf(7, 8, 9)
        val proof = byteArrayOf(10, 11, 12)
        val failure = runCatching {
            with_wiped_login_secrets(password, proof) { throw IllegalStateException("slot claim failed") }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(password.all { it == 0.toByte() })
        assertTrue(proof.all { it == 0.toByte() })
    }
}
