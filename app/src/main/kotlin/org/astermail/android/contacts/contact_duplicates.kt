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

const val DUPLICATE_THRESHOLD = 70

private const val MAX_COMPARED_CONTACTS = 4000

data class DuplicateCluster(
    val key: String,
    val contacts: List<Contact>,
    val score: Int,
)

fun normalize_text(value: String?): String =
    (value ?: "").trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

fun normalize_email(value: String): String = value.trim().lowercase(Locale.ROOT)

fun email_identity(value: String): String {
    val normalized = normalize_email(value)
    val at = normalized.lastIndexOf('@')
    if (at <= 0) return normalized
    val local = normalized.substring(0, at).substringBefore('+')
    val domain = normalized.substring(at + 1)
    return "$local@$domain"
}

fun normalize_phone(value: String?): String {
    val digits = (value ?: "").filter { it.isDigit() }
    return if (digits.length >= 7) digits.takeLast(10) else ""
}

private fun addresses_of(contact: Contact): List<String> =
    listOf(contact.email, contact.work_email).filter { it.isNotBlank() }

private fun exact_emails(contact: Contact): Set<String> =
    addresses_of(contact).map { normalize_email(it) }.toSet()

private fun identity_emails(contact: Contact): Set<String> =
    addresses_of(contact).map { email_identity(it) }.toSet()

fun similarity_score(left: Contact, right: Contact): Int {
    if (exact_emails(left).any { it in exact_emails(right) }) return 100

    val left_name = normalize_text(left.name)
    val right_name = normalize_text(right.name)
    val same_name = left_name.isNotEmpty() && left_name == right_name

    if (identity_emails(left).any { it in identity_emails(right) }) {
        return if (same_name) 95 else 85
    }

    val left_phone = normalize_phone(left.phone)
    val right_phone = normalize_phone(right.phone)
    val same_phone = left_phone.isNotEmpty() && left_phone == right_phone

    if (same_name && same_phone) return 90
    if (same_phone) return 75

    if (same_name) {
        val left_company = normalize_text(left.company)
        val right_company = normalize_text(right.company)
        if (left_company.isNotEmpty() && left_company == right_company) return 80
        return 60
    }

    return 0
}

fun find_duplicate_clusters(contacts: List<Contact>): List<DuplicateCluster> {
    val pool = contacts.take(MAX_COMPARED_CONTACTS)
    if (pool.size < 2) return emptyList()

    val parent = HashMap<String, String>(pool.size)
    val best = HashMap<String, Int>()

    for (contact in pool) parent[contact.id] = contact.id

    fun find(id: String): String {
        var current = id
        while (parent[current] != current) {
            val next = parent[current] ?: break
            parent[current] = parent[next] ?: next
            current = parent[current] ?: current
        }
        return current
    }

    fun union(left: String, right: String) {
        val left_root = find(left)
        val right_root = find(right)
        if (left_root != right_root) parent[right_root] = left_root
    }

    val buckets = HashMap<String, MutableList<Contact>>()

    fun add_to_bucket(key: String, contact: Contact) {
        if (key.isBlank()) return
        buckets.getOrPut(key) { mutableListOf() }.add(contact)
    }

    for (contact in pool) {
        for (identity in identity_emails(contact)) add_to_bucket("e:$identity", contact)
        val name = normalize_text(contact.name)
        if (name.isNotEmpty()) add_to_bucket("n:$name", contact)
        val phone = normalize_phone(contact.phone)
        if (phone.isNotEmpty()) add_to_bucket("p:$phone", contact)
    }

    for (bucket in buckets.values) {
        if (bucket.size < 2) continue
        for (i in bucket.indices) {
            for (j in i + 1 until bucket.size) {
                val score = similarity_score(bucket[i], bucket[j])
                if (score < DUPLICATE_THRESHOLD) continue
                union(bucket[i].id, bucket[j].id)
                for (id in listOf(bucket[i].id, bucket[j].id)) {
                    best[id] = maxOf(best[id] ?: 0, score)
                }
            }
        }
    }

    val grouped = LinkedHashMap<String, MutableList<Contact>>()
    for (contact in pool) {
        grouped.getOrPut(find(contact.id)) { mutableListOf() }.add(contact)
    }

    return grouped
        .filter { it.value.size >= 2 }
        .map { (key, members) ->
            DuplicateCluster(
                key = key,
                contacts = members.toList(),
                score = members.maxOf { best[it.id] ?: 0 },
            )
        }
        .sortedWith(
            compareByDescending<DuplicateCluster> { it.score }
                .thenByDescending { it.contacts.size },
        )
}

fun count_duplicate_contacts(clusters: List<DuplicateCluster>): Int =
    clusters.sumOf { it.contacts.size }

private fun first_non_blank(ordered: List<Contact>, read: (Contact) -> String): String =
    ordered.firstOrNull { read(it).isNotBlank() }?.let(read) ?: ""

fun merge_contacts(ordered: List<Contact>): Contact {
    val primary = ordered.first()

    val addresses = LinkedHashSet<String>()
    for (contact in ordered) {
        for (address in addresses_of(contact)) {
            val trimmed = address.trim()
            if (trimmed.isNotEmpty()) addresses.add(trimmed)
        }
    }
    val ordered_addresses = addresses.toList()

    val groups = LinkedHashMap<String, String>()
    for (contact in ordered) {
        for (name in contact.groups) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) continue
            groups.putIfAbsent(trimmed.lowercase(Locale.ROOT), trimmed)
        }
    }

    val notes = ordered.map { it.notes.trim() }.filter { it.isNotEmpty() }.distinct()

    return primary.copy(
        name = first_non_blank(ordered) { it.name },
        email = ordered_addresses.getOrElse(0) { "" },
        work_email = ordered_addresses.getOrElse(1) { "" },
        phone = first_non_blank(ordered) { it.phone },
        work_phone = first_non_blank(ordered) { it.work_phone },
        company = first_non_blank(ordered) { it.company },
        title = first_non_blank(ordered) { it.title },
        birthday = first_non_blank(ordered) { it.birthday },
        address = first_non_blank(ordered) { it.address },
        city = first_non_blank(ordered) { it.city },
        region = first_non_blank(ordered) { it.region },
        postal_code = first_non_blank(ordered) { it.postal_code },
        country = first_non_blank(ordered) { it.country },
        website = first_non_blank(ordered) { it.website },
        twitter = first_non_blank(ordered) { it.twitter },
        linkedin = first_non_blank(ordered) { it.linkedin },
        notes = notes.joinToString("\n\n"),
        is_favorite = ordered.any { it.is_favorite },
        groups = groups.values.toList(),
    )
}
