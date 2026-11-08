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

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekStartTest {

    @Test
    fun maps_the_calendar_preference_values() {
        assertEquals(Calendar.SUNDAY, WeekStart.day_of_week("sunday"))
        assertEquals(Calendar.MONDAY, WeekStart.day_of_week("monday"))
        assertEquals(Calendar.SATURDAY, WeekStart.day_of_week(" Saturday "))
    }

    @Test
    fun unknown_values_fall_back_to_the_locale() {
        assertNull(WeekStart.day_of_week(null))
        assertNull(WeekStart.day_of_week(""))
        assertNull(WeekStart.day_of_week("tuesday"))
        assertEquals(Calendar.MONDAY, WeekStart.resolve(null, Calendar.MONDAY))
        assertEquals(Calendar.SATURDAY, WeekStart.resolve(Calendar.SATURDAY, Calendar.MONDAY))
    }

    @Test
    fun the_account_setting_drives_the_first_day_of_week() {
        try {
            AsterTimePreferences.set_account_week_start("saturday")
            assertEquals(Calendar.SATURDAY, AsterTimePreferences.first_day_of_week())
            AsterTimePreferences.set_account_week_start("monday")
            assertEquals(Calendar.MONDAY, AsterTimePreferences.first_day_of_week())
            AsterTimePreferences.set_account_week_start(null)
            assertEquals(
                AsterTimePreferences.account_calendar().firstDayOfWeek,
                AsterTimePreferences.first_day_of_week(),
            )
        } finally {
            AsterTimePreferences.set_account_week_start(null)
        }
    }
}
