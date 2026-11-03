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
package org.astermail.android.ui.mail

import org.astermail.android.mail.split_display_address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class sender_logo_domain_test {
    @Test
    fun `extracts the domain from a plain address`() {
        assertEquals("ezpde.com", extract_domain("billing@EZPDE.com"))
    }

    @Test
    fun `drops a trailing angle bracket from the domain`() {
        assertEquals("ezpde.com", extract_domain("<billing@ezpde.com>"))
        assertEquals("ezpde.com", extract_domain("Billing <billing@ezpde.com>"))
        assertEquals("sammohr.dev", extract_domain("\"Sam\" <sam@sammohr.dev> "))
    }

    @Test
    fun `returns no domain when it is not a valid host`() {
        assertEquals("", extract_domain("billing@"))
        assertEquals("", extract_domain("no-address"))
        assertEquals("", extract_domain("billing@localhost"))
        assertEquals("", extract_domain("billing@ezpde.com/x"))
    }

    @Test
    fun `validates favicon domains`() {
        assertTrue(is_valid_favicon_domain("mail.example.co.uk"))
        assertFalse(is_valid_favicon_domain("example.com>"))
        assertFalse(is_valid_favicon_domain("-example.com"))
        assertFalse(is_valid_favicon_domain(""))
    }

    @Test
    fun `splits a bare angle address with no display name`() {
        assertEquals("" to "billing@ezpde.com", split_display_address("<billing@ezpde.com>"))
    }

    @Test
    fun `splits a named address`() {
        assertEquals("Sam Mohr" to "sam@sammohr.dev", split_display_address("\"Sam Mohr\" <sam@sammohr.dev>"))
    }

    @Test
    fun `leaves an address without angle brackets to the caller`() {
        assertNull(split_display_address("sam@sammohr.dev"))
        assertNull(split_display_address("Sam <sam@sammohr.dev"))
    }
}
