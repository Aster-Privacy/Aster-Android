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

import java.util.Locale
import org.astermail.android.ui.contacts.Contact
import org.json.JSONArray
import org.json.JSONObject

data class CsvColumn(
    val target: String?,
    val key: String,
    val part: String,
    val implied_type: String? = null,
)

private val NO_COLUMN = CsvColumn(null, "", "value")

private val ADDRESS_PART_TARGETS: Map<String, String?> = mapOf(
    "street" to "street",
    "street 2" to null,
    "street 3" to null,
    "address" to "street",
    "city" to "city",
    "state" to "state",
    "province" to "state",
    "region" to "state",
    "postal code" to "postal_code",
    "zip" to "postal_code",
    "zip code" to "postal_code",
    "postcode" to "postal_code",
    "country" to "country",
    "country/region" to "country",
)

private val ADDRESS_PART_KINDS = mapOf(
    "street 2" to "extended",
    "street 3" to "extended",
    "po box" to "extended",
    "extended address" to "extended",
    "formatted" to "formatted",
    "label" to "label",
    "type" to "label",
)

private val PHONE_KIND_WORDS = mapOf(
    "home" to "home",
    "business" to "work",
    "work" to "work",
    "company" to "work",
    "company main" to "work",
    "assistant's" to "work",
    "mobile" to "mobile",
    "cell" to "mobile",
    "car" to "mobile",
    "other" to "other",
    "primary" to "other",
    "radio" to "other",
    "callback" to "other",
    "telex" to "other",
    "tty/tdd" to "other",
    "isdn" to "other",
)

private val PLACE_WORDS = mapOf(
    "home" to "home",
    "personal" to "home",
    "business" to "work",
    "work" to "work",
    "other" to "other",
)

private val SIMPLE_HEADERS = mapOf(
    "first name" to "first_name",
    "given name" to "first_name",
    "first" to "first_name",
    "given" to "first_name",
    "middle name" to "middle_name",
    "additional name" to "middle_name",
    "middle" to "middle_name",
    "last name" to "last_name",
    "family name" to "last_name",
    "surname" to "last_name",
    "last" to "last_name",
    "name prefix" to "name_prefix",
    "prefix" to "name_prefix",
    "honorific prefix" to "name_prefix",
    "salutation" to "name_prefix",
    "name suffix" to "name_suffix",
    "suffix" to "name_suffix",
    "honorific suffix" to "name_suffix",
    "nickname" to "nickname",
    "nick name" to "nickname",
    "short name" to "nickname",
    "name" to "full_name",
    "full name" to "full_name",
    "display name" to "full_name",
    "contact name" to "full_name",
    "email" to "emails",
    "e-mail" to "emails",
    "email address" to "emails",
    "e-mail address" to "emails",
    "mail" to "emails",
    "emails" to "emails",
    "phone" to "phone",
    "telephone" to "phone",
    "tel" to "phone",
    "phone number" to "phone",
    "primary phone" to "phone",
    "mobile" to "phone",
    "mobile phone" to "phone",
    "mobile number" to "phone",
    "cell" to "phone",
    "cell phone" to "phone",
    "pager" to "phone",
    "fax" to "phone",
    "fax number" to "phone",
    "company" to "company",
    "company name" to "company",
    "organization" to "company",
    "organisation" to "company",
    "organization name" to "company",
    "job title" to "job_title",
    "title" to "job_title",
    "position" to "job_title",
    "role" to "job_title",
    "occupation" to "job_title",
    "profession" to "job_title",
    "department" to "department",
    "street" to "street",
    "street address" to "street",
    "address" to "street",
    "address line 1" to "street",
    "address 1" to "street",
    "city" to "city",
    "town" to "city",
    "locality" to "city",
    "state" to "state",
    "region" to "state",
    "province" to "state",
    "county" to "state",
    "postal code" to "postal_code",
    "zip" to "postal_code",
    "zip code" to "postal_code",
    "postcode" to "postal_code",
    "country" to "country",
    "country/region" to "country",
    "website" to "website",
    "web page" to "website",
    "web site" to "website",
    "url" to "website",
    "homepage" to "website",
    "home page" to "website",
    "personal web page" to "website",
    "business web page" to "website",
    "birthday" to "birthday",
    "birth date" to "birthday",
    "date of birth" to "birthday",
    "dob" to "birthday",
    "anniversary" to "event",
    "spouse" to "related_person",
    "partner" to "related_person",
    "manager's name" to "related_person",
    "manager" to "related_person",
    "assistant's name" to "related_person",
    "assistant" to "related_person",
    "children" to "related_person",
    "child" to "related_person",
    "im address" to "instant_messenger",
    "imaddress" to "instant_messenger",
    "im" to "instant_messenger",
    "instant messenger" to "instant_messenger",
    "notes" to "notes",
    "note" to "notes",
    "comment" to "notes",
    "comments" to "notes",
    "description" to "notes",
    "group membership" to "groups",
    "labels" to "groups",
    "categories" to "groups",
    "groups" to "groups",
    "group" to "groups",
    "tags" to "groups",
    "favorite" to "is_favorite",
    "favourite" to "is_favorite",
    "starred" to "is_favorite",
    "is favorite" to "is_favorite",
)

private val IMPLIED_TYPES = mapOf(
    "mobile" to "mobile",
    "mobile phone" to "mobile",
    "mobile number" to "mobile",
    "cell" to "mobile",
    "cell phone" to "mobile",
    "pager" to "pager",
    "fax" to "fax",
    "fax number" to "fax",
    "anniversary" to "anniversary",
    "spouse" to "spouse",
    "partner" to "partner",
    "manager's name" to "manager",
    "manager" to "manager",
    "assistant's name" to "assistant",
    "assistant" to "assistant",
    "children" to "child",
    "child" to "child",
    "personal web page" to "private",
    "home page" to "private",
    "homepage" to "private",
    "business web page" to "work",
)

private val ORG_PART_TARGETS = mapOf(
    "name" to "company",
    "title" to "job_title",
    "department" to "department",
)

private val DATE_TYPES = mapOf(
    "anniversary" to "anniversary",
    "graduation" to "graduation",
    "wedding" to "wedding",
)

private val RELATION_TYPES = mapOf(
    "assistant" to "assistant",
    "manager" to "manager",
    "supervisor" to "manager",
    "spouse" to "spouse",
    "partner" to "partner",
    "child" to "child",
    "parent" to "parent",
    "father" to "parent",
    "mother" to "parent",
    "sibling" to "sibling",
    "brother" to "sibling",
    "sister" to "sibling",
    "friend" to "friend",
)

private val MESSENGER_TYPES = mapOf(
    "signal" to "signal",
    "matrix" to "matrix",
    "telegram" to "telegram",
    "tg" to "telegram",
    "whatsapp" to "whatsapp",
    "xmpp" to "xmpp",
    "jabber" to "xmpp",
)

private val WEBSITE_TYPES = mapOf(
    "home" to "private",
    "private" to "private",
    "personal" to "private",
    "work" to "work",
    "blog" to "blog",
)

private val ADDRESS_TARGETS = setOf("street", "city", "state", "postal_code", "country")

private val EMAIL_INDEXED = Regex("^e-?mail (\\d+) - (value|label|type)$")
private val EMAIL_NUMBERED = Regex("^e-?mail(?: (\\d+))?(?: address)?$")
private val EMAIL_META = Regex("^e-?mail(?: (\\d+))? (display name|type)$")
private val EMAIL_PLACED = Regex("^(home|business|work|other|personal) (e-?mail|email address|e-mail address)(?: (\\d+))?$")
private val PHONE_INDEXED = Regex("^phone (\\d+) - (value|label|type)$")
private val PHONE_KINDED = Regex(
    "^(home|business|work|company|company main|assistant's|mobile|cell|car|other|primary|radio|callback|telex|tty/tdd|isdn) (phone|fax|telephone)(?: (\\d+))?$",
)
private val ADDRESS_INDEXED = Regex("^address (\\d+) - (.+)$")
private val ADDRESS_PLACED = Regex("^(home|business|work|other) (.+)$")
private val ADDRESS_SECOND_LINE = Regex("^(address line 2|address 2|street 2|street 3)$")
private val ORG_INDEXED = Regex("^organization(?: (\\d+))? - (.+)$")
private val ORG_NAMED = Regex("^organization (name|title|department)$")
private val WEBSITE_INDEXED = Regex("^website (\\d+) - (value|label|type)$")
private val EVENT_INDEXED = Regex("^event (\\d+) - (value|label|type)$")
private val RELATION_INDEXED = Regex("^relation (\\d+) - (value|label|type)$")
private val IM_INDEXED = Regex("^im (\\d+) - (value|label|type|service)$")
private val CUSTOM_INDEXED = Regex("^custom field (\\d+) - (value|label|type)$")
private val FAVORITE_VALUES = Regex("^(true|yes|y|1|starred)$", RegexOption.IGNORE_CASE)
private val ANGLED_EMAIL = Regex("<([^>]+)>")
private val MULTI_VALUE_SEPARATOR = " ::: "

private fun normalize_csv_header(header: String): String =
    header.trim().lowercase(Locale.ROOT).replace('_', ' ').replace(Regex("\\s+"), " ")

private fun part_of(word: String): String = if (word == "value") "value" else "label"

fun describe_csv_column(header: String): CsvColumn {
    val lower = normalize_csv_header(header)
    if (lower.isEmpty()) return NO_COLUMN

    EMAIL_INDEXED.matchEntire(lower)?.let { match ->
        val part = part_of(match.groupValues[2])
        return CsvColumn(if (part == "value") "emails" else null, "email:${match.groupValues[1]}", part)
    }
    EMAIL_NUMBERED.matchEntire(lower)?.let { match ->
        if (match.groupValues[1].isNotEmpty()) {
            return CsvColumn("emails", "email:${match.groupValues[1]}", "value")
        }
    }
    if (EMAIL_META.matchEntire(lower) != null) return NO_COLUMN
    EMAIL_PLACED.matchEntire(lower)?.let { match ->
        val index = match.groupValues[3].ifEmpty { "1" }
        return CsvColumn("emails", "email:${match.groupValues[1]}:$index", "value", PLACE_WORDS[match.groupValues[1]])
    }
    PHONE_INDEXED.matchEntire(lower)?.let { match ->
        val part = part_of(match.groupValues[2])
        return CsvColumn(if (part == "value") "phone" else null, "phone:${match.groupValues[1]}", part)
    }
    PHONE_KINDED.matchEntire(lower)?.let { match ->
        val kind = match.groupValues[1]
        val medium = match.groupValues[2]
        val index = match.groupValues[3].ifEmpty { "1" }
        val implied = if (medium == "fax") "fax" else PHONE_KIND_WORDS[kind]
        return CsvColumn("phone", "phone:$kind $medium:$index", "value", implied)
    }
    ADDRESS_INDEXED.matchEntire(lower)?.let { match ->
        val key = "address:${match.groupValues[1]}"
        ADDRESS_PART_KINDS[match.groupValues[2]]?.let { kind -> return CsvColumn(null, key, kind) }
        if (!ADDRESS_PART_TARGETS.containsKey(match.groupValues[2])) return NO_COLUMN
        return CsvColumn(ADDRESS_PART_TARGETS[match.groupValues[2]], key, "value")
    }
    ADDRESS_PLACED.matchEntire(lower)?.let { match ->
        val part_name = match.groupValues[2]
        if (ADDRESS_PART_TARGETS.containsKey(part_name)) {
            val place = PLACE_WORDS[match.groupValues[1]]
            val key = "address:$place"
            ADDRESS_PART_KINDS[part_name]?.let { kind -> return CsvColumn(null, key, kind) }
            return CsvColumn(ADDRESS_PART_TARGETS[part_name], key, "value", place)
        }
    }
    if (ADDRESS_SECOND_LINE.matchEntire(lower) != null) return CsvColumn(null, "address:1", "extended")
    ORG_INDEXED.matchEntire(lower)?.let { match ->
        val index = match.groupValues[1].ifEmpty { "1" }
        return CsvColumn(ORG_PART_TARGETS[match.groupValues[2]], "org:$index", "value")
    }
    ORG_NAMED.matchEntire(lower)?.let { match ->
        return CsvColumn(ORG_PART_TARGETS[match.groupValues[1]], "org:1", "value")
    }
    WEBSITE_INDEXED.matchEntire(lower)?.let { match ->
        val part = part_of(match.groupValues[2])
        return CsvColumn(if (part == "value") "website" else null, "website:${match.groupValues[1]}", part)
    }
    EVENT_INDEXED.matchEntire(lower)?.let { match ->
        val part = part_of(match.groupValues[2])
        return CsvColumn(if (part == "value") "event" else null, "event:${match.groupValues[1]}", part)
    }
    RELATION_INDEXED.matchEntire(lower)?.let { match ->
        val part = part_of(match.groupValues[2])
        return CsvColumn(if (part == "value") "related_person" else null, "relation:${match.groupValues[1]}", part)
    }
    IM_INDEXED.matchEntire(lower)?.let { match ->
        val part = when (match.groupValues[2]) {
            "value" -> "value"
            "service" -> "service"
            else -> "label"
        }
        return CsvColumn(if (part == "value") "instant_messenger" else null, "im:${match.groupValues[1]}", part)
    }
    CUSTOM_INDEXED.matchEntire(lower)?.let { match ->
        val part = part_of(match.groupValues[2])
        return CsvColumn(if (part == "value") "notes" else null, "custom:${match.groupValues[1]}", part)
    }

    val simple = SIMPLE_HEADERS[lower] ?: return NO_COLUMN
    val key = if (simple in ADDRESS_TARGETS) "address:1" else "$simple:$lower"
    return CsvColumn(simple, key, "value", IMPLIED_TYPES[lower])
}

fun auto_map_csv_headers(headers: List<String>): List<String?> {
    val columns = headers.map { describe_csv_column(it) }
    val normalized = headers.map { normalize_csv_header(it) }
    val has_other_job_title = columns.indices.any { columns[it].target == "job_title" && normalized[it] != "title" }
    val has_name_suffix = columns.any { it.target == "name_suffix" }
    return headers.indices.map { index ->
        if (normalized[index] == "title" && (has_other_job_title || has_name_suffix)) {
            "name_prefix"
        } else {
            columns[index].target
        }
    }
}

private class CsvSlot {
    val values = mutableListOf<String>()
    var label: String? = null
    var service: String? = null
    var implied_type: String? = null
}

private class CsvAddressSlot(var implied_type: String?) {
    var label: String? = null
    var street: String? = null
    var extended: String? = null
    var formatted: String? = null
    var city: String? = null
    var state: String? = null
    var postal_code: String? = null
    var country: String? = null
}

private fun strip_csv_guard(value: String): String = if (value.startsWith("'")) value.drop(1) else value

private fun split_multi_values(value: String, extra: Regex?): List<String> {
    val out = mutableListOf<String>()
    for (piece in value.split(MULTI_VALUE_SEPARATOR)) {
        val parts = if (extra != null) piece.split(extra) else listOf(piece)
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) out.add(trimmed)
        }
    }
    return out
}

private val SPLIT_LIST = Regex("[;,]")
private val SPLIT_SEMICOLON = Regex(";")

private fun clean_label(label: String?): String =
    (label ?: "").replace(Regex("^\\*\\s*"), "").trim().lowercase(Locale.ROOT)

private fun email_type_of(label: String): String = when {
    label.contains("work") || label.contains("business") -> "work"
    label.contains("home") || label.contains("personal") -> "home"
    else -> "other"
}

private fun phone_type_of(label: String): String = when {
    label.contains("fax") -> "fax"
    label.contains("pager") -> "pager"
    label.contains("mobile") || label.contains("cell") || label.contains("iphone") -> "mobile"
    label.contains("work") || label.contains("business") -> "work"
    label.contains("home") -> "home"
    else -> "other"
}

private fun place_type_of(label: String): String = when {
    label.contains("work") || label.contains("business") -> "work"
    label.contains("home") || label.contains("personal") -> "home"
    else -> "other"
}

private fun mapped_type(table: Map<String, String>, candidates: List<String>, fallback: String): String {
    for (candidate in candidates) {
        table[candidate]?.let { return it }
    }
    return fallback
}

private fun website_type_of(label: String): String =
    mapped_type(WEBSITE_TYPES, listOf(label, label.substringBefore(' ')), "other")

private fun clean_email(value: String): String {
    val stripped = value.replace(Regex("^mailto:", RegexOption.IGNORE_CASE), "").trim()
    val angled = ANGLED_EMAIL.find(stripped)
    return (angled?.groupValues?.get(1) ?: stripped).trim()
}

private fun split_full_name(full_name: String): Pair<String, String> {
    val trimmed = full_name.trim().replace(Regex("\\s+"), " ")
    val separator = trimmed.lastIndexOf(' ')
    if (separator <= 0) return trimmed to ""
    return trimmed.substring(0, separator) to trimmed.substring(separator + 1)
}

private fun social_host_of(url: String): String? {
    val lower = url.lowercase(Locale.ROOT)
    return when {
        lower.contains("linkedin.com") -> "linkedin"
        lower.contains("twitter.com") || lower.contains("x.com/") -> "twitter"
        lower.contains("github.com") -> "github"
        else -> null
    }
}

private fun <T> MutableMap<String, T>.slot(key: String, create: () -> T): T = getOrPut(key, create)

private fun typed_entries(entries: List<Pair<String, String>>): JSONArray {
    val array = JSONArray()
    for ((value, type) in entries) array.put(JSONObject().put("value", value).put("type", type))
    return array
}

private fun put_if_present(obj: JSONObject, key: String, value: String) {
    if (value.isNotBlank()) obj.put(key, value)
}

fun parse_csv_contacts(text: String): List<Contact> {
    val rows = parse_csv_rows(text)
    if (rows.size < 2) return emptyList()

    val headers = rows.first()
    val columns = headers.map { describe_csv_column(it) }
    val mapping = auto_map_csv_headers(headers)
    val contacts = mutableListOf<Contact>()

    for (values in rows.drop(1)) {
        if (contacts.size >= MAX_IMPORTED_CONTACTS) break
        build_csv_contact(headers, columns, mapping, values)?.let { contacts.add(it) }
    }

    return contacts
}

private fun build_csv_contact(
    headers: List<String>,
    columns: List<CsvColumn>,
    mapping: List<String?>,
    values: List<String>,
): Contact? {
    val singles = mutableMapOf<String, String>()
    val emails = linkedMapOf<String, CsvSlot>()
    val phones = linkedMapOf<String, CsvSlot>()
    val websites = linkedMapOf<String, CsvSlot>()
    val events = linkedMapOf<String, CsvSlot>()
    val relations = linkedMapOf<String, CsvSlot>()
    val messengers = linkedMapOf<String, CsvSlot>()
    val custom_fields = linkedMapOf<String, CsvSlot>()
    val addresses = linkedMapOf<String, CsvAddressSlot>()
    val notes = mutableListOf<String>()
    val groups = mutableListOf<String>()
    var full_name = ""
    var is_favorite = false

    val slot_maps = mapOf(
        "email" to emails,
        "phone" to phones,
        "website" to websites,
        "event" to events,
        "relation" to relations,
        "im" to messengers,
        "custom" to custom_fields,
    )

    for (index in headers.indices) {
        val raw = values.getOrNull(index) ?: continue
        val value = strip_csv_guard(raw).trim()
        if (value.isEmpty()) continue
        val column = columns[index]
        val mapped = mapping[index]

        if (column.part != "value" && column.key.isNotEmpty()) {
            val kind = column.key.substringBefore(':')
            if (kind == "address") {
                val slot = addresses.slot(column.key) { CsvAddressSlot(null) }
                when (column.part) {
                    "label" -> slot.label = value
                    "extended" -> slot.extended = value
                    "formatted" -> slot.formatted = value
                }
                continue
            }
            slot_maps[kind]?.let { map ->
                val slot = map.slot(column.key) { CsvSlot() }
                if (column.part == "service") slot.service = value else slot.label = value
            }
            continue
        }

        if (mapped == null) continue
        val same_target = mapped == column.target
        val key = if (same_target) column.key else "$mapped:${headers[index]}"
        val implied_type = if (same_target) column.implied_type else null

        when (mapped) {
            "full_name" -> full_name = value
            "first_name", "last_name", "company", "job_title", "birthday",
            "middle_name", "nickname", "department", "name_suffix", "name_prefix",
            -> singles.putIfAbsent(mapped, value)
            "emails" -> emails.slot(key) { CsvSlot() }.also {
                it.implied_type = implied_type
                it.values.addAll(split_multi_values(value, SPLIT_LIST))
            }
            "phone" -> phones.slot(key) { CsvSlot() }.also {
                it.implied_type = implied_type
                it.values.addAll(split_multi_values(value, SPLIT_SEMICOLON))
            }
            "website" -> websites.slot(key) { CsvSlot() }.also {
                it.implied_type = implied_type
                it.values.addAll(split_multi_values(value, SPLIT_SEMICOLON))
            }
            "event" -> events.slot(key) { CsvSlot() }.also {
                it.implied_type = implied_type
                it.values.addAll(split_multi_values(value, null))
            }
            "related_person" -> relations.slot(key) { CsvSlot() }.also {
                it.implied_type = implied_type
                it.values.addAll(split_multi_values(value, SPLIT_SEMICOLON))
            }
            "instant_messenger" -> messengers.slot(key) { CsvSlot() }.also {
                it.implied_type = implied_type
                it.values.addAll(split_multi_values(value, SPLIT_SEMICOLON))
            }
            "street", "city", "state", "postal_code", "country" -> {
                val slot = addresses.slot(key) { CsvAddressSlot(implied_type) }
                when (mapped) {
                    "street" -> if (slot.street == null) slot.street = value
                    "city" -> if (slot.city == null) slot.city = value
                    "state" -> if (slot.state == null) slot.state = value
                    "postal_code" -> if (slot.postal_code == null) slot.postal_code = value
                    "country" -> if (slot.country == null) slot.country = value
                }
            }
            "notes" -> if (key.startsWith("custom:")) {
                custom_fields.slot(key) { CsvSlot() }.values.add(value)
            } else {
                notes.add(value)
            }
            "groups" -> groups.addAll(split_multi_values(value, SPLIT_LIST))
            "is_favorite" -> is_favorite = FAVORITE_VALUES.matches(value)
        }
    }

    var first_name = singles["first_name"].orEmpty()
    var last_name = singles["last_name"].orEmpty()
    if (first_name.isEmpty() && last_name.isEmpty() && full_name.isNotEmpty()) {
        val (first, last) = split_full_name(full_name)
        first_name = first
        last_name = last
    }

    val email_entries = mutableListOf<Pair<String, String>>()
    val seen_emails = mutableSetOf<String>()
    for (slot in emails.values) {
        val type = email_type_of(clean_label(slot.label ?: slot.implied_type))
        for (raw_email in slot.values) {
            val email = clean_email(raw_email)
            val lower = email.lowercase(Locale.ROOT)
            if (email.isEmpty() || !seen_emails.add(lower)) continue
            email_entries.add(email to type)
        }
    }
    val ordered_emails = order_work_second(email_entries)

    val phone_entries = mutableListOf<Pair<String, String>>()
    val seen_phones = mutableSetOf<String>()
    for (slot in phones.values) {
        val type = phone_type_of(clean_label(slot.label ?: slot.implied_type))
        for (phone in slot.values) {
            val digits = phone.replace(Regex("[^\\d+]"), "")
            if (!seen_phones.add(digits.ifEmpty { phone })) continue
            phone_entries.add(phone to type)
        }
    }
    val ordered_phones = order_work_second(phone_entries)

    val address_entries = mutableListOf<JSONObject>()
    for (slot in addresses.values) {
        val street = listOfNotNull(slot.street, slot.extended).joinToString(", ")
        val entry = JSONObject().put("type", place_type_of(clean_label(slot.label ?: slot.implied_type)))
        if (street.isNotEmpty()) {
            entry.put("street", street)
        } else if (slot.formatted != null && slot.city == null && slot.postal_code == null) {
            entry.put("street", slot.formatted!!.replace(Regex("\\s*\\n\\s*"), ", "))
        }
        slot.city?.let { entry.put("city", it) }
        slot.state?.let { entry.put("state", it) }
        slot.postal_code?.let { entry.put("postal_code", it) }
        slot.country?.let { entry.put("country", it) }
        if (entry.length() > 1) address_entries.add(entry)
    }

    val website_entries = mutableListOf<Pair<String, String>>()
    val social_links = JSONObject()
    for (slot in websites.values) {
        val type = website_type_of(clean_label(slot.label ?: slot.implied_type))
        for (url in slot.values) {
            val social = social_host_of(url)
            if (social != null && !social_links.has(social)) {
                social_links.put(social, url)
                continue
            }
            if (!social_links.has("website")) social_links.put("website", url)
            website_entries.add(url to type)
        }
    }

    var birthday = singles["birthday"].orEmpty()
    val date_entries = mutableListOf<Pair<String, String>>()
    for (slot in events.values) {
        val label = clean_label(slot.label ?: slot.implied_type)
        for (date in slot.values) {
            if (label == "birthday") {
                if (birthday.isEmpty()) birthday = date
                continue
            }
            date_entries.add(date to mapped_type(DATE_TYPES, listOf(label), "other"))
        }
    }

    val related_people = mutableListOf<Pair<String, String>>()
    for (slot in relations.values) {
        val label = clean_label(slot.label ?: slot.implied_type)
        for (person in slot.values) {
            related_people.add(person to mapped_type(RELATION_TYPES, listOf(label), "other"))
        }
    }

    val instant_messengers = mutableListOf<Pair<String, String>>()
    for (slot in messengers.values) {
        val label = clean_label(slot.service ?: slot.label ?: slot.implied_type)
        for (handle in slot.values) {
            instant_messengers.add(handle to mapped_type(MESSENGER_TYPES, listOf(label), "other"))
        }
    }

    for (slot in custom_fields.values) {
        val label = (slot.label ?: "").replace(Regex("^\\*\\s*"), "").trim()
        for (value in slot.values) notes.add(if (label.isNotEmpty()) "$label: $value" else value)
    }

    val group_names = mutableListOf<String>()
    for (raw_group in groups) {
        val name = raw_group.replace(Regex("^\\*\\s*"), "").trim()
        val lower = name.lowercase(Locale.ROOT)
        if (name.isEmpty() || lower == "mycontacts" || lower == "my contacts") continue
        if (lower == "starred") {
            is_favorite = true
            continue
        }
        if (group_names.none { it.lowercase(Locale.ROOT) == lower }) group_names.add(name)
    }

    if (first_name.isEmpty() && last_name.isEmpty() && ordered_emails.isEmpty()) return null

    val primary_address = address_entries.firstOrNull()
    val raw = JSONObject()
    put_if_present(raw, "middle_name", singles["middle_name"].orEmpty())
    put_if_present(raw, "title", singles["name_prefix"].orEmpty())
    put_if_present(raw, "name_suffix", singles["name_suffix"].orEmpty())
    put_if_present(raw, "nickname", singles["nickname"].orEmpty())
    put_if_present(raw, "department", singles["department"].orEmpty())
    if (ordered_emails.isNotEmpty()) raw.put("email_entries", typed_entries(ordered_emails))
    if (ordered_phones.isNotEmpty()) raw.put("phone_entries", typed_entries(ordered_phones))
    if (address_entries.isNotEmpty()) raw.put("address_entries", JSONArray(address_entries))
    if (website_entries.isNotEmpty()) raw.put("websites", typed_entries(website_entries))
    if (date_entries.isNotEmpty()) raw.put("date_entries", typed_entries(date_entries))
    if (related_people.isNotEmpty()) raw.put("related_people", typed_entries(related_people))
    if (instant_messengers.isNotEmpty()) raw.put("instant_messengers", typed_entries(instant_messengers))
    if (social_links.has("github")) {
        raw.put("social_links", JSONObject().put("github", social_links.getString("github")))
    }

    val name = listOf(first_name, last_name).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { ordered_emails.first().first }

    return Contact(
        id = "",
        name = name,
        email = ordered_emails.firstOrNull()?.first.orEmpty(),
        work_email = work_slot(ordered_emails),
        phone = ordered_phones.firstOrNull()?.first.orEmpty(),
        work_phone = work_slot(ordered_phones),
        company = singles["company"].orEmpty(),
        title = singles["job_title"].orEmpty(),
        address = primary_address?.optString("street", "").orEmpty(),
        city = primary_address?.optString("city", "").orEmpty(),
        region = primary_address?.optString("state", "").orEmpty(),
        postal_code = primary_address?.optString("postal_code", "").orEmpty(),
        country = primary_address?.optString("country", "").orEmpty(),
        website = social_links.optString("website", ""),
        twitter = social_links.optString("twitter", ""),
        linkedin = social_links.optString("linkedin", ""),
        birthday = birthday,
        notes = notes.joinToString("\n"),
        is_favorite = is_favorite,
        groups = group_names,
        raw_json = if (raw.length() > 0) raw.toString() else "",
    )
}

private fun work_slot(entries: List<Pair<String, String>>): String {
    val second = entries.getOrNull(1) ?: return ""
    return if (second.second == "work") second.first else ""
}

private fun order_work_second(entries: List<Pair<String, String>>): List<Pair<String, String>> {
    if (entries.size < 2) return entries
    val primary_index = entries.indices.firstOrNull { entries[it].second != "work" } ?: 0
    val work_index = entries.indices.firstOrNull { it != primary_index && entries[it].second == "work" }
    val rest = entries.filterIndexed { index, _ -> index != primary_index && index != work_index }
    val work = if (work_index != null) entries[work_index] else rest.first().first to "work"
    val remaining = if (work_index != null) rest else rest.drop(1)
    return listOf(entries[primary_index], work) + remaining
}
