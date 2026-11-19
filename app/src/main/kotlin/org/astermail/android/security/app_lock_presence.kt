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

package org.astermail.android.security

enum class AppLockPresence { ABSENT, CONFIGURED, UNVERIFIABLE }

object AppLockPresenceRule {
    fun resolve(has_pin_hash: Boolean, marker_present: Boolean): AppLockPresence = when {
        has_pin_hash -> AppLockPresence.CONFIGURED
        marker_present -> AppLockPresence.UNVERIFIABLE
        else -> AppLockPresence.ABSENT
    }

    fun requires_lock(presence: AppLockPresence): Boolean = presence != AppLockPresence.ABSENT

    fun should_write_marker(has_pin_hash: Boolean, marker_present: Boolean): Boolean =
        has_pin_hash && !marker_present

    fun may_forget_marker(presence: AppLockPresence, signed_in: Boolean): Boolean =
        presence == AppLockPresence.UNVERIFIABLE && !signed_in
}
