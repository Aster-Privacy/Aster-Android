// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressCopyTest {

    @Test
    fun `a display name wrapper is dropped`() {
        assertEquals(
            "offers@thehindu.hindugroup.org.in",
            copyable_email_address("The Hindu <offers@thehindu.hindugroup.org.in>"),
        )
    }

    @Test
    fun `a bare address is returned as is`() {
        assertEquals(
            "help@thehindu.hindugroup.org.in",
            copyable_email_address("  help@thehindu.hindugroup.org.in  "),
        )
    }

    @Test
    fun `every address in a list is kept`() {
        assertEquals(
            "one@aster.cx, two@aster.cx",
            copyable_email_address("One <one@aster.cx>, Two <two@aster.cx>"),
        )
        assertEquals(
            "one@aster.cx, two@aster.cx",
            copyable_email_address("one@aster.cx, two@aster.cx"),
        )
    }

    @Test
    fun `a quoted display name is stripped from a bare list`() {
        assertEquals("one@aster.cx", copyable_email_address("\"One\", one@aster.cx"))
    }

    @Test
    fun `a value without an address is left untouched`() {
        assertEquals("Undisclosed recipients", copyable_email_address("Undisclosed recipients"))
    }
}
