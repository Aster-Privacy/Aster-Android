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

data class Contact(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val company: String = "",
    val title: String = "",
    val work_email: String = "",
    val work_phone: String = "",
    val birthday: String = "",
    val address: String = "",
    val city: String = "",
    val region: String = "",
    val postal_code: String = "",
    val country: String = "",
    val website: String = "",
    val twitter: String = "",
    val linkedin: String = "",
    val notes: String = "",
    val avatar_url: String = "",
    val profile_color: String = "",
    val is_favorite: Boolean = false,
    val groups: List<String> = emptyList(),
    val raw_json: String = "",
    val deleted_at: String = "",
    val emails: List<ContactEntry> = emptyList(),
    val phones: List<ContactEntry> = emptyList(),
    val addresses: List<ContactPostal> = emptyList(),
) {
    val has_typed_fields: Boolean get() = emails.isNotEmpty() || phones.isNotEmpty() || addresses.isNotEmpty()

    fun email_entries(): List<ContactEntry> = emails.ifEmpty {
        listOfNotNull(
            email.takeIf { it.isNotBlank() }?.let { ContactEntry(it, ContactEntry.TYPE_HOME) },
            work_email.takeIf { it.isNotBlank() }?.let { ContactEntry(it, ContactEntry.TYPE_WORK) },
        )
    }

    fun phone_entries(): List<ContactEntry> = phones.ifEmpty {
        listOfNotNull(
            phone.takeIf { it.isNotBlank() }?.let { ContactEntry(it, ContactEntry.TYPE_MOBILE) },
            work_phone.takeIf { it.isNotBlank() }?.let { ContactEntry(it, ContactEntry.TYPE_WORK) },
        )
    }

    fun address_entries(): List<ContactPostal> = addresses.ifEmpty {
        listOfNotNull(
            ContactPostal(address, city, region, postal_code, country, ContactEntry.TYPE_HOME)
                .takeIf { !it.is_blank() },
        )
    }

    fun with_typed_fields(
        emails: List<ContactEntry>,
        phones: List<ContactEntry>,
        addresses: List<ContactPostal>,
    ): Contact {
        val clean_emails = emails.map { it.trimmed() }.filter { it.value.isNotEmpty() }
        val clean_phones = phones.map { it.trimmed() }.filter { it.value.isNotEmpty() }
        val clean_addresses = addresses.map { it.trimmed() }.filterNot { it.is_blank() }
        val primary_address = clean_addresses.firstOrNull { it.type == ContactEntry.TYPE_HOME }
            ?: clean_addresses.firstOrNull()
        return copy(
            emails = clean_emails,
            phones = clean_phones,
            addresses = clean_addresses,
            email = clean_emails.firstOrNull()?.value.orEmpty(),
            work_email = secondary_value(clean_emails),
            phone = clean_phones.firstOrNull()?.value.orEmpty(),
            work_phone = clean_phones.drop(1).firstOrNull { it.type == ContactEntry.TYPE_WORK }?.value.orEmpty(),
            address = primary_address?.street.orEmpty(),
            city = primary_address?.city.orEmpty(),
            region = primary_address?.region.orEmpty(),
            postal_code = primary_address?.postal_code.orEmpty(),
            country = primary_address?.country.orEmpty(),
        )
    }

    fun typed_fields_in_sync(): Boolean {
        if (!has_typed_fields) return true
        val derived = with_typed_fields(emails, phones, addresses)
        return derived.email == email && derived.work_email == work_email &&
            derived.phone == phone && derived.work_phone == work_phone &&
            derived.address == address && derived.city == city && derived.region == region &&
            derived.postal_code == postal_code && derived.country == country
    }

    private fun secondary_value(entries: List<ContactEntry>): String {
        val rest = entries.drop(1)
        return (rest.firstOrNull { it.type == ContactEntry.TYPE_WORK } ?: rest.firstOrNull())?.value.orEmpty()
    }
}

data class ContactEntry(
    val value: String,
    val type: String,
    val label: String = "",
) {
    fun trimmed(): ContactEntry {
        val clean_label = if (type == TYPE_OTHER) label.trim() else ""
        return ContactEntry(value.trim(), type, clean_label)
    }

    companion object {
        const val TYPE_HOME = "home"
        const val TYPE_WORK = "work"
        const val TYPE_PERSONAL = "personal"
        const val TYPE_MOBILE = "mobile"
        const val TYPE_FAX = "fax"
        const val TYPE_PAGER = "pager"
        const val TYPE_OTHER = "other"
    }
}

data class ContactPostal(
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postal_code: String = "",
    val country: String = "",
    val type: String = ContactEntry.TYPE_HOME,
    val label: String = "",
) {
    fun is_blank(): Boolean =
        street.isBlank() && city.isBlank() && region.isBlank() && postal_code.isBlank() && country.isBlank()

    fun trimmed(): ContactPostal = copy(
        street = street.trim(),
        city = city.trim(),
        region = region.trim(),
        postal_code = postal_code.trim(),
        country = country.trim(),
        label = if (type == ContactEntry.TYPE_OTHER) label.trim() else "",
    )

    fun lines(): List<String> = listOf(
        street,
        listOf(city, region, postal_code).filter { it.isNotBlank() }.joinToString(", "),
        country,
    ).filter { it.isNotBlank() }
}
