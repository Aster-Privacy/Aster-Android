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

package org.astermail.android.contacts

import org.astermail.android.ui.contacts.Contact
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

enum class RecentBucket { TODAY, YESTERDAY, DATE }

data class RecentGroup(
    val date: LocalDate,
    val bucket: RecentBucket,
    val contacts: List<Contact>,
)

fun parse_created_at(value: String): Instant? {
    val text = value.trim()
    if (text.isEmpty()) return null
    return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(text) }.getOrNull()
}

fun has_recently_added(contacts: List<Contact>): Boolean =
    contacts.any { parse_created_at(it.created_at) != null }

fun recently_added_sorted(contacts: List<Contact>): List<Contact> =
    contacts
        .mapNotNull { c -> parse_created_at(c.created_at)?.let { c to it } }
        .sortedWith(compareByDescending<Pair<Contact, Instant>> { it.second }.thenBy { it.first.name.uppercase() })
        .map { it.first }

fun group_recently_added(contacts: List<Contact>, today: LocalDate, zone: ZoneId): List<RecentGroup> {
    val yesterday = today.minusDays(1)
    val groups = linkedMapOf<LocalDate, MutableList<Contact>>()
    for (contact in recently_added_sorted(contacts)) {
        val day = parse_created_at(contact.created_at)!!.atZone(zone).toLocalDate()
        groups.getOrPut(day) { mutableListOf() }.add(contact)
    }
    return groups.map { (day, list) ->
        val bucket = when (day) {
            today -> RecentBucket.TODAY
            yesterday -> RecentBucket.YESTERDAY
            else -> RecentBucket.DATE
        }
        RecentGroup(day, bucket, list)
    }
}
