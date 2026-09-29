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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

@Serializable
data class CardEntry(
    val value: String,
    val type: String,
)

@Serializable
data class CardAddress(
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val postal_code: String = "",
    val country: String = "",
    val type: String = "home",
) {
    fun is_blank(): Boolean =
        street.isBlank() && city.isBlank() && state.isBlank() && postal_code.isBlank() && country.isBlank()
}

@Serializable
data class ContactCard(
    val first_name: String = "",
    val middle_name: String = "",
    val last_name: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val phonetic_first_name: String = "",
    val phonetic_middle_name: String = "",
    val phonetic_last_name: String = "",
    val nickname: String = "",
    val company: String = "",
    val department: String = "",
    val job_title: String = "",
    val notes: String = "",
    val birthday: String = "",
    val emails: List<CardEntry> = emptyList(),
    val phones: List<CardEntry> = emptyList(),
    val addresses: List<CardAddress> = emptyList(),
    val websites: List<CardEntry> = emptyList(),
    val relations: List<CardEntry> = emptyList(),
    val messengers: List<CardEntry> = emptyList(),
    val dates: List<CardEntry> = emptyList(),
    val starred: Boolean = false,
) {
    fun has_content(): Boolean =
        normalized().copy(starred = false) != ContactCard()

    fun normalized(): ContactCard = ContactCard(
        first_name = first_name.trim(),
        middle_name = middle_name.trim(),
        last_name = last_name.trim(),
        prefix = prefix.trim(),
        suffix = suffix.trim(),
        phonetic_first_name = phonetic_first_name.trim(),
        phonetic_middle_name = phonetic_middle_name.trim(),
        phonetic_last_name = phonetic_last_name.trim(),
        nickname = nickname.trim(),
        company = company.trim(),
        department = department.trim(),
        job_title = job_title.trim(),
        notes = notes.trim(),
        birthday = birthday.trim(),
        emails = normalize_entries(emails, EMAIL_TYPES, dedupe_case_insensitive = true),
        phones = normalize_entries(phones, PHONE_TYPES, dedupe_case_insensitive = false),
        addresses = normalize_addresses(addresses),
        websites = normalize_entries(websites, WEBSITE_TYPES, dedupe_case_insensitive = true),
        relations = normalize_entries(relations, RELATION_TYPES, dedupe_case_insensitive = false),
        messengers = normalize_entries(messengers, MESSENGER_TYPES, dedupe_case_insensitive = false),
        dates = normalize_entries(dates, DATE_TYPES, dedupe_case_insensitive = false),
        starred = starred,
    )

    companion object {
        val EMAIL_TYPES = listOf("home", "work", "other")
        val PHONE_TYPES = listOf("mobile", "home", "work", "fax", "pager", "other")
        val ADDRESS_TYPES = listOf("home", "work", "other")
        val WEBSITE_TYPES = listOf("private", "work", "blog", "other")
        val RELATION_TYPES = listOf(
            "assistant", "manager", "spouse", "partner", "child", "parent", "sibling", "friend", "other",
        )
        val MESSENGER_TYPES = listOf("signal", "matrix", "telegram", "whatsapp", "xmpp", "other")
        val DATE_TYPES = listOf("anniversary", "graduation", "wedding", "other")
    }
}

private fun normalize_type(type: String, allowed: List<String>): String {
    val lowered = type.trim().lowercase(java.util.Locale.ROOT)
    return if (lowered in allowed) lowered else "other"
}

private fun normalize_entries(
    entries: List<CardEntry>,
    allowed: List<String>,
    dedupe_case_insensitive: Boolean,
): List<CardEntry> {
    val seen = mutableSetOf<Pair<String, String>>()
    val out = mutableListOf<CardEntry>()
    for (entry in entries) {
        val value = entry.value.trim()
        if (value.isEmpty()) continue
        val type = normalize_type(entry.type, allowed)
        val key = (if (dedupe_case_insensitive) value.lowercase(java.util.Locale.ROOT) else value) to type
        if (!seen.add(key)) continue
        out.add(CardEntry(value, type))
    }
    return out
}

private fun normalize_addresses(addresses: List<CardAddress>): List<CardAddress> {
    val out = mutableListOf<CardAddress>()
    for (address in addresses) {
        val trimmed = CardAddress(
            street = address.street.trim(),
            city = address.city.trim(),
            state = address.state.trim(),
            postal_code = address.postal_code.trim(),
            country = address.country.trim(),
            type = normalize_type(address.type, ContactCard.ADDRESS_TYPES),
        )
        if (trimmed.is_blank() || trimmed in out) continue
        out.add(trimmed)
    }
    return out
}

enum class CardField {
    FIRST_NAME,
    MIDDLE_NAME,
    LAST_NAME,
    PREFIX,
    SUFFIX,
    PHONETIC_FIRST_NAME,
    PHONETIC_MIDDLE_NAME,
    PHONETIC_LAST_NAME,
    NICKNAME,
    COMPANY,
    DEPARTMENT,
    JOB_TITLE,
    NOTES,
    BIRTHDAY,
    EMAILS,
    PHONES,
    ADDRESSES,
    WEBSITES,
    RELATIONS,
    MESSENGERS,
    DATES,
    STARRED,
}

fun ContactCard.field_value(field: CardField): Any = when (field) {
    CardField.FIRST_NAME -> first_name
    CardField.MIDDLE_NAME -> middle_name
    CardField.LAST_NAME -> last_name
    CardField.PREFIX -> prefix
    CardField.SUFFIX -> suffix
    CardField.PHONETIC_FIRST_NAME -> phonetic_first_name
    CardField.PHONETIC_MIDDLE_NAME -> phonetic_middle_name
    CardField.PHONETIC_LAST_NAME -> phonetic_last_name
    CardField.NICKNAME -> nickname
    CardField.COMPANY -> company
    CardField.DEPARTMENT -> department
    CardField.JOB_TITLE -> job_title
    CardField.NOTES -> notes
    CardField.BIRTHDAY -> birthday
    CardField.EMAILS -> emails
    CardField.PHONES -> phones
    CardField.ADDRESSES -> addresses
    CardField.WEBSITES -> websites
    CardField.RELATIONS -> relations
    CardField.MESSENGERS -> messengers
    CardField.DATES -> dates
    CardField.STARRED -> starred
}

fun ContactCard.changed_fields(other: ContactCard): List<CardField> =
    CardField.entries.filter { field_value(it) != other.field_value(it) }

private val base_json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

fun ContactCard.to_base_json(): String = base_json.encodeToString(ContactCard.serializer(), this)

fun contact_card_from_base_json(raw: String?): ContactCard? {
    if (raw.isNullOrBlank()) return null
    return runCatching { base_json.decodeFromString(ContactCard.serializer(), raw) }.getOrNull()
}

private fun JSONObject.str(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return when (val value = opt(key)) {
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> ""
    }
}

private fun JSONObject.entries(key: String): List<CardEntry> {
    val arr = optJSONArray(key) ?: return emptyList()
    val out = mutableListOf<CardEntry>()
    for (i in 0 until arr.length()) {
        val entry = arr.optJSONObject(i) ?: continue
        out.add(CardEntry(entry.str("value"), entry.str("type")))
    }
    return out
}

private fun JSONObject.address(type_fallback: String): CardAddress = CardAddress(
    street = str("street"),
    city = str("city"),
    state = str("state"),
    postal_code = str("postal_code"),
    country = str("country"),
    type = str("type").ifBlank { type_fallback },
)

private fun JSONObject.has_array_entries(key: String): Boolean =
    (optJSONArray(key)?.length() ?: 0) > 0

fun contact_card_from_json(json: String): ContactCard =
    contact_card_from_json_object(JSONObject(json))

fun contact_card_from_json_object(obj: JSONObject): ContactCard {
    val emails = if (obj.has_array_entries("email_entries")) {
        obj.entries("email_entries")
    } else {
        val arr = obj.optJSONArray("emails")
        buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val value = if (arr.isNull(i)) "" else arr.optString(i, "")
                    add(CardEntry(value, "other"))
                }
            }
        }
    }
    val phones = if (obj.has_array_entries("phone_entries")) {
        obj.entries("phone_entries")
    } else {
        listOf(CardEntry(obj.str("phone"), "mobile"))
    }
    val addresses = if (obj.has_array_entries("address_entries")) {
        val arr = obj.getJSONArray("address_entries")
        buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(it.address("home")) }
            }
        }
    } else {
        listOfNotNull(obj.optJSONObject("address")?.address("home"))
    }
    val social_website = obj.optJSONObject("social_links")?.str("website").orEmpty()
    val websites = obj.entries("websites").let { list ->
        if (social_website.isBlank() ||
            list.any { it.value.trim().equals(social_website.trim(), ignoreCase = true) }
        ) {
            list
        } else {
            list + CardEntry(social_website, "private")
        }
    }
    return ContactCard(
        first_name = obj.str("first_name"),
        middle_name = obj.str("middle_name"),
        last_name = obj.str("last_name"),
        prefix = obj.str("title"),
        suffix = obj.str("name_suffix"),
        phonetic_first_name = obj.str("phonetic_first_name"),
        phonetic_middle_name = obj.str("phonetic_middle_name"),
        phonetic_last_name = obj.str("phonetic_last_name"),
        nickname = obj.str("nickname"),
        company = obj.str("company"),
        department = obj.str("department"),
        job_title = obj.str("role").ifBlank { obj.str("job_title") },
        notes = obj.str("notes"),
        birthday = obj.str("birthday"),
        emails = emails,
        phones = phones,
        addresses = addresses,
        websites = websites,
        relations = obj.entries("related_people"),
        messengers = obj.entries("instant_messengers"),
        dates = obj.entries("date_entries"),
        starred = obj.optBoolean("is_favorite", false),
    ).normalized()
}

fun is_trashed_contact_json(json: String): Boolean =
    runCatching { JSONObject(json).str("deleted_at").isNotBlank() }.getOrDefault(false)

private fun JSONObject.put_or_remove(key: String, value: String) {
    if (value.isBlank()) remove(key) else put(key, value)
}

private fun entries_array(entries: List<CardEntry>): JSONArray {
    val arr = JSONArray()
    for (entry in entries) arr.put(JSONObject().put("value", entry.value).put("type", entry.type))
    return arr
}

private fun address_object(address: CardAddress, include_type: Boolean): JSONObject {
    val obj = JSONObject()
    obj.put("street", address.street)
    obj.put("city", address.city)
    obj.put("state", address.state)
    obj.put("postal_code", address.postal_code)
    obj.put("country", address.country)
    if (include_type) obj.put("type", address.type)
    return obj
}

fun write_card_field(obj: JSONObject, card: ContactCard, field: CardField) {
    when (field) {
        CardField.FIRST_NAME -> obj.put("first_name", card.first_name)
        CardField.LAST_NAME -> obj.put("last_name", card.last_name)
        CardField.MIDDLE_NAME -> obj.put_or_remove("middle_name", card.middle_name)
        CardField.PREFIX -> obj.put_or_remove("title", card.prefix)
        CardField.SUFFIX -> obj.put_or_remove("name_suffix", card.suffix)
        CardField.PHONETIC_FIRST_NAME -> obj.put_or_remove("phonetic_first_name", card.phonetic_first_name)
        CardField.PHONETIC_MIDDLE_NAME -> obj.put_or_remove("phonetic_middle_name", card.phonetic_middle_name)
        CardField.PHONETIC_LAST_NAME -> obj.put_or_remove("phonetic_last_name", card.phonetic_last_name)
        CardField.NICKNAME -> obj.put_or_remove("nickname", card.nickname)
        CardField.COMPANY -> obj.put_or_remove("company", card.company)
        CardField.DEPARTMENT -> obj.put_or_remove("department", card.department)
        CardField.JOB_TITLE -> {
            obj.put_or_remove("job_title", card.job_title)
            obj.put_or_remove("role", card.job_title)
        }
        CardField.NOTES -> obj.put_or_remove("notes", card.notes)
        CardField.BIRTHDAY -> obj.put_or_remove("birthday", card.birthday)
        CardField.EMAILS -> {
            obj.put("email_entries", entries_array(card.emails))
            obj.put("emails", JSONArray().also { arr -> card.emails.forEach { arr.put(it.value) } })
        }
        CardField.PHONES -> {
            obj.put("phone_entries", entries_array(card.phones))
            obj.put_or_remove("phone", card.phones.firstOrNull()?.value.orEmpty())
        }
        CardField.ADDRESSES -> {
            val arr = JSONArray()
            card.addresses.forEach { arr.put(address_object(it, include_type = true)) }
            obj.put("address_entries", arr)
            val first = card.addresses.firstOrNull()
            if (first == null) obj.remove("address") else obj.put("address", address_object(first, include_type = false))
        }
        CardField.WEBSITES -> {
            obj.put("websites", entries_array(card.websites))
            val social = obj.optJSONObject("social_links")
            if (social != null) {
                val first = card.websites.firstOrNull()?.value.orEmpty()
                social.put_or_remove("website", first)
                if (social.length() == 0) obj.remove("social_links")
            }
        }
        CardField.RELATIONS -> obj.put("related_people", entries_array(card.relations))
        CardField.MESSENGERS -> obj.put("instant_messengers", entries_array(card.messengers))
        CardField.DATES -> obj.put("date_entries", entries_array(card.dates))
        CardField.STARRED -> obj.put("is_favorite", card.starred)
    }
}

fun patch_contact_json(remote_json: String, remote_card: ContactCard, target: ContactCard): String {
    val obj = JSONObject(remote_json)
    for (field in target.changed_fields(remote_card)) write_card_field(obj, target, field)
    return obj.toString()
}

fun new_contact_json(card: ContactCard): String {
    val obj = JSONObject()
    obj.put("first_name", "")
    obj.put("last_name", "")
    obj.put("emails", JSONArray())
    obj.put("is_favorite", false)
    for (field in CardField.entries) {
        if (card.field_value(field) != ContactCard().field_value(field)) write_card_field(obj, card, field)
    }
    return obj.toString()
}

fun trash_contact_json(remote_json: String, deleted_at: String): String =
    JSONObject(remote_json).put("deleted_at", deleted_at).toString()
