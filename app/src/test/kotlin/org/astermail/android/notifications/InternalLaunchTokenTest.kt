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

package org.astermail.android.notifications

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalLaunchTokenTest {

    private val token = internal_launch_token.new_token()

    @Test
    fun own_token_is_accepted() {
        assertTrue(internal_launch_token.matches(token, token))
        assertTrue(internal_launch_token.matches(token, String(token.toCharArray())))
    }

    @Test
    fun missing_token_is_rejected() {
        assertFalse(internal_launch_token.matches(token, null))
        assertFalse(internal_launch_token.matches(token, ""))
        assertFalse(internal_launch_token.matches(token, "   "))
    }

    @Test
    fun wrong_token_is_rejected() {
        assertFalse(internal_launch_token.matches(token, internal_launch_token.new_token()))
        assertFalse(internal_launch_token.matches(token, token.dropLast(1)))
        assertFalse(internal_launch_token.matches(token, token + "0"))
        assertFalse(internal_launch_token.matches(token, token.uppercase()))
    }

    @Test
    fun blank_expected_token_accepts_nothing() {
        assertFalse(internal_launch_token.matches("", ""))
        assertFalse(internal_launch_token.matches("", "anything"))
    }

    @Test
    fun new_tokens_are_long_random_hex() {
        val first = internal_launch_token.new_token(SecureRandom())
        val second = internal_launch_token.new_token(SecureRandom())
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(first, second)
    }
}
