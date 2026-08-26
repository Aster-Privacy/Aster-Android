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

import java.time.DayOfWeek
import java.time.ZonedDateTime

const val SCHEDULE_MORNING_HOUR = 8

const val SCHEDULE_AFTERNOON_HOUR = 13

const val SCHEDULE_EVENING_HOUR = 21

private fun at_hour(value: ZonedDateTime, hour: Int): ZonedDateTime {
    return value.toLocalDate().atTime(hour, 0).atZone(value.zone)
}

fun schedule_in_one_hour(now: ZonedDateTime): ZonedDateTime {
    return now.plusHours(1)
}

fun schedule_tonight(now: ZonedDateTime): ZonedDateTime {
    return at_hour(now, SCHEDULE_EVENING_HOUR)
}

fun schedule_tomorrow_morning(now: ZonedDateTime): ZonedDateTime {
    return at_hour(now.plusDays(1), SCHEDULE_MORNING_HOUR)
}

fun schedule_tomorrow_afternoon(now: ZonedDateTime): ZonedDateTime {
    return at_hour(now.plusDays(1), SCHEDULE_AFTERNOON_HOUR)
}

fun schedule_next_monday_morning(now: ZonedDateTime): ZonedDateTime {
    val days_ahead = ((DayOfWeek.MONDAY.value - now.dayOfWeek.value + 7) % 7)
        .let { if (it == 0) 7 else it }

    return at_hour(now.plusDays(days_ahead.toLong()), SCHEDULE_MORNING_HOUR)
}

fun schedule_targets(now: ZonedDateTime): List<ZonedDateTime> {
    val tonight = schedule_tonight(now)

    return listOfNotNull(
        schedule_in_one_hour(now),
        tonight.takeIf { it.isAfter(now) },
        schedule_tomorrow_morning(now),
        schedule_tomorrow_afternoon(now),
        schedule_next_monday_morning(now),
    )
}

fun schedule_target_labels(now: ZonedDateTime): List<String> {
    val tonight = schedule_tonight(now)

    return listOfNotNull(
        SCHEDULE_LABEL_ONE_HOUR,
        SCHEDULE_LABEL_TONIGHT.takeIf { tonight.isAfter(now) },
        SCHEDULE_LABEL_TOMORROW_MORNING,
        SCHEDULE_LABEL_TOMORROW_AFTERNOON,
        SCHEDULE_LABEL_MONDAY_MORNING,
    )
}

const val SCHEDULE_LABEL_ONE_HOUR = "one_hour"

const val SCHEDULE_LABEL_TONIGHT = "tonight"

const val SCHEDULE_LABEL_TOMORROW_MORNING = "tomorrow_morning"

const val SCHEDULE_LABEL_TOMORROW_AFTERNOON = "tomorrow_afternoon"

const val SCHEDULE_LABEL_MONDAY_MORNING = "monday_morning"
