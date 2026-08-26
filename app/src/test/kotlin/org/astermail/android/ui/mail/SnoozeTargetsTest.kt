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

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeTargetsTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun targets(iso: String): List<ZonedDateTime> {
        return snooze_targets(ZonedDateTime.parse(iso).withZoneSameInstant(tokyo))
    }

    @Test
    fun later_today_is_a_four_hour_offset() {
        val now = ZonedDateTime.parse("2026-08-03T12:00:00Z").withZoneSameInstant(tokyo)

        assertEquals(
            now.plusHours(4).toInstant(),
            snooze_targets(now)[0].toInstant(),
        )
    }

    @Test
    fun tomorrow_wakes_at_nine_in_the_account_zone() {
        assertEquals(
            "2026-08-04T00:00:00Z",
            targets("2026-08-03T12:00:00Z")[1].toInstant().toString(),
        )
    }

    @Test
    fun tomorrow_uses_the_account_zone_calendar_day() {
        assertEquals(
            "2026-08-05T00:00:00Z",
            targets("2026-08-03T22:00:00Z")[1].toInstant().toString(),
        )
    }

    @Test
    fun this_weekend_skips_a_week_when_it_is_already_saturday() {
        assertEquals(
            "2026-08-15T00:00:00Z",
            targets("2026-08-07T22:00:00Z")[2].toInstant().toString(),
        )
    }

    @Test
    fun this_weekend_picks_the_coming_saturday() {
        assertEquals(
            "2026-08-08T00:00:00Z",
            targets("2026-08-03T12:00:00Z")[2].toInstant().toString(),
        )
    }

    @Test
    fun next_week_is_seven_days_at_nine() {
        assertEquals(
            "2026-08-10T00:00:00Z",
            targets("2026-08-03T12:00:00Z")[3].toInstant().toString(),
        )
    }

    @Test
    fun next_month_keeps_the_day_of_month_at_nine() {
        assertEquals(
            "2026-09-03T00:00:00Z",
            targets("2026-08-03T12:00:00Z")[4].toInstant().toString(),
        )
    }

    @Test
    fun next_month_clamps_to_the_last_day_of_a_shorter_month() {
        assertEquals(
            "2026-02-28T00:00:00Z",
            targets("2026-01-31T12:00:00Z")[4].toInstant().toString(),
        )
    }

    @Test
    fun options_pair_every_label_with_a_target() {
        val options = snooze_options(
            "later",
            "tomorrow",
            "weekend",
            "next week",
            "next month",
            ZonedDateTime.parse("2026-08-03T00:00:00Z").withZoneSameInstant(tokyo),
        )

        assertEquals(5, options.size)
        assertEquals("later", options[0].first)
        assertEquals("2026-08-04T00:00:00Z", options[1].second)
    }

    @Test
    fun options_drop_later_today_once_it_would_land_tomorrow() {
        val options = snooze_options(
            "later",
            "tomorrow",
            "weekend",
            "next week",
            "next month",
            ZonedDateTime.parse("2026-08-03T12:00:00Z").withZoneSameInstant(tokyo),
        )

        assertEquals(4, options.size)
        assertEquals("tomorrow", options[0].first)
    }
}
