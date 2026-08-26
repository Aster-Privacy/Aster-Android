// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.api.preferences

const val INBOX_SORT_NEWEST_FIRST = "newest_first"
const val INBOX_SORT_OLDEST_FIRST = "oldest_first"

fun resolve_inbox_sort_oldest_first(preferences: UserPreferences?): Boolean {
    if (preferences == null) return false

    return when (preferences.inbox_sort_order) {
        INBOX_SORT_OLDEST_FIRST -> true
        INBOX_SORT_NEWEST_FIRST -> false
        else -> preferences.conversation_order == "oldest"
    }
}

const val CONVERSATION_ORDER_ASCENDING = "asc"
const val CONVERSATION_ORDER_DESCENDING = "desc"

fun normalize_order_preferences(preferences: UserPreferences): UserPreferences {
    val order = when (preferences.conversation_order) {
        CONVERSATION_ORDER_ASCENDING, CONVERSATION_ORDER_DESCENDING -> preferences.conversation_order
        else -> CONVERSATION_ORDER_ASCENDING
    }

    return preferences.copy(
        inbox_sort_order = inbox_sort_order_value(resolve_inbox_sort_oldest_first(preferences)),
        conversation_order = order,
    )
}

fun inbox_sort_order_value(oldest_first: Boolean): String =
    if (oldest_first) INBOX_SORT_OLDEST_FIRST else INBOX_SORT_NEWEST_FIRST
