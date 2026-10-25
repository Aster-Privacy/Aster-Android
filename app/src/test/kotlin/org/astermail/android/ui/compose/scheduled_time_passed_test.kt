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

package org.astermail.android.ui.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class scheduled_time_passed_test {
    private val now = java.time.Instant.parse("2026-10-25T09:01:00Z").toEpochMilli()

    @Test
    fun past_time_has_passed() {
        assertTrue(is_scheduled_time_passed("2026-10-25T09:00:00Z", now))
    }

    @Test
    fun current_instant_has_passed() {
        assertTrue(is_scheduled_time_passed("2026-10-25T09:01:00Z", now))
    }

    @Test
    fun future_time_has_not_passed() {
        assertFalse(is_scheduled_time_passed("2026-10-25T09:02:00Z", now))
    }

    @Test
    fun unparseable_time_is_left_to_the_server() {
        assertFalse(is_scheduled_time_passed("not a time", now))
    }
}
