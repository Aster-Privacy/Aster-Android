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

package org.astermail.android.api.network

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class server_clock_test {
    private val base = Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()

    private fun header(ms: Long): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC))

    @Test
    fun small_skew_is_ignored() {
        assertEquals(0L, server_clock_offset(header(base + 2_000), base, base + 200))
    }

    @Test
    fun large_skew_is_measured_from_the_round_trip_midpoint() {
        val offset = server_clock_offset(header(base + 120_000), base, base + 1_000)!!
        assertEquals(120_000L + 500 - 500, offset)
    }

    @Test
    fun clock_behind_the_server_gives_a_negative_offset() {
        val offset = server_clock_offset(header(base - 60_000), base, base)!!
        assertEquals(-60_000L + 500, offset)
    }

    @Test
    fun bad_inputs_give_no_offset() {
        assertNull(server_clock_offset(null, base, base))
        assertNull(server_clock_offset("", base, base))
        assertNull(server_clock_offset("not a date", base, base))
        assertNull(server_clock_offset(header(base), base, base - 1))
        assertNull(server_clock_offset(header(base), base, base + 20_000))
    }

    @Test
    fun note_updates_now_and_reset_clears_it() {
        server_clock.reset()
        val now = System.currentTimeMillis()
        server_clock.note(header(now + 3_600_000), now, now)
        val offset = server_clock.offset_ms()
        assertEquals(true, offset in 3_599_000L..3_601_000L)
        server_clock.note(null, now, now)
        assertEquals(offset, server_clock.offset_ms())
        server_clock.reset()
        assertEquals(0L, server_clock.offset_ms())
    }
}
