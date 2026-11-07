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
)

private fun normalized_key(value: String): String = value.lowercase(Locale.ROOT).trim()

private fun email_match_keys(contact: Contact): List<String> =
    listOf(contact.email, contact.work_email)
        .map(::normalized_key)
        .filter { it.isNotEmpty() }
        .map { "e:$it" }

private fun import_match_keys(contact: Contact): List<String> {
    val emails = email_match_keys(contact)
    if (emails.isNotEmpty()) return emails
    val name = normalized_key(contact.name)
    return if (name.isEmpty()) emptyList() else listOf("n:$name")
}

private fun stored_match_keys(contacts: List<Contact>): Set<String> = buildSet {
    for (contact in contacts) {
        addAll(email_match_keys(contact))
        val name = normalized_key(contact.name)
        if (name.isNotEmpty()) add("n:$name")
    }
}

fun plan_contact_import(incoming: List<Contact>, existing: List<Contact>): ContactImportPlan {
    val active_keys = stored_match_keys(existing.filter { it.deleted_at.isBlank() })
    val trashed_keys = stored_match_keys(existing.filter { it.deleted_at.isNotBlank() })
    val seen = mutableSetOf<String>()
    val new_contacts = mutableListOf<Contact>()
    var in_trash = 0
    for (contact in incoming) {
        val keys = import_match_keys(contact)
        if (keys.isEmpty()) {
            new_contacts.add(contact)
            continue
        }
        if (keys.any { it in active_keys || it in seen }) continue
        seen.addAll(keys)
        if (keys.any { it in trashed_keys }) {
            in_trash++
            continue
        }
        new_contacts.add(contact)
    }
    return ContactImportPlan(new_contacts, in_trash)
}
