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

package org.astermail.android.contacts.sync

enum class ContactFieldScope(val key: String) {
    ALL("all"),
    NAMES_AND_EMAILS("names_emails");

    fun limit(card: ContactCard): ContactCard = when (this) {
        ALL -> card
        NAMES_AND_EMAILS -> ContactCard(
            first_name = card.first_name,
            middle_name = card.middle_name,
            last_name = card.last_name,
            prefix = card.prefix,
            suffix = card.suffix,
            phonetic_first_name = card.phonetic_first_name,
            phonetic_middle_name = card.phonetic_middle_name,
            phonetic_last_name = card.phonetic_last_name,
            emails = card.emails,
            starred = card.starred,
        )
    }

    fun keep_device_details(target: ContactCard, device: ContactCard?): ContactCard = when {
        this == ALL || device == null -> target
        else -> device.copy(
            first_name = target.first_name,
            middle_name = target.middle_name,
            last_name = target.last_name,
            prefix = target.prefix,
            suffix = target.suffix,
            phonetic_first_name = target.phonetic_first_name,
            phonetic_middle_name = target.phonetic_middle_name,
            phonetic_last_name = target.phonetic_last_name,
            emails = target.emails,
            starred = target.starred,
        ).normalized()
    }

    fun marker(): String = if (this == ALL) CARD_SCHEMA.toString() else "$CARD_SCHEMA$MARKER_SEPARATOR$key"

    companion object {
        private const val MARKER_SEPARATOR = ':'

        fun from_key(key: String?): ContactFieldScope? = entries.firstOrNull { it.key == key }

        fun resolve(stored: String?, sync_enabled: Boolean): ContactFieldScope =
            from_key(stored) ?: if (sync_enabled) ALL else NAMES_AND_EMAILS

        fun schema_of(marker: String?): Int = marker?.substringBefore(MARKER_SEPARATOR)?.toIntOrNull() ?: 1

        fun scope_of(marker: String?): ContactFieldScope =
            from_key(marker?.substringAfter(MARKER_SEPARATOR, "")) ?: ALL
    }
}
