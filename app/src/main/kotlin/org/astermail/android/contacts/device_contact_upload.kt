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
    val unsent: List<Any?> = emptyList(),
)

suspend fun <T> upload_device_contacts(
    contacts: List<T>,
    create: suspend (T) -> Result<*>,
    on_progress: (Int) -> Unit = {},
): DeviceContactUploadResult {
    var imported = 0
    var failed = 0
    var last_failure: Throwable? = null
    val unsent = mutableListOf<T>()
    for ((index, contact) in contacts.withIndex()) {
        val error = create(contact).exceptionOrNull()
        when (error) {
            null -> imported++
            is CancellationException -> throw error
            is ApiError.PlanLimitExceeded -> {
                unsent.addAll(contacts.subList(index, contacts.size))
                return DeviceContactUploadResult(imported, failed, true, last_failure, unsent)
            }
            else -> {
                last_failure = error
                failed++
                unsent.add(contact)
            }
        }
        on_progress(index + 1)
    }
    return DeviceContactUploadResult(imported, failed, false, last_failure, unsent)
}

data class ContactImportPlan(
    val new_contacts: List<Contact>,
    val in_trash: Int,
    val updates: List<Contact> = emptyList(),
    val deleted_in_aster: Int = 0,
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
        addAll(detail_entry_keys(contact))
    }
    add(DETAILS_BASELINE_MARKER)
}

const val DETAILS_BASELINE_MARKER = "details_v1"

private fun identity_key(match_key: String): String = hash_entry_key("i:$match_key")

fun device_identity_keys(contacts: List<Contact>): Set<String> = buildSet {
    for (contact in contacts) import_match_keys(contact).forEach { add(identity_key(it)) }
}

private const val TOMBSTONE_KEY_LENGTH = 32

private fun tombstone_key(match_key: String): String = hash_entry_key("t:$match_key").take(TOMBSTONE_KEY_LENGTH)

fun deletion_tombstone_keys(contact: Contact): List<String> =
    (email_match_keys(contact) + listOfNotNull(name_key(contact))).map(::tombstone_key)

fun deletion_tombstone_keys(contacts: Collection<Contact>): List<String> =
    contacts.flatMap(::deletion_tombstone_keys).distinct()

private fun seen_in_last_device_sync(keys: List<String>, known_identities: Set<String>): Boolean =
    known_identities.isNotEmpty() && keys.any { identity_key(it) in known_identities }

private fun deleted_in_aster(keys: List<String>, tombstones: Set<String>): Boolean =
    tombstones.isNotEmpty() && keys.any { tombstone_key(it) in tombstones }

private class DetailField(
    val name: String,
    val device_wins_without_baseline: Boolean,
    val read: (Contact) -> String,
    val write: (Contact, String) -> Contact,
)

private val detail_fields = listOf(
    DetailField("birthday", true, { it.birthday }, { contact, value -> contact.copy(birthday = value) }),
    DetailField("company", false, { it.company }, { contact, value -> contact.copy(company = value) }),
    DetailField("title", false, { it.title }, { contact, value -> contact.copy(title = value) }),
    DetailField("website", false, { it.website }, { contact, value -> contact.copy(website = value) }),
    DetailField("notes", false, { it.notes }, { contact, value -> contact.copy(notes = value) }),
)

private fun detail_entry_key(owner: String, field: DetailField, value: String): String =
    hash_entry_key("d:" + field.name + ":" + owner + ":" + value)

private fun detail_entry_keys(contact: Contact): List<String> {
    val owner = import_match_keys(contact).firstOrNull() ?: return emptyList()
    return detail_fields.mapNotNull { field ->
        field.read(contact).trim().takeIf { it.isNotEmpty() }?.let { detail_entry_key(owner, field, it) }
    }
}

private fun refresh_changed_details(existing: Contact, incoming: Contact, previously_seen: Set<String>): Contact? {
    val owner = import_match_keys(incoming).firstOrNull() ?: return null
    val has_baseline = DETAILS_BASELINE_MARKER in previously_seen
    var refreshed = existing
    for (field in detail_fields) {
        val device_value = field.read(incoming).trim()
        if (device_value.isEmpty()) continue
        val stored_value = field.read(refreshed).trim()
        if (device_value == stored_value) continue
        val changed_on_device = if (has_baseline) {
            detail_entry_key(owner, field, device_value) !in previously_seen
        } else {
            field.device_wins_without_baseline || stored_value.isEmpty()
        }
        if (changed_on_device) refreshed = field.write(refreshed, device_value)
    }
    return refreshed.takeIf { it != existing }
}

private fun merge_new_entries(existing: Contact, incoming: Contact, previously_seen: Set<String>): Contact? {
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
    refresh_details: Boolean = false,
    tombstones: Set<String> = emptySet(),
    known_identities: Set<String> = emptySet(),
): ContactImportPlan {
    val active = existing.filter { it.deleted_at.isBlank() }
    val active_index = active_match_index(active)
    val emailless_index = emailless_name_index(active)
    val trashed_keys = stored_match_keys(existing.filter { it.deleted_at.isNotBlank() })
    val seen = mutableSetOf<String>()
    val new_contacts = mutableListOf<Contact>()
    val updates = linkedMapOf<String, Contact>()
    var in_trash = 0
    var removed = 0
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
                val known = previously_seen.orEmpty()
                merge_new_entries(updates[target.id] ?: target, contact, known)
                    ?.let { updates[target.id] = it }
                if (refresh_details) {
                    refresh_changed_details(updates[target.id] ?: target, contact, known)
                        ?.let { updates[target.id] = it }
                }
            }
            continue
        }
        if (keys.any { it in seen }) continue
        seen.addAll(keys)
        if (keys.any { it in trashed_keys }) {
            in_trash++
            continue
        }
        if (deleted_in_aster(keys, tombstones) || seen_in_last_device_sync(keys, known_identities)) {
            removed++
            continue
        }
        new_contacts.add(contact)
    }
    return ContactImportPlan(new_contacts, in_trash, updates.values.toList(), removed)
}
