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

package org.astermail.android.ui.upgrade

import org.junit.Assert.assertEquals
import org.junit.Test

class OfferCountdownFormatTest {

    private val hour = 3_600_000L

    @Test
    fun shows_days_and_hours() {
        assertEquals("2d 5h", offer_countdown_text(53 * hour))
    }

    @Test
    fun shows_hours_and_minutes_under_a_day() {
        assertEquals("5h 30m", offer_countdown_text(5 * hour + 30 * 60_000L))
    }

    @Test
    fun shows_minutes_under_an_hour() {
        assertEquals("45m", offer_countdown_text(45 * 60_000L))
    }

    @Test
    fun is_empty_after_the_end() {
        assertEquals("", offer_countdown_text(0L))
        assertEquals("", offer_countdown_text(-5L))
    }
}
