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

const val UNDO_SEND_MIN_SECONDS = 1
const val UNDO_SEND_MAX_SECONDS = 30
const val UNDO_SEND_DEFAULT_SECONDS = 10

fun clamp_undo_send_seconds(seconds: Int): Int {
    if (seconds < UNDO_SEND_MIN_SECONDS) {
        return UNDO_SEND_DEFAULT_SECONDS
    }

    return minOf(seconds, UNDO_SEND_MAX_SECONDS)
}

fun resolve_undo_send_seconds(enabled: Boolean?, seconds: Int?): Int {
    if (enabled == false) {
        return 0
    }

    if (seconds == null) {
        return UNDO_SEND_DEFAULT_SECONDS
    }

    if (seconds <= 0) {
        return 0
    }

    return clamp_undo_send_seconds(seconds)
}
