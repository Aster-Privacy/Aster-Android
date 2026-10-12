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

package org.astermail.android.ui.contacts

import org.astermail.android.ui.search.fold_text

private const val PHONE_QUERY_MIN_DIGITS = 3
private const val PHONE_QUERY_PUNCTUATION = "+-(). "

internal fun contact_matches_query(contact: Contact, query: String): Boolean {
    val needle = fold_text(query.trim())
    if (needle.isEmpty()) return true
    val text_fields = sequenceOf(
        contact.name,
        contact.email,
        contact.work_email,
        contact.company,
        contact.title,
    ) + contact.emails.asSequence().map { it.value }
    if (text_fields.any { fold_text(it).contains(needle) }) return true
    val is_phone_query = needle.all { it.isDigit() || it in PHONE_QUERY_PUNCTUATION }
    val needle_digits = needle.filter { it.isDigit() }
    if (!is_phone_query || needle_digits.length < PHONE_QUERY_MIN_DIGITS) return false
    val phones = sequenceOf(contact.phone, contact.work_phone) + contact.phones.asSequence().map { it.value }
    return phones.any { phone -> phone.filter { it.isDigit() }.contains(needle_digits) }
}

internal enum class ContactsEmptyReason { no_contacts, no_match, no_favorites, no_favorites_match }

internal fun contacts_empty_reason(query: String, favorites_only: Boolean): ContactsEmptyReason = when {
    favorites_only && query.isBlank() -> ContactsEmptyReason.no_favorites
    favorites_only -> ContactsEmptyReason.no_favorites_match
    query.isBlank() -> ContactsEmptyReason.no_contacts
    else -> ContactsEmptyReason.no_match
}
