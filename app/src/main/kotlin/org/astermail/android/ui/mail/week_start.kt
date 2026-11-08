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

import android.app.DatePickerDialog
import java.util.Calendar
import java.util.Locale

object WeekStart {
    fun day_of_week(value: String?): Int? = when (value?.trim()?.lowercase(Locale.ROOT)) {
        "sunday" -> Calendar.SUNDAY
        "monday" -> Calendar.MONDAY
        "saturday" -> Calendar.SATURDAY
        else -> null
    }

    fun resolve(account_week_start: Int?, locale_week_start: Int): Int = account_week_start ?: locale_week_start
}

fun DatePickerDialog.with_account_week_start(): DatePickerDialog = apply {
    datePicker.firstDayOfWeek = AsterTimePreferences.first_day_of_week()
}
