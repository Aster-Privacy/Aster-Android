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

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimEmailRunonTest {

    @Test
    fun `strips a run-on header word from the tld`() {
        assertEquals("someone@aster.cx", trim_email_runon("someone@aster.cxDate"))
        assertEquals("someone@example.com", trim_email_runon("someone@example.comDate"))
        assertEquals("someone@example.com", trim_email_runon("someone@example.comSubject"))
        assertEquals("someone@example.com", trim_email_runon("someone@example.comTo"))
    }

    @Test
    fun `leaves ordinary addresses untouched`() {
        assertEquals("someone@example.com", trim_email_runon("someone@example.com"))
        assertEquals("first.last+tag@sub.example.co.uk", trim_email_runon("first.last+tag@sub.example.co.uk"))
        assertEquals("user@aster.cx", trim_email_runon("user@aster.cx"))
    }

    @Test
    fun `leaves uppercase and mixed-case tlds untouched`() {
        assertEquals("user@example.COM", trim_email_runon("user@example.COM"))
        assertEquals("user@EXAMPLE.COM", trim_email_runon("user@EXAMPLE.COM"))
    }

    @Test
    fun `caps an absurdly long tld`() {
        val absurd = "user@example." + "a".repeat(60)
        assertEquals("user@example." + "a".repeat(24), trim_email_runon(absurd))
    }

    @Test
    fun `returns input unchanged when there is no at sign or dot`() {
        assertEquals("notanaddress", trim_email_runon("notanaddress"))
        assertEquals("user@localhost", trim_email_runon("user@localhost"))
    }
}
