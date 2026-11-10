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
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ApiError
import org.astermail.android.ui.contacts.Contact

data class DeviceContactUploadResult(
    val imported: Int,
    val failed: Int,
    val limit_reached: Boolean,
    val last_failure: Throwable?,
)

suspend fun <T> upload_device_contacts(
    contacts: List<T>,
    create: suspend (T) -> Result<*>,
    on_progress: (Int) -> Unit = {},
): DeviceContactUploadResult {
    var imported = 0
    var failed = 0
    var last_failure: Throwable? = null
    for ((index, contact) in contacts.withIndex()) {
        val error = create(contact).exceptionOrNull()
        when (error) {
            null -> imported++
            is CancellationException -> throw error
            is ApiError.PlanLimitExceeded -> return DeviceContactUploadResult(imported, failed, true, last_failure)
            else -> {
                last_failure = error
                failed++
            }
        }
        on_progress(index + 1)
    }
    return DeviceContactUploadResult(imported, failed, false, last_failure)
}

data class ContactImportPlan(
    val new_contacts: List<Contact>,
    val in_trash: Int,
    val updates: List<Contact> = emptyList(),
)

private fun normalized_key(value: String): String = value.lowercase(Locale.ROOT).trim()

private fun name_key(contact: Contact): String? =
    normalized_key(contact.name).takeIf { it.isNotEmpty() }?.let { "n:$it" }

private fun email_match_keys(contact: Contact): List<String> =
    (listOf(contact.email, contact.work_email) + contact.email_entries().map { it.value })
        .map(::normalized_key)
        .filter { it.isNotEmpty() }
        .distinct()
        .map { "e:$it" }

private fun import_match_keys(contact: Contact): List<String> {
    val emails = email_match_keys(contact)
    if (emails.isNotEmpty()) return emails
    return listOfNotNull(name_key(contact))
}

private fun stored_match_keys(contacts: List<Contact>): Set<String> = buildSet {
    for (contact in contacts) {
        addAll(email_match_keys(contact))
        name_key(contact)?.let(::add)
    }
}

private fun phone_digits_key(value: String): String = value.filter { it.isDigit() }

private fun email_entry_key(value: String): String = hash_entry_key("e:" + normalized_key(value))

private fun phone_entry_key(value: String): String = hash_entry_key("p:" + phone_digits_key(value))

fun device_entry_keys(contacts: List<Contact>): Set<String> = buildSet {
    for (contact in contacts) {
        contact.email_entries().filter { normalized_key(it.value).isNotEmpty() }.forEach { add(email_entry_key(it.value)) }
        contact.phone_entries().filter { phone_digits_key(it.value).isNotEmpty() }.forEach { add(phone_entry_key(it.value)) }
    }
}

private fun merge_new_entries(existing: Contact, incoming: Contact, previously_seen: Set<String>?): Contact? {
    if (previously_seen == null) return null
    val known_emails = existing.email_entries()
    val known_phones = existing.phone_entries()
    val email_set = known_emails.map { normalized_key(it.value) }.toSet()
    val phone_set = known_phones.map { phone_digits_key(it.value) }.filter { it.isNotEmpty() }.toSet()
    val added_emails = incoming.email_entries().filter {
        val key = normalized_key(it.value)
        key.isNotEmpty() && key !in email_set && email_entry_key(it.value) !in previously_seen
    }.distinctBy { normalized_key(it.value) }
    val added_phones = incoming.phone_entries().filter {
        val key = phone_digits_key(it.value)
        key.isNotEmpty() && key !in phone_set && phone_entry_key(it.value) !in previously_seen
    }.distinctBy { phone_digits_key(it.value) }
    if (added_emails.isEmpty() && added_phones.isEmpty()) return null
    return existing.with_typed_fields(
        emails = known_emails + added_emails,
        phones = known_phones + added_phones,
        addresses = existing.address_entries(),
    )
}

private fun active_match_index(active: List<Contact>): Map<String, Contact> = buildMap {
    for (contact in active) {
        for (key in email_match_keys(contact)) putIfAbsent(key, contact)
        name_key(contact)?.let { putIfAbsent(it, contact) }
    }
}

private fun emailless_name_index(active: List<Contact>): Map<String, Contact> = buildMap {
    for (contact in active) {
        if (email_match_keys(contact).isNotEmpty()) continue
        name_key(contact)?.let { putIfAbsent(it, contact) }
    }
}

fun plan_contact_import(
    incoming: List<Contact>,
    existing: List<Contact>,
    previously_seen: Set<String>? = emptySet(),
): ContactImportPlan {
    val active = existing.filter { it.deleted_at.isBlank() }
    val active_index = active_match_index(active)
    val emailless_index = emailless_name_index(active)
    val trashed_keys = stored_match_keys(existing.filter { it.deleted_at.isNotBlank() })
    val seen = mutableSetOf<String>()
    val new_contacts = mutableListOf<Contact>()
    val updates = linkedMapOf<String, Contact>()
    var in_trash = 0
    for (contact in incoming) {
        val keys = import_match_keys(contact)
        if (keys.isEmpty()) {
            new_contacts.add(contact)
            continue
        }
        val target = keys.firstNotNullOfOrNull { active_index[it] }
            ?: name_key(contact)?.let { emailless_index[it] }
        if (target != null) {
            if (target.id.isNotBlank()) {
                merge_new_entries(updates[target.id] ?: target, contact, previously_seen)?.let { updates[target.id] = it }
            }
            continue
        }
        if (keys.any { it in seen }) continue
        seen.addAll(keys)
        if (keys.any { it in trashed_keys }) {
            in_trash++
            continue
        }
        new_contacts.add(contact)
    }
    return ContactImportPlan(new_contacts, in_trash, updates.values.toList())
}
