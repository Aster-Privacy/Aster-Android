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

package org.astermail.android.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailDotNormalizationTest {

    @Test
    fun `strips dots from the local part only`() {
        assertEquals(
            "johnsmith@astermail.org",
            normalize_address_ignoring_dots("john.smith@astermail.org"),
        )
    }

    @Test
    fun `keeps dots in the domain`() {
        assertEquals(
            "johnsmith@aster.cx",
            normalize_address_ignoring_dots("John.Smith@Aster.cx"),
        )
    }

    @Test
    fun `handles a bare local part`() {
        assertEquals(
            "johnsmith",
            normalize_address_ignoring_dots("john.smith"),
        )
    }

    @Test
    fun `splits on the last at sign`() {
        assertEquals(
            "ab@cd@example.com",
            normalize_address_ignoring_dots("a.b@c.d@example.com"),
        )
    }

    @Test
    fun `treats dotted and dotless spellings as one address`() {
        assertEquals(
            normalize_address_ignoring_dots("johnsmith@astermail.org"),
            normalize_address_ignoring_dots("j.o.h.n.smith@astermail.org"),
        )
    }
}
