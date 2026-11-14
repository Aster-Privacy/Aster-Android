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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDatePatternTest {

    @After
    fun reset() {
        AsterTimePreferences.set_account_date_format(null)
    }

    @Test
    fun `iso format keeps the year on dates from this year`() {
        AsterTimePreferences.set_account_date_format("YYYY-MM-DD")
        assertEquals("yyyy-MM-dd", account_date_pattern("MMMd"))
        assertEquals("yyyy-MM-dd", account_date_pattern("yMMMd"))
        assertEquals("yyyy-MM-dd HH:mm", account_date_pattern("MMMdHm"))
        assertEquals("yyyy-MM-dd h:mm a", account_date_pattern("yMMMdhm"))
    }

    @Test
    fun `day first format drops the year only for this year`() {
        AsterTimePreferences.set_account_date_format("DD/MM/YYYY")
        assertEquals("dd/MM", account_date_pattern("MMMd"))
        assertEquals("dd/MM/yyyy", account_date_pattern("yMMMd"))
    }

    @Test
    fun `month first format drops the year only for this year`() {
        AsterTimePreferences.set_account_date_format("MM/DD/YYYY")
        assertEquals("MM/dd", account_date_pattern("MMMd"))
        assertEquals("MM/dd/yyyy", account_date_pattern("yMMMd"))
    }

    @Test
    fun `no preference and time only skeletons use the device pattern`() {
        assertNull(account_date_pattern("MMMd"))
        AsterTimePreferences.set_account_date_format("YYYY-MM-DD")
        assertNull(account_date_pattern("hm"))
        assertNull(account_date_pattern("EEE"))
    }
}
