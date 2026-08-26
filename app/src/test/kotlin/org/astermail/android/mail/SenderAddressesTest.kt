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
package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderAddressesTest {
    @Test
    fun `an alias whose local part failed to decrypt is not sendable`() {
        assertFalse(is_sendable_address("@astermail.org"))
    }

    @Test
    fun `an address without a domain is not sendable`() {
        assertFalse(is_sendable_address("someone@"))
    }

    @Test
    fun `a value that is not an address is not sendable`() {
        assertFalse(is_sendable_address("someone"))
        assertFalse(is_sendable_address(""))
        assertFalse(is_sendable_address(null))
    }

    @Test
    fun `a normal address is sendable and trimmed`() {
        assertEquals("you@astermail.org", sanitize_sender_address("  you@astermail.org \n"))
        assertTrue(is_sendable_address("you@astermail.org"))
    }
}
