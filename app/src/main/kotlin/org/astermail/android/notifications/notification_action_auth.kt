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

package org.astermail.android.notifications

enum class NotificationActionGate { Apply, RequireUnlock, Ignore }

object NotificationActionAuthPolicy {
    const val SYSTEM_AUTH_MIN_SDK = 31

    fun requires_auth(active_count: Int, after_locked_tap: Boolean = false): Boolean =
        after_locked_tap || active_count <= 1

    fun gate(device_locked: Boolean, posted_with_auth: Boolean, sdk: Int): NotificationActionGate = when {
        sdk >= SYSTEM_AUTH_MIN_SDK && posted_with_auth -> NotificationActionGate.Apply
        !device_locked -> NotificationActionGate.Apply
        sdk >= SYSTEM_AUTH_MIN_SDK -> NotificationActionGate.RequireUnlock
        else -> NotificationActionGate.Ignore
    }

    fun reposts(
        current: Map<Int, Boolean>,
        active_count: Int,
        after_locked_tap: Boolean = false,
    ): Map<Int, Boolean> {
        val target = requires_auth(active_count, after_locked_tap)
        return current.filterValues { it != target }.mapValues { target }
    }

    fun repost_order(ids: List<Int>, first: Int): List<Int> =
        ids.sortedBy { if (it == first) 0 else 1 }
}
