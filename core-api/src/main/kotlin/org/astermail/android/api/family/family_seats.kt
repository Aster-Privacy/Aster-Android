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

package org.astermail.android.api.family

data class FamilySeatUsage(
    val seats_used: Int,
    val max_members: Int,
    val seats_remaining: Int,
    val seats_full: Boolean,
    val breakdown: FamilySeatBreakdown?,
)

fun breakdown_total(breakdown: FamilySeatBreakdown): Int =
    breakdown.active_members +
        breakdown.pending_invites +
        breakdown.reserved_addresses

fun family_seat_usage(
    seats_used: Int,
    max_members: Int,
    breakdown: FamilySeatBreakdown? = null,
): FamilySeatUsage {
    val cap = maxOf(0, max_members)
    val used = maxOf(0, seats_used)
    return FamilySeatUsage(
        seats_used = used,
        max_members = cap,
        seats_remaining = (cap - used).coerceAtLeast(0),
        seats_full = used >= cap,
        breakdown = breakdown,
    )
}

fun family_seat_usage(response: ListReservationsResponse): FamilySeatUsage =
    family_seat_usage(response.seats_used, response.max_members, response.seats)
