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

package org.astermail.android.notifications

object NotificationActionChoices {
    const val ARCHIVE = "archive"
    const val TRASH = "trash"
    const val MARK_READ = "mark_read"
    const val SPAM = "spam"
    const val MAX = 3

    val ALL = listOf(ARCHIVE, TRASH, MARK_READ, SPAM)
    val DEFAULT = listOf(ARCHIVE, TRASH, MARK_READ)

    fun sanitize(stored: List<String>?): List<String> {
        if (stored == null) return DEFAULT
        val wanted = stored.toSet()
        return ALL.filter { it in wanted }.take(MAX)
    }

    fun can_select(current: List<String>, id: String): Boolean =
        id in current || (id in ALL && current.size < MAX)

    fun toggle(current: List<String>, id: String): List<String> {
        val base = sanitize(current)
        return when {
            id in base -> base - id
            can_select(base, id) -> sanitize(base + id)
            else -> base
        }
    }

    fun visible(chosen: List<String>, has_code: Boolean): List<String> =
        sanitize(chosen).take(if (has_code) MAX - 1 else MAX)

    fun request_offset(id: String): Int = when (id) {
        ARCHIVE -> 0
        TRASH -> 1
        MARK_READ -> 2
        SPAM -> 4
        else -> 5
    }
}
