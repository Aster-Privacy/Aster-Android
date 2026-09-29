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

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import java.util.Locale

data class DeviceDataRow(
    val mimetype: String,
    val values: Map<String, Any?>,
)

data class DeviceLabels(
    val sibling: String,
    val graduation: String,
    val wedding: String,
)

enum class DeviceGroup(val mimetype: String) {
    NAME(StructuredName.CONTENT_ITEM_TYPE),
    NICKNAME(Nickname.CONTENT_ITEM_TYPE),
    ORGANIZATION(Organization.CONTENT_ITEM_TYPE),
    NOTE(Note.CONTENT_ITEM_TYPE),
    EVENT(Event.CONTENT_ITEM_TYPE),
    EMAIL(Email.CONTENT_ITEM_TYPE),
    PHONE(Phone.CONTENT_ITEM_TYPE),
    POSTAL(StructuredPostal.CONTENT_ITEM_TYPE),
    WEBSITE(Website.CONTENT_ITEM_TYPE),
    RELATION(Relation.CONTENT_ITEM_TYPE),
    IM(Im.CONTENT_ITEM_TYPE),
}

val SYNCED_MIMETYPES: List<String> = DeviceGroup.entries.map { it.mimetype }

private fun DeviceGroup.fields(card: ContactCard): List<Any> = when (this) {
    DeviceGroup.NAME -> listOf(
        card.first_name,
        card.middle_name,
        card.last_name,
        card.prefix,
        card.suffix,
        card.phonetic_first_name,
        card.phonetic_middle_name,
        card.phonetic_last_name,
    )
    DeviceGroup.NICKNAME -> listOf(card.nickname)
    DeviceGroup.ORGANIZATION -> listOf(card.company, card.department, card.job_title)
    DeviceGroup.NOTE -> listOf(card.notes)
    DeviceGroup.EVENT -> listOf(card.birthday, card.dates)
    DeviceGroup.EMAIL -> card.emails
    DeviceGroup.PHONE -> card.phones
    DeviceGroup.POSTAL -> card.addresses
    DeviceGroup.WEBSITE -> card.websites
    DeviceGroup.RELATION -> card.relations
    DeviceGroup.IM -> card.messengers
}

fun changed_device_groups(current: ContactCard?, target: ContactCard): List<DeviceGroup> =
    DeviceGroup.entries.filter { current == null || it.fields(current) != it.fields(target) }

private val MESSENGER_PROTOCOL_NAMES = mapOf(
    "signal" to "Signal",
    "matrix" to "Matrix",
    "telegram" to "Telegram",
    "whatsapp" to "WhatsApp",
)

private fun String.or_null(): String? = ifBlank { null }

fun device_rows_for_group(card: ContactCard, group: DeviceGroup, labels: DeviceLabels): List<DeviceDataRow> {
    fun row(vararg pairs: Pair<String, Any?>) = DeviceDataRow(group.mimetype, mapOf(*pairs))
    return when (group) {
        DeviceGroup.NAME ->
            if (group.fields(card).all { (it as String).isBlank() }) {
                emptyList()
            } else {
                listOf(
                    row(
                        StructuredName.GIVEN_NAME to card.first_name.or_null(),
                        StructuredName.MIDDLE_NAME to card.middle_name.or_null(),
                        StructuredName.FAMILY_NAME to card.last_name.or_null(),
                        StructuredName.PREFIX to card.prefix.or_null(),
                        StructuredName.SUFFIX to card.suffix.or_null(),
                        StructuredName.PHONETIC_GIVEN_NAME to card.phonetic_first_name.or_null(),
                        StructuredName.PHONETIC_MIDDLE_NAME to card.phonetic_middle_name.or_null(),
                        StructuredName.PHONETIC_FAMILY_NAME to card.phonetic_last_name.or_null(),
                    ),
                )
            }
        DeviceGroup.NICKNAME ->
            if (card.nickname.isBlank()) {
                emptyList()
            } else {
                listOf(row(Nickname.NAME to card.nickname, Nickname.TYPE to Nickname.TYPE_DEFAULT))
            }
        DeviceGroup.ORGANIZATION ->
            if (card.company.isBlank() && card.department.isBlank() && card.job_title.isBlank()) {
                emptyList()
            } else {
                listOf(
                    row(
                        Organization.COMPANY to card.company.or_null(),
                        Organization.DEPARTMENT to card.department.or_null(),
                        Organization.TITLE to card.job_title.or_null(),
                        Organization.TYPE to Organization.TYPE_WORK,
                    ),
                )
            }
        DeviceGroup.NOTE -> if (card.notes.isBlank()) emptyList() else listOf(row(Note.NOTE to card.notes))
        DeviceGroup.EVENT -> buildList {
            if (card.birthday.isNotBlank()) {
                add(row(Event.START_DATE to card.birthday, Event.TYPE to Event.TYPE_BIRTHDAY))
            }
            for (date in card.dates) {
                add(
                    when (date.type) {
                        "anniversary" -> row(Event.START_DATE to date.value, Event.TYPE to Event.TYPE_ANNIVERSARY)
                        "graduation" -> row(
                            Event.START_DATE to date.value,
                            Event.TYPE to Event.TYPE_CUSTOM,
                            Event.LABEL to labels.graduation,
                        )
                        "wedding" -> row(
                            Event.START_DATE to date.value,
                            Event.TYPE to Event.TYPE_CUSTOM,
                            Event.LABEL to labels.wedding,
                        )
                        else -> row(Event.START_DATE to date.value, Event.TYPE to Event.TYPE_OTHER)
                    },
                )
            }
        }
        DeviceGroup.EMAIL -> card.emails.map {
            row(
                Email.ADDRESS to it.value,
                Email.TYPE to when (it.type) {
                    "home" -> Email.TYPE_HOME
                    "work" -> Email.TYPE_WORK
                    else -> Email.TYPE_OTHER
                },
            )
        }
        DeviceGroup.PHONE -> card.phones.map {
            row(
                Phone.NUMBER to it.value,
                Phone.TYPE to when (it.type) {
                    "mobile" -> Phone.TYPE_MOBILE
                    "home" -> Phone.TYPE_HOME
                    "work" -> Phone.TYPE_WORK
                    "fax" -> Phone.TYPE_OTHER_FAX
                    "pager" -> Phone.TYPE_PAGER
                    else -> Phone.TYPE_OTHER
                },
            )
        }
        DeviceGroup.POSTAL -> card.addresses.map {
            row(
                StructuredPostal.STREET to it.street.or_null(),
                StructuredPostal.CITY to it.city.or_null(),
                StructuredPostal.REGION to it.state.or_null(),
                StructuredPostal.POSTCODE to it.postal_code.or_null(),
                StructuredPostal.COUNTRY to it.country.or_null(),
                StructuredPostal.TYPE to when (it.type) {
                    "home" -> StructuredPostal.TYPE_HOME
                    "work" -> StructuredPostal.TYPE_WORK
                    else -> StructuredPostal.TYPE_OTHER
                },
            )
        }
        DeviceGroup.WEBSITE -> card.websites.map {
            row(
                Website.URL to it.value,
                Website.TYPE to when (it.type) {
                    "private" -> Website.TYPE_HOME
                    "work" -> Website.TYPE_WORK
                    "blog" -> Website.TYPE_BLOG
                    else -> Website.TYPE_OTHER
                },
            )
        }
        DeviceGroup.RELATION -> card.relations.map {
            when (it.type) {
                "sibling" -> row(
                    Relation.NAME to it.value,
                    Relation.TYPE to Relation.TYPE_CUSTOM,
                    Relation.LABEL to labels.sibling,
                )
                else -> row(
                    Relation.NAME to it.value,
                    Relation.TYPE to when (it.type) {
                        "assistant" -> Relation.TYPE_ASSISTANT
                        "manager" -> Relation.TYPE_MANAGER
                        "spouse" -> Relation.TYPE_SPOUSE
                        "partner" -> Relation.TYPE_PARTNER
                        "child" -> Relation.TYPE_CHILD
                        "parent" -> Relation.TYPE_PARENT
                        "friend" -> Relation.TYPE_FRIEND
                        else -> Relation.TYPE_RELATIVE
                    },
                )
            }
        }
        DeviceGroup.IM -> card.messengers.map {
            when (it.type) {
                "xmpp" -> row(
                    Im.DATA to it.value,
                    Im.TYPE to Im.TYPE_OTHER,
                    Im.PROTOCOL to Im.PROTOCOL_JABBER,
                )
                else -> row(
                    Im.DATA to it.value,
                    Im.TYPE to Im.TYPE_OTHER,
                    Im.PROTOCOL to Im.PROTOCOL_CUSTOM,
                    Im.CUSTOM_PROTOCOL to MESSENGER_PROTOCOL_NAMES[it.type],
                )
            }
        }
    }
}

fun device_rows_for_card(card: ContactCard, labels: DeviceLabels): List<DeviceDataRow> =
    DeviceGroup.entries.flatMap { device_rows_for_group(card, it, labels) }

private fun DeviceDataRow.text(column: String): String = values[column]?.toString()?.trim().orEmpty()

private fun DeviceDataRow.int(column: String): Int? = values[column]?.toString()?.trim()?.toIntOrNull()

private fun String.matches_label(localized: String, fallback: String): Boolean =
    equals(localized, ignoreCase = true) || equals(fallback, ignoreCase = true)

fun contact_card_from_device_rows(rows: List<DeviceDataRow>, starred: Boolean, labels: DeviceLabels): ContactCard {
    fun of(mimetype: String) = rows.filter { it.mimetype == mimetype }
    val name = of(StructuredName.CONTENT_ITEM_TYPE).firstOrNull()
    val organization = of(Organization.CONTENT_ITEM_TYPE).firstOrNull {
        it.text(Organization.COMPANY).isNotEmpty() ||
            it.text(Organization.DEPARTMENT).isNotEmpty() ||
            it.text(Organization.TITLE).isNotEmpty()
    }
    val nickname = of(Nickname.CONTENT_ITEM_TYPE).map { it.text(Nickname.NAME) }.firstOrNull { it.isNotEmpty() }
    val notes = of(Note.CONTENT_ITEM_TYPE).map { it.text(Note.NOTE) }.filter { it.isNotEmpty() }
    val events = of(Event.CONTENT_ITEM_TYPE).filter { it.text(Event.START_DATE).isNotEmpty() }
    val birthday = events.firstOrNull { it.int(Event.TYPE) == Event.TYPE_BIRTHDAY }
    val dates = events.filter { it !== birthday }.map { event ->
        val label = event.text(Event.LABEL)
        val type = when (event.int(Event.TYPE)) {
            Event.TYPE_ANNIVERSARY -> "anniversary"
            Event.TYPE_CUSTOM -> when {
                label.matches_label(labels.graduation, "graduation") -> "graduation"
                label.matches_label(labels.wedding, "wedding") -> "wedding"
                else -> "other"
            }
            else -> "other"
        }
        CardEntry(event.text(Event.START_DATE), type)
    }
    return ContactCard(
        first_name = name?.text(StructuredName.GIVEN_NAME).orEmpty(),
        middle_name = name?.text(StructuredName.MIDDLE_NAME).orEmpty(),
        last_name = name?.text(StructuredName.FAMILY_NAME).orEmpty(),
        prefix = name?.text(StructuredName.PREFIX).orEmpty(),
        suffix = name?.text(StructuredName.SUFFIX).orEmpty(),
        phonetic_first_name = name?.text(StructuredName.PHONETIC_GIVEN_NAME).orEmpty(),
        phonetic_middle_name = name?.text(StructuredName.PHONETIC_MIDDLE_NAME).orEmpty(),
        phonetic_last_name = name?.text(StructuredName.PHONETIC_FAMILY_NAME).orEmpty(),
        nickname = nickname.orEmpty(),
        company = organization?.text(Organization.COMPANY).orEmpty(),
        department = organization?.text(Organization.DEPARTMENT).orEmpty(),
        job_title = organization?.text(Organization.TITLE).orEmpty(),
        notes = notes.joinToString("\n"),
        birthday = birthday?.text(Event.START_DATE).orEmpty(),
        emails = of(Email.CONTENT_ITEM_TYPE).map {
            CardEntry(
                it.text(Email.ADDRESS),
                when (it.int(Email.TYPE)) {
                    Email.TYPE_HOME -> "home"
                    Email.TYPE_WORK -> "work"
                    else -> "other"
                },
            )
        },
        phones = of(Phone.CONTENT_ITEM_TYPE).map {
            CardEntry(
                it.text(Phone.NUMBER),
                when (it.int(Phone.TYPE)) {
                    Phone.TYPE_MOBILE -> "mobile"
                    Phone.TYPE_HOME -> "home"
                    Phone.TYPE_WORK, Phone.TYPE_WORK_MOBILE, Phone.TYPE_COMPANY_MAIN -> "work"
                    Phone.TYPE_FAX_HOME, Phone.TYPE_FAX_WORK, Phone.TYPE_OTHER_FAX -> "fax"
                    Phone.TYPE_PAGER, Phone.TYPE_WORK_PAGER -> "pager"
                    else -> "other"
                },
            )
        },
        addresses = of(StructuredPostal.CONTENT_ITEM_TYPE).map {
            val street = it.text(StructuredPostal.STREET)
            val city = it.text(StructuredPostal.CITY)
            val region = it.text(StructuredPostal.REGION)
            val postcode = it.text(StructuredPostal.POSTCODE)
            val country = it.text(StructuredPostal.COUNTRY)
            val structured_blank = street.isEmpty() && city.isEmpty() && region.isEmpty() &&
                postcode.isEmpty() && country.isEmpty()
            CardAddress(
                street = if (structured_blank) it.text(StructuredPostal.FORMATTED_ADDRESS) else street,
                city = city,
                state = region,
                postal_code = postcode,
                country = country,
                type = when (it.int(StructuredPostal.TYPE)) {
                    StructuredPostal.TYPE_HOME -> "home"
                    StructuredPostal.TYPE_WORK -> "work"
                    else -> "other"
                },
            )
        },
        websites = of(Website.CONTENT_ITEM_TYPE).map {
            CardEntry(
                it.text(Website.URL),
                when (it.int(Website.TYPE)) {
                    Website.TYPE_HOME, Website.TYPE_HOMEPAGE -> "private"
                    Website.TYPE_WORK -> "work"
                    Website.TYPE_BLOG -> "blog"
                    else -> "other"
                },
            )
        },
        relations = of(Relation.CONTENT_ITEM_TYPE).map {
            CardEntry(
                it.text(Relation.NAME),
                when (it.int(Relation.TYPE)) {
                    Relation.TYPE_ASSISTANT -> "assistant"
                    Relation.TYPE_MANAGER -> "manager"
                    Relation.TYPE_SPOUSE -> "spouse"
                    Relation.TYPE_PARTNER, Relation.TYPE_DOMESTIC_PARTNER -> "partner"
                    Relation.TYPE_CHILD -> "child"
                    Relation.TYPE_PARENT, Relation.TYPE_FATHER, Relation.TYPE_MOTHER -> "parent"
                    Relation.TYPE_BROTHER, Relation.TYPE_SISTER -> "sibling"
                    Relation.TYPE_FRIEND -> "friend"
                    Relation.TYPE_CUSTOM ->
                        if (it.text(Relation.LABEL).matches_label(labels.sibling, "sibling")) "sibling" else "other"
                    else -> "other"
                },
            )
        },
        messengers = of(Im.CONTENT_ITEM_TYPE).map {
            val custom = it.text(Im.CUSTOM_PROTOCOL).lowercase(Locale.ROOT)
            CardEntry(
                it.text(Im.DATA),
                when (it.int(Im.PROTOCOL)) {
                    Im.PROTOCOL_JABBER -> "xmpp"
                    Im.PROTOCOL_CUSTOM -> when (custom) {
                        "signal", "matrix", "telegram", "whatsapp" -> custom
                        "xmpp", "jabber" -> "xmpp"
                        else -> "other"
                    }
                    else -> "other"
                },
            )
        },
        dates = dates,
        starred = starred,
    ).normalized()
}
