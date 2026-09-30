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
import org.astermail.android.ui.contacts.ContactEntry
import org.astermail.android.ui.contacts.ContactPostal

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

private data class VcardLine(val group: String, val key: String, val params: List<String>, val value: String)

private fun parse_line(line: String): VcardLine? {
    val separator = line.indexOf(':')
    if (separator <= 0) return null
    val head = line.substring(0, separator)
    val value = line.substring(separator + 1)
    val segments = head.split(";")
    val name = segments.first()
    val group = name.substringBefore('.', "").trim().lowercase(Locale.ROOT)
    val key = name.substringAfter('.').trim().uppercase(Locale.ROOT)
    val params = segments.drop(1).map { it.trim() }
    return VcardLine(group, key, params, value)
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
        val match = photo_data_uri.matchEntire(value) ?: return
        val media = match.groupValues[1].uppercase(Locale.ROOT)
        push_raw("PHOTO;ENCODING=b;TYPE=$media", match.groupValues[2])
    }

    var item = 0

    fun push_typed(key: String, base_params: String, type_param: String?, label: String?, value: String) {
        if (label != null) {
            item += 1
            push("item$item.$key$base_params:$value")
            push("item$item.X-ABLabel:${escape_vcard(label)}")
        } else {
            push("$key$base_params${type_param?.let { ";TYPE=$it" }.orEmpty()}:$value")
        }
    }

    push("N:${escape_vcard(last)};${escape_vcard(first)};;;")
    push("FN:${escape_vcard(contact.name.ifBlank { contact.email })}")
    for (entry in contact.email_entries()) {
        val (type_param, label) = vcard_type_for(entry.type, entry.label, EMAIL_TYPE_PARAMS)
        push_typed("EMAIL", ";TYPE=INTERNET", type_param, label, escape_vcard(entry.value))
    }
    for (entry in contact.phone_entries()) {
        val (type_param, label) = vcard_type_for(entry.type, entry.label, PHONE_TYPE_PARAMS)
        push_typed("TEL", "", type_param, label, escape_vcard(entry.value))
    }
    if (contact.company.isNotBlank()) push("ORG:${escape_vcard(contact.company)}")
    if (contact.title.isNotBlank()) push("TITLE:${escape_vcard(contact.title)}")
    if (contact.birthday.isNotBlank()) push("BDAY:${escape_vcard(contact.birthday)}")

    for (postal in contact.address_entries()) {
        val (type_param, label) = vcard_type_for(postal.type, postal.label, ADDRESS_TYPE_PARAMS)
        val value = ";;${escape_vcard(postal.street)};${escape_vcard(postal.city)};" +
            "${escape_vcard(postal.region)};${escape_vcard(postal.postal_code)};" +
            escape_vcard(postal.country)
        push_typed("ADR", "", type_param, label, value)
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

private val EMAIL_TYPE_PARAMS = mapOf("home" to "HOME", "work" to "WORK")
private val PHONE_TYPE_PARAMS = mapOf(
    "mobile" to "CELL",
    "home" to "HOME",
    "work" to "WORK",
    "fax" to "FAX",
    "pager" to "PAGER",
)
private val ADDRESS_TYPE_PARAMS = mapOf("home" to "HOME", "work" to "WORK")

private fun vcard_type_for(type: String, label: String, known: Map<String, String>): Pair<String?, String?> =
    when {
        type == ContactEntry.TYPE_PERSONAL -> null to "Personal"
        type == ContactEntry.TYPE_OTHER && label.isNotBlank() -> null to label.trim()
        else -> known[type] to null
    }

fun contact_share_text(contact: Contact): String {
    val phones = contact.phone_entries().map { it.value.trim() }.filter { it.isNotEmpty() }
    val name = contact.name.trim()
    return (listOfNotNull(name.takeIf { it.isNotEmpty() }) + phones).joinToString("\n")
}

fun contact_share_file_name(contact: Contact): String {
    val base = contact.name.trim()
        .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
        .trim()
        .take(60)
    return base.ifBlank { "contact" } + ".vcf"
}

private val IGNORED_TYPE_TOKENS = setOf(
    "pref", "internet", "voice", "x400", "dom", "intl", "postal", "parcel", "text", "msg",
)

private fun clean_ab_label(raw: String): String =
    raw.trim().removePrefix("_\$!<").removeSuffix(">!\$_").trim()

private enum class VcardKind { EMAIL, PHONE, ADDRESS }

private fun resolve_word(word: String, kind: VcardKind): String? =
    when (word.lowercase(Locale.ROOT)) {
        "home" -> "home"
        "work" -> "work"
        "other" -> "other"
        "personal" -> if (kind == VcardKind.ADDRESS) null else "personal"
        "cell", "mobile", "iphone" -> if (kind == VcardKind.PHONE) "mobile" else null
        "fax", "homefax", "workfax", "otherfax" -> if (kind == VcardKind.PHONE) "fax" else null
        "pager" -> if (kind == VcardKind.PHONE) "pager" else null
        else -> null
    }

private fun resolve_type(types: List<String>, ab_label: String?, kind: VcardKind): Pair<String, String> {
    val cleaned = ab_label?.let { clean_ab_label(it) }.orEmpty()
    if (cleaned.isNotEmpty()) {
        val word = resolve_word(cleaned, kind)
        return if (word != null) word to "" else "other" to cleaned
    }
    val tokens = types.filter { it !in IGNORED_TYPE_TOKENS }
    if (tokens.isEmpty()) return (if (kind == VcardKind.PHONE) "mobile" else "other") to ""
    if (kind == VcardKind.PHONE) {
        if ("fax" in tokens) return "fax" to ""
        if ("pager" in tokens) return "pager" to ""
        if (tokens.any { it == "cell" || it == "mobile" || it == "iphone" }) return "mobile" to ""
    }
    tokens.firstNotNullOfOrNull { resolve_word(it, kind) }?.let { return it to "" }
    val custom = tokens.first().removePrefix("x-").replaceFirstChar { it.titlecase(Locale.ROOT) }
    return "other" to custom
}

private class PendingEntry(val group: String, val value: String, val types: List<String>)

private class PendingPostal(val group: String, val postal: ContactPostal, val types: List<String>)

fun contacts_to_vcard(contacts: List<Contact>): String =
    contacts.joinToString("\r\n") { contact_to_vcard(it) } + "\r\n"

fun parse_vcards(raw_text: String): List<Contact> {
    val text = raw_text.removePrefix("\uFEFF")
    val contacts = mutableListOf<Contact>()
    var current: MutableMap<String, String>? = null
    var groups = mutableListOf<String>()
    var emails = mutableListOf<PendingEntry>()
    var phones = mutableListOf<PendingEntry>()
    var postals = mutableListOf<PendingPostal>()
    var ab_labels = mutableMapOf<String, String>()

    fun flush() {
        val fields = current ?: return
        fun label_for(group: String) = if (group.isEmpty()) null else ab_labels[group]
        val typed_emails = emails.map {
            val (type, label) = resolve_type(it.types, label_for(it.group), VcardKind.EMAIL)
            ContactEntry(it.value, type, label)
        }
        val typed_phones = phones.map {
            val (type, label) = resolve_type(it.types, label_for(it.group), VcardKind.PHONE)
            ContactEntry(it.value, type, label)
        }
        val typed_addresses = postals.map {
            val (type, label) = resolve_type(it.types, label_for(it.group), VcardKind.ADDRESS)
            it.postal.copy(type = type, label = label)
        }
        val first_email = typed_emails.firstOrNull()?.value.orEmpty()
        val name = fields["name"].orEmpty().ifBlank { first_email }
        if (name.isBlank() && first_email.isBlank()) return
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
            ).with_typed_fields(typed_emails, typed_phones, typed_addresses),
        )
    }

    for (line in unfold(text)) {
        val trimmed = line.trim()
        if (trimmed.equals("BEGIN:VCARD", ignoreCase = true)) {
            current = mutableMapOf()
            groups = mutableListOf()
            emails = mutableListOf()
            phones = mutableListOf()
            postals = mutableListOf()
            ab_labels = mutableMapOf()
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
            "EMAIL" -> emails.add(PendingEntry(parsed.group, value.removePrefix("mailto:"), types))
            "TEL" -> phones.add(PendingEntry(parsed.group, value.removePrefix("tel:"), types))
            "X-ABLABEL" -> if (parsed.group.isNotEmpty()) ab_labels[parsed.group] = value
            "ORG" -> fields["company"] = split_vcard_value(parsed.value).firstOrNull().orEmpty().trim()
            "TITLE" -> fields["title"] = value
            "BDAY" -> fields["birthday"] = value
            "ADR" -> {
                val parts = split_vcard_value(parsed.value)
                val street = listOf(parts.getOrNull(1).orEmpty(), parts.getOrNull(2).orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val postal = ContactPostal(
                    street = street.trim(),
                    city = parts.getOrNull(3).orEmpty().trim(),
                    region = parts.getOrNull(4).orEmpty().trim(),
                    postal_code = parts.getOrNull(5).orEmpty().trim(),
                    country = parts.getOrNull(6).orEmpty().trim(),
                )
                if (!postal.is_blank()) postals.add(PendingPostal(parsed.group, postal, types))
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
                    photo_data_uri.matches(raw) -> raw
                    raw.contains(":") -> ""
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

fun parse_contacts_file(name: String, content: String): List<Contact> {
    val lower = name.lowercase(Locale.ROOT)
    val looks_like_vcard = lower.endsWith(".vcf") ||
        lower.endsWith(".vcard") ||
        content.contains("BEGIN:VCARD", ignoreCase = true)
    return if (looks_like_vcard) parse_vcards(content) else parse_csv_contacts(content)
}
