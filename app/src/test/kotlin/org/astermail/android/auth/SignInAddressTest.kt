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

import org.junit.Assert.assertEquals
import org.junit.Test

class SignInAddressTest {

    @Test
    fun `bare username tries the picked domain first and then the other one`() {
        assertEquals(
            listOf("robin@astermail.org", "robin@aster.cx"),
            SignInAddress.candidates("robin", "astermail.org", domain_explicit = false),
        )
        assertEquals(
            listOf("robin@aster.cx", "robin@astermail.org"),
            SignInAddress.candidates("robin", "aster.cx", domain_explicit = false),
        )
    }

    @Test
    fun `chosen domain is the only candidate`() {
        assertEquals(
            listOf("robin@aster.cx"),
            SignInAddress.candidates("robin", "aster.cx", domain_explicit = true),
        )
    }

    @Test
    fun `typed address keeps its domain`() {
        assertEquals(
            listOf("robin@aster.cx"),
            SignInAddress.candidates(" Robin@Aster.cx ", "astermail.org", domain_explicit = false),
        )
    }

    @Test
    fun `username is cleaned the same way as on the web`() {
        assertEquals("robin.s_1", SignInAddress.local_part(" Robin.S_1\u200B "))
        assertEquals("robin", SignInAddress.local_part("robin\u00A0"))
        assertEquals("a".repeat(40), SignInAddress.local_part("a".repeat(45)))
    }

    @Test
    fun `long passwords are cut to the length the web keeps`() {
        val long = "p".repeat(130)
        assertEquals("p".repeat(128), SignInAddress.clamp_password(long))
        assertEquals("short", SignInAddress.clamp_password("short"))
        val split_pair = "p".repeat(127) + "\uD83D\uDE00"
        assertEquals("p".repeat(127) + "\uFFFD", SignInAddress.clamp_password(split_pair))
    }
}
