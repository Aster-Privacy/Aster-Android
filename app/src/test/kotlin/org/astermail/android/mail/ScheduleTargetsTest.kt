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

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTargetsTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `tomorrow morning lands on the next day at the shared morning hour`() {
        val now = ZonedDateTime.of(2026, 8, 24, 15, 42, 0, 0, zone)
        val target = schedule_tomorrow_morning(now)

        assertEquals(ZonedDateTime.of(2026, 8, 25, SCHEDULE_MORNING_HOUR, 0, 0, 0, zone), target)
    }

    @Test
    fun `tomorrow afternoon lands on the next day at the shared afternoon hour`() {
        val now = ZonedDateTime.of(2026, 8, 24, 6, 5, 0, 0, zone)
        val target = schedule_tomorrow_afternoon(now)

        assertEquals(ZonedDateTime.of(2026, 8, 25, SCHEDULE_AFTERNOON_HOUR, 0, 0, 0, zone), target)
    }

    @Test
    fun `next monday from a monday skips to the following week`() {
        val now = ZonedDateTime.of(2026, 8, 24, 9, 0, 0, 0, zone)
        val target = schedule_next_monday_morning(now)

        assertEquals(ZonedDateTime.of(2026, 8, 31, SCHEDULE_MORNING_HOUR, 0, 0, 0, zone), target)
    }

    @Test
    fun `next monday from a friday lands on the coming monday`() {
        val now = ZonedDateTime.of(2026, 8, 28, 23, 30, 0, 0, zone)
        val target = schedule_next_monday_morning(now)

        assertEquals(ZonedDateTime.of(2026, 8, 31, SCHEDULE_MORNING_HOUR, 0, 0, 0, zone), target)
    }

    @Test
    fun `tonight is offered while it is still ahead`() {
        val now = ZonedDateTime.of(2026, 8, 24, 15, 42, 0, 0, zone)

        assertEquals(
            listOf(
                SCHEDULE_LABEL_ONE_HOUR,
                SCHEDULE_LABEL_TONIGHT,
                SCHEDULE_LABEL_TOMORROW_MORNING,
                SCHEDULE_LABEL_TOMORROW_AFTERNOON,
                SCHEDULE_LABEL_MONDAY_MORNING,
            ),
            schedule_target_labels(now),
        )
        assertEquals(5, schedule_targets(now).size)
    }

    @Test
    fun `tonight drops out once the evening hour has passed`() {
        val now = ZonedDateTime.of(2026, 8, 24, 22, 10, 0, 0, zone)

        assertEquals(
            listOf(
                SCHEDULE_LABEL_ONE_HOUR,
                SCHEDULE_LABEL_TOMORROW_MORNING,
                SCHEDULE_LABEL_TOMORROW_AFTERNOON,
                SCHEDULE_LABEL_MONDAY_MORNING,
            ),
            schedule_target_labels(now),
        )
        assertEquals(4, schedule_targets(now).size)
    }

    @Test
    fun `labels stay aligned with targets`() {
        val now = ZonedDateTime.of(2026, 8, 24, 20, 59, 0, 0, zone)

        assertEquals(schedule_targets(now).size, schedule_target_labels(now).size)
    }

    @Test
    fun `every target is in the future`() {
        val now = ZonedDateTime.of(2026, 8, 24, 23, 59, 0, 0, zone)

        schedule_targets(now).forEach { target ->
            assert(target.isAfter(now))
        }
    }

    @Test
    fun `targets keep the account zone`() {
        val now = ZonedDateTime.of(2026, 8, 24, 10, 0, 0, 0, ZoneId.of("Asia/Tokyo"))

        schedule_targets(now).forEach { target ->
            assertEquals(ZoneId.of("Asia/Tokyo"), target.zone)
        }
    }
}
