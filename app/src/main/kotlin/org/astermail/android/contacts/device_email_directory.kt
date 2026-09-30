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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import androidx.core.content.ContextCompat
import org.astermail.android.contacts.sync.ContactSyncAccounts
import org.astermail.android.ui.contacts.Contact
import java.util.Locale

private const val DEVICE_SUGGESTION_LIMIT = 5_000

fun load_device_email_contacts(context: Context): List<Contact> {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return emptyList()
    val own_type = ContactSyncAccounts.account_type(context)
    val out = linkedMapOf<String, Contact>()
    runCatching {
        context.contentResolver.query(
            Email.CONTENT_URI,
            arrayOf(Email.ADDRESS, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "(${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR ${ContactsContract.RawContacts.ACCOUNT_TYPE} != ?)",
            arrayOf(own_type),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < DEVICE_SUGGESTION_LIMIT) {
                val address = cursor.getString(0)?.trim().orEmpty()
                if (address.isEmpty() || '@' !in address) continue
                val key = address.lowercase(Locale.ROOT)
                if (key in out) continue
                val name = cursor.getString(1)?.trim().orEmpty()
                out[key] = Contact(id = "", name = name.ifEmpty { address }, email = address)
            }
        }
    }
    return out.values.toList()
}

fun recipient_suggestion_pool(aster: List<Contact>, device: List<Contact>): List<Contact> {
    val out = linkedMapOf<String, Contact>()
    for (contact in aster) {
        for (address in listOf(contact.email, contact.work_email)) {
            val trimmed = address.trim()
            if (trimmed.isEmpty()) continue
            out.putIfAbsent(trimmed.lowercase(Locale.ROOT), contact.copy(email = trimmed))
        }
    }
    for (contact in device) {
        out.putIfAbsent(contact.email.lowercase(Locale.ROOT), contact)
    }
    return out.values.toList()
}
