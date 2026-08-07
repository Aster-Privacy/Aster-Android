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

package org.astermail.android.api

import kotlinx.serialization.json.Json
import org.astermail.android.api.family.FamilySeatBreakdown
import org.astermail.android.api.family.ListReservationsResponse
import org.astermail.android.api.family.breakdown_total
import org.astermail.android.api.family.family_seat_usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilySeatUsageTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    @Test
    fun total_is_the_server_value_and_breakdown_explains_it() {
        val response = ListReservationsResponse(
            reservations = emptyList(),
            max_members = 6,
            seats_used = 5,
            seats = FamilySeatBreakdown(
                active_members = 3,
                pending_invites = 0,
                reserved_addresses = 0,
                grace_members = 2,
            ),
        )

        val usage = family_seat_usage(response)

        assertEquals(5, usage.seats_used)
        assertEquals(6, usage.max_members)
        assertEquals(1, usage.seats_remaining)
        assertFalse(usage.seats_full)
        assertEquals(3, usage.breakdown?.active_members)
        assertEquals(2, usage.breakdown?.grace_members)
    }

    @Test
    fun breakdown_parts_always_sum_to_the_enforced_total() {
        val cases = listOf(
            FamilySeatBreakdown(3, 0, 0, 2),
            FamilySeatBreakdown(3, 2, 0, 0),
            FamilySeatBreakdown(1, 1, 2, 1),
            FamilySeatBreakdown(0, 0, 0, 0),
        )

        cases.forEach { breakdown ->
            val total = breakdown_total(breakdown)
            val usage = family_seat_usage(
                ListReservationsResponse(
                    reservations = emptyList(),
                    max_members = 6,
                    seats_used = total,
                    seats = breakdown,
                ),
            )
            assertEquals(total, usage.seats_used)
            assertEquals(6 - total, usage.seats_remaining)
        }
    }

    @Test
    fun seats_remaining_never_goes_negative_and_full_is_flagged() {
        val usage = family_seat_usage(seats_used = 8, max_members = 6)

        assertEquals(8, usage.seats_used)
        assertEquals(0, usage.seats_remaining)
        assertTrue(usage.seats_full)
    }

    @Test
    fun missing_breakdown_keeps_the_total_usable() {
        val response = json.decodeFromString<ListReservationsResponse>(
            """{"reservations":[],"max_members":6,"seats_used":5}""",
        )

        val usage = family_seat_usage(response)

        assertNull(usage.breakdown)
        assertEquals(5, usage.seats_used)
        assertEquals(1, usage.seats_remaining)
    }

    @Test
    fun server_breakdown_is_parsed_from_the_reservations_payload() {
        val response = json.decodeFromString<ListReservationsResponse>(
            """
            {"reservations":[],"max_members":6,"seats_used":5,
             "seats":{"active_members":3,"pending_invites":0,"reserved_addresses":0,"grace_members":2}}
            """.trimIndent(),
        )

        assertEquals(3, response.seats?.active_members)
        assertEquals(2, response.seats?.grace_members)
        assertEquals(response.seats_used, response.seats?.let { breakdown_total(it) })
    }
}
