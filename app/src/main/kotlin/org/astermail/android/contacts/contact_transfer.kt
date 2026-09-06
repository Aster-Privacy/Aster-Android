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

const val MAX_IMPORTED_CONTACTS = 5000

val CSV_HEADERS = listOf(
    "First name",
    "Last name",
    "Email",
    "Phone",
    "Company",
    "Job title",
    "Street",
    "City",
    "State",
    "Postal code",
    "Country",
    "Website",
    "Birthday",
    "Notes",
    "Favorite",
)

private fun escape_vcard(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

private fun unescape_vcard(value: String): String {
    val out = StringBuilder()
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '\\' && index + 1 < value.length) {
            when (val next = value[index + 1]) {
                'n', 'N' -> out.append('\n')
                else -> out.append(next)
            }
            index += 2
        } else {
            out.append(character)
            index += 1
        }
    }
    return out.toString()
}

private val photo_data_uri = Regex("^data:image/([A-Za-z0-9.+-]+);base64,(.+)$")

private fun fold_line(line: String): String {
    if (line.length <= 75) return line
    val parts = mutableListOf<String>()
    var current = StringBuilder()
    var limit = 75
    for (character in line) {
        if (current.length + 1 > limit) {
            parts.add(if (parts.isEmpty()) current.toString() else " $current")
            current = StringBuilder()
            limit = 74
        }
        current.append(character)
    }
    if (current.isNotEmpty()) {
        parts.add(if (parts.isEmpty()) current.toString() else " $current")
    }
    return parts.joinToString("\r\n")
}

private fun unfold(text: String): List<String> {
    val raw = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val lines = mutableListOf<String>()
    for (line in raw) {
        if (line.startsWith(" ") || line.startsWith("\t")) {
            if (lines.isNotEmpty()) {
                lines[lines.size - 1] = lines[lines.size - 1] + line.substring(1)
                continue
            }
        }
        lines.add(line)
    }
    return lines
}

private fun split_vcard_value(value: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '\\' && index + 1 < value.length) {
            current.append(character).append(value[index + 1])
            index += 2
            continue
        }
        if (character == ';') {
            parts.add(current.toString())
            current.setLength(0)
            index += 1
            continue
        }
        current.append(character)
        index += 1
    }
    parts.add(current.toString())
    return parts.map { unescape_vcard(it) }
}

private data class VcardLine(val key: String, val params: List<String>, val value: String)

private fun parse_line(line: String): VcardLine? {
    val separator = line.indexOf(':')
    if (separator <= 0) return null
    val head = line.substring(0, separator)
    val value = line.substring(separator + 1)
    val segments = head.split(";")
    val key = segments.first().substringAfter('.').uppercase(Locale.ROOT)
    val params = segments.drop(1).map { it.trim() }
    return VcardLine(key, params, value)
}

private fun types_of(params: List<String>): List<String> =
    params
        .filter { it.uppercase(Locale.ROOT).startsWith("TYPE=") || !it.contains("=") }
        .flatMap { it.substringAfter("=").split(",") }
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }

private fun param_value(params: List<String>, name: String): String =
    params
        .firstOrNull { it.substringBefore("=").trim().equals(name, ignoreCase = true) }
        ?.substringAfter("=")
        ?.trim()
        .orEmpty()

private fun split_name(name: String): Pair<String, String> {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "" to ""
    val index = trimmed.lastIndexOf(' ')
    if (index <= 0) return trimmed to ""
    return trimmed.substring(0, index) to trimmed.substring(index + 1)
}

fun contact_to_vcard(contact: Contact): String {
    val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
    val (first, last) = split_name(contact.name)

    fun push(line: String) = lines.add(fold_line(line))
    fun push_raw(key: String, value: String) =
        lines.add(fold_line("$key:" + value.replace("\r", "").replace("\n", "")))

    fun push_photo(value: String) {
        val match = photo_data_uri.matchEntire(value)
        if (match == null) {
            push_raw("PHOTO;VALUE=URI", value)
            return
        }
        val media = match.groupValues[1].uppercase(Locale.ROOT)
        push_raw("PHOTO;ENCODING=b;TYPE=$media", match.groupValues[2])
    }

    push("N:${escape_vcard(last)};${escape_vcard(first)};;;")
    push("FN:${escape_vcard(contact.name.ifBlank { contact.email })}")
    if (contact.email.isNotBlank()) {
        push("EMAIL;TYPE=INTERNET;TYPE=HOME:${escape_vcard(contact.email)}")
    }
    if (contact.work_email.isNotBlank()) {
        push("EMAIL;TYPE=INTERNET;TYPE=WORK:${escape_vcard(contact.work_email)}")
    }
    if (contact.phone.isNotBlank()) push("TEL;TYPE=CELL:${escape_vcard(contact.phone)}")
    if (contact.work_phone.isNotBlank()) push("TEL;TYPE=WORK:${escape_vcard(contact.work_phone)}")
    if (contact.company.isNotBlank()) push("ORG:${escape_vcard(contact.company)}")
    if (contact.title.isNotBlank()) push("TITLE:${escape_vcard(contact.title)}")
    if (contact.birthday.isNotBlank()) push("BDAY:${escape_vcard(contact.birthday)}")

    val has_address = listOf(
        contact.address,
        contact.city,
        contact.region,
        contact.postal_code,
        contact.country,
    ).any { it.isNotBlank() }
    if (has_address) {
        push(
            "ADR;TYPE=HOME:;;${escape_vcard(contact.address)};${escape_vcard(contact.city)};" +
                "${escape_vcard(contact.region)};${escape_vcard(contact.postal_code)};" +
                escape_vcard(contact.country),
        )
    }
    if (contact.website.isNotBlank()) push_raw("URL", contact.website)
    if (contact.twitter.isNotBlank()) {
        push("X-SOCIALPROFILE;TYPE=TWITTER:${escape_vcard(contact.twitter)}")
    }
    if (contact.linkedin.isNotBlank()) {
        push("X-SOCIALPROFILE;TYPE=LINKEDIN:${escape_vcard(contact.linkedin)}")
    }
    if (contact.groups.isNotEmpty()) {
        push("CATEGORIES:${contact.groups.joinToString(",") { escape_vcard(it) }}")
    }
    if (contact.is_favorite) push("X-ASTER-FAVORITE:true")
    if (contact.profile_color.isNotBlank()) {
        push("X-ASTER-COLOR:${escape_vcard(contact.profile_color)}")
    }
    if (contact.notes.isNotBlank()) push("NOTE:${escape_vcard(contact.notes)}")
    if (contact.avatar_url.isNotBlank()) push_photo(contact.avatar_url)
    lines.add("END:VCARD")

    return lines.joinToString("\r\n")
}

fun contacts_to_vcard(contacts: List<Contact>): String =
    contacts.joinToString("\r\n") { contact_to_vcard(it) } + "\r\n"

fun parse_vcards(raw_text: String): List<Contact> {
    val text = raw_text.removePrefix("\uFEFF")
    val contacts = mutableListOf<Contact>()
    var current: MutableMap<String, String>? = null
    var groups = mutableListOf<String>()

    fun flush() {
        val fields = current ?: return
        val name = fields["name"].orEmpty().ifBlank { fields["email"].orEmpty() }
        if (name.isBlank() && fields["email"].orEmpty().isBlank()) return
        contacts.add(
            Contact(
                id = "",
                name = name,
                email = fields["email"].orEmpty(),
                phone = fields["phone"].orEmpty(),
                company = fields["company"].orEmpty(),
                title = fields["title"].orEmpty(),
                work_email = fields["work_email"].orEmpty(),
                work_phone = fields["work_phone"].orEmpty(),
                birthday = fields["birthday"].orEmpty(),
                address = fields["address"].orEmpty(),
                city = fields["city"].orEmpty(),
                region = fields["region"].orEmpty(),
                postal_code = fields["postal_code"].orEmpty(),
                country = fields["country"].orEmpty(),
                website = fields["website"].orEmpty(),
                twitter = fields["twitter"].orEmpty(),
                linkedin = fields["linkedin"].orEmpty(),
                notes = fields["notes"].orEmpty(),
                avatar_url = fields["avatar_url"].orEmpty(),
                profile_color = fields["profile_color"].orEmpty(),
                is_favorite = fields["is_favorite"] == "true",
                groups = groups.toList(),
            ),
        )
    }

    for (line in unfold(text)) {
        val trimmed = line.trim()
        if (trimmed.equals("BEGIN:VCARD", ignoreCase = true)) {
            current = mutableMapOf()
            groups = mutableListOf()
            continue
        }
        if (trimmed.equals("END:VCARD", ignoreCase = true)) {
            if (contacts.size >= MAX_IMPORTED_CONTACTS) break
            flush()
            current = null
            continue
        }
        val fields = current ?: continue
        val parsed = parse_line(line) ?: continue
        val types = types_of(parsed.params)
        val value = unescape_vcard(parsed.value).trim()
        if (value.isEmpty() && parsed.key != "N") continue

        when (parsed.key) {
            "FN" -> fields["name"] = value
            "N" -> {
                if (fields["name"].isNullOrBlank()) {
                    val parts = split_vcard_value(parsed.value)
                    val given = parts.getOrNull(1).orEmpty().trim()
                    val family = parts.getOrNull(0).orEmpty().trim()
                    val joined = listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
                    if (joined.isNotBlank()) fields["name"] = joined
                }
            }
            "EMAIL" -> {
                if (types.contains("work") && fields["work_email"].isNullOrBlank()) {
                    fields["work_email"] = value
                } else if (fields["email"].isNullOrBlank()) {
                    fields["email"] = value
                } else if (fields["work_email"].isNullOrBlank()) {
                    fields["work_email"] = value
                }
            }
            "TEL" -> {
                if (types.contains("work") && fields["work_phone"].isNullOrBlank()) {
                    fields["work_phone"] = value
                } else if (fields["phone"].isNullOrBlank()) {
                    fields["phone"] = value
                } else if (fields["work_phone"].isNullOrBlank()) {
                    fields["work_phone"] = value
                }
            }
            "ORG" -> fields["company"] = split_vcard_value(parsed.value).firstOrNull().orEmpty().trim()
            "TITLE" -> fields["title"] = value
            "BDAY" -> fields["birthday"] = value
            "ADR" -> {
                if (fields["address"].isNullOrBlank() && fields["city"].isNullOrBlank()) {
                    val parts = split_vcard_value(parsed.value)
                    val street = listOf(parts.getOrNull(1).orEmpty(), parts.getOrNull(2).orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    fields["address"] = street
                    fields["city"] = parts.getOrNull(3).orEmpty()
                    fields["region"] = parts.getOrNull(4).orEmpty()
                    fields["postal_code"] = parts.getOrNull(5).orEmpty()
                    fields["country"] = parts.getOrNull(6).orEmpty()
                }
            }
            "URL" -> if (fields["website"].isNullOrBlank()) fields["website"] = parsed.value.trim()
            "X-SOCIALPROFILE" -> {
                when {
                    types.contains("twitter") -> fields["twitter"] = value
                    types.contains("linkedin") -> fields["linkedin"] = value
                }
            }
            "IMPP" -> Unit
            "CATEGORIES" -> {
                for (entry in value.split(",")) {
                    val label = entry.trim()
                    if (label.isNotEmpty()) groups.add(label)
                }
            }
            "X-ASTER-FAVORITE" -> fields["is_favorite"] = value.lowercase(Locale.ROOT)
            "X-ASTER-COLOR" -> {
                if (Regex("^#[0-9a-fA-F]{6}$").matches(value)) fields["profile_color"] = value
            }
            "NOTE" -> fields["notes"] = value
            "PHOTO" -> {
                val encoding = param_value(parsed.params, "ENCODING").lowercase(Locale.ROOT)
                val raw = parsed.value.trim()
                fields["avatar_url"] = when {
                    raw.startsWith("data:") || raw.startsWith("http") -> raw
                    encoding == "b" || encoding == "base64" -> {
                        val media = param_value(parsed.params, "TYPE").ifBlank { "jpeg" }
                        "data:image/${media.lowercase(Locale.ROOT)};base64,$raw"
                    }
                    else -> ""
                }
            }
        }
    }

    return contacts
}

private fun escape_csv(value: String): String {
    val guarded = if (value.isNotEmpty() && value.first() in listOf('=', '+', '-', '@', '\t', '\r')) {
        "'$value"
    } else {
        value
    }
    return "\"" + guarded.replace("\"", "\"\"") + "\""
}

fun contacts_to_csv(contacts: List<Contact>): String {
    val rows = contacts.map { contact ->
        val (first, last) = split_name(contact.name)
        listOf(
            first,
            last,
            listOf(contact.email, contact.work_email).filter { it.isNotBlank() }.joinToString("; "),
            listOf(contact.phone, contact.work_phone).filter { it.isNotBlank() }.joinToString("; "),
            contact.company,
            contact.title,
            contact.address,
            contact.city,
            contact.region,
            contact.postal_code,
            contact.country,
            contact.website,
            contact.birthday,
            contact.notes,
            if (contact.is_favorite) "true" else "false",
        )
    }
    return (
        listOf(CSV_HEADERS.joinToString(",") { escape_csv(it) }) +
            rows.map { row -> row.joinToString(",") { escape_csv(it) } }
        ).joinToString("\r\n")
}

fun parse_csv_rows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0
    val content =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")

    fun end_cell() {
        row.add(cell.toString())
        cell.setLength(0)
    }

    fun end_row() {
        end_cell()
        if (row.any { it.isNotBlank() }) rows.add(row.toList())
        row = mutableListOf()
    }

    while (index < content.length) {
        val character = content[index]
        if (quoted) {
            if (character == '"') {
                if (index + 1 < content.length && content[index + 1] == '"') {
                    cell.append('"')
                    index += 2
                    continue
                }
                quoted = false
                index += 1
                continue
            }
            cell.append(character)
            index += 1
            continue
        }
        when (character) {
            '"' -> quoted = true
            ',' -> end_cell()
            '\n' -> end_row()
            else -> cell.append(character)
        }
        index += 1
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) end_row()

    return rows
}

fun parse_csv_contacts(text: String): List<Contact> {
    val rows = parse_csv_rows(text)
    if (rows.isEmpty()) return emptyList()

    val headers = rows.first().map { it.trim().lowercase(Locale.ROOT).replace("_", " ") }

    fun index_of(vararg names: String): Int =
        names.map { it.lowercase(Locale.ROOT) }
            .map { headers.indexOf(it) }
            .firstOrNull { it >= 0 } ?: -1

    val first_index = index_of("first name", "given name", "first")
    val last_index = index_of("last name", "family name", "surname", "last")
    val full_index = index_of("name", "display name", "full name")
    val email_index = index_of("email", "email address", "e-mail", "email 1 - value")
    val work_email_index = index_of("work email", "business email")
    val phone_index = index_of("phone", "phone number", "mobile", "phone 1 - value")
    val work_phone_index = index_of("work phone", "business phone")
    val company_index = index_of("company", "organization", "organisation")
    val title_index = index_of("job title", "title", "position")
    val street_index = index_of("street", "address")
    val city_index = index_of("city")
    val region_index = index_of("state", "region", "province")
    val postal_index = index_of("postal code", "zip", "zip code")
    val country_index = index_of("country")
    val website_index = index_of("website", "url")
    val birthday_index = index_of("birthday", "birth date")
    val notes_index = index_of("notes", "note")
    val favorite_index = index_of("favorite", "favourite", "starred")

    fun cell(row: List<String>, position: Int): String {
        if (position < 0 || position >= row.size) return ""
        val value = row[position].trim()
        return if (value.startsWith("'")) value.drop(1).trim() else value
    }

    val contacts = mutableListOf<Contact>()
    for (row in rows.drop(1)) {
        if (contacts.size >= MAX_IMPORTED_CONTACTS) break
        val first = cell(row, first_index)
        val last = cell(row, last_index)
        val full = cell(row, full_index)
        val emails = cell(row, email_index).split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val phones = cell(row, phone_index).split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val email = emails.firstOrNull().orEmpty()
        val name = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { full }
        if (name.isBlank() && email.isBlank()) continue
        val favorite = cell(row, favorite_index).lowercase(Locale.ROOT)
        contacts.add(
            Contact(
                id = "",
                name = name.ifBlank { email },
                email = email,
                work_email = cell(row, work_email_index).ifBlank { emails.getOrNull(1).orEmpty() },
                phone = phones.firstOrNull().orEmpty(),
                work_phone = cell(row, work_phone_index).ifBlank { phones.getOrNull(1).orEmpty() },
                company = cell(row, company_index),
                title = cell(row, title_index),
                address = cell(row, street_index),
                city = cell(row, city_index),
                region = cell(row, region_index),
                postal_code = cell(row, postal_index),
                country = cell(row, country_index),
                website = cell(row, website_index),
                birthday = cell(row, birthday_index),
                notes = cell(row, notes_index),
                is_favorite = favorite == "true" || favorite == "yes" || favorite == "1",
            ),
        )
    }

    return contacts
}

fun parse_contacts_file(name: String, content: String): List<Contact> {
    val lower = name.lowercase(Locale.ROOT)
    val looks_like_vcard = lower.endsWith(".vcf") ||
        lower.endsWith(".vcard") ||
        content.contains("BEGIN:VCARD", ignoreCase = true)
    return if (looks_like_vcard) parse_vcards(content) else parse_csv_contacts(content)
}
