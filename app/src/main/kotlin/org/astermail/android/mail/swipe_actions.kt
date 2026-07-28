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

const val SWIPE_ACTION_ARCHIVE = "archive"
const val SWIPE_ACTION_DELETE = "delete"
const val SWIPE_ACTION_TOGGLE_READ = "toggle_read"
const val SWIPE_ACTION_SNOOZE = "snooze"
const val SWIPE_ACTION_STAR = "star"
const val SWIPE_ACTION_SPAM = "spam"
const val SWIPE_ACTION_NONE = "none"

const val DEFAULT_SWIPE_RIGHT_ACTION = SWIPE_ACTION_TOGGLE_READ
const val DEFAULT_SWIPE_LEFT_ACTION = SWIPE_ACTION_ARCHIVE

val SWIPE_ACTION_IDS = listOf(
    SWIPE_ACTION_ARCHIVE,
    SWIPE_ACTION_DELETE,
    SWIPE_ACTION_TOGGLE_READ,
    SWIPE_ACTION_SNOOZE,
    SWIPE_ACTION_STAR,
    SWIPE_ACTION_SPAM,
    SWIPE_ACTION_NONE,
)

fun normalize_swipe_action(raw: String?, fallback: String = SWIPE_ACTION_NONE): String {
    val id = raw.orEmpty()
    return when {
        id.isEmpty() -> fallback
        id == "trash" -> SWIPE_ACTION_DELETE
        id == "mark_read" || id == "mark_unread" || id == "read" || id == "unread" -> SWIPE_ACTION_TOGGLE_READ
        SWIPE_ACTION_IDS.contains(id) -> id
        else -> fallback
    }
}
