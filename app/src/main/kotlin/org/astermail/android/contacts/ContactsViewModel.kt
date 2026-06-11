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

import android.content.Context
import android.provider.ContactsContract
import org.astermail.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.ui.contacts.Contact

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val selected_contact: Contact? = null,
    val is_loading: Boolean = false,
    val is_syncing: Boolean = false,
    val sync_message: String? = null,
    val error: String? = null,
    val save_success: Boolean = false,
    val delete_success: Boolean = false,
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    fun load_contacts() {
        if (_state.value.is_loading) return
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            repository.fetch_contacts().fold(
                onSuccess = { contacts ->
                    _state.value = _state.value.copy(
                        contacts = contacts,
                        is_loading = false,
                    )
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_loading = false,
                        error = friendly_error(t),
                    )
                },
            )
        }
    }

    fun load_contact(contact_id: String) {
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            repository.fetch_contact(contact_id).fold(
                onSuccess = { contact ->
                    _state.value = _state.value.copy(
                        selected_contact = contact,
                        is_loading = false,
                    )
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_loading = false,
                        error = friendly_error(t),
                    )
                },
            )
        }
    }

    fun save_contact(contact: Contact, existing_id: String? = null) {
        _state.value = _state.value.copy(is_loading = true, error = null, save_success = false)
        viewModelScope.launch {
            val result = if (existing_id != null) {
                repository.update_contact(existing_id, contact).map { }
            } else {
                repository.create_contact(contact).map { }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        is_loading = false,
                        save_success = true,
                    )
                    load_contacts()
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_loading = false,
                        error = friendly_error(t),
                    )
                },
            )
        }
    }

    fun delete_contact(contact_id: String) {
        _state.value = _state.value.copy(is_loading = true, error = null, delete_success = false)
        viewModelScope.launch {
            repository.delete_contact(contact_id).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        is_loading = false,
                        delete_success = true,
                        contacts = _state.value.contacts.filter { it.id != contact_id },
                    )
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_loading = false,
                        error = friendly_error(t),
                    )
                },
            )
        }
    }

    fun sync_device_contacts(context: Context) {
        if (_state.value.is_syncing) return
        _state.value = _state.value.copy(is_syncing = true, error = null, sync_message = null)
        viewModelScope.launch {
            try {
                val device_contacts = withContext(Dispatchers.IO) {
                    read_device_contacts(context)
                }
                if (device_contacts.isEmpty()) {
                    _state.value = _state.value.copy(
                        is_syncing = false,
                        sync_message = context.getString(R.string.no_device_contacts),
                    )
                    return@launch
                }
                val existing_emails = _state.value.contacts
                    .filter { it.email.isNotBlank() }
                    .map { it.email.lowercase().trim() }
                    .toSet()
                val existing_names = _state.value.contacts
                    .filter { it.email.isBlank() }
                    .map { it.name.lowercase().trim() }
                    .toSet()
                val new_contacts = device_contacts.filter { contact ->
                    if (contact.email.isNotBlank()) {
                        contact.email.lowercase().trim() !in existing_emails
                    } else {
                        contact.name.lowercase().trim() !in existing_names
                    }
                }
                var imported = 0
                for (contact in new_contacts) {
                    try {
                        repository.create_contact(contact)
                        imported++
                    } catch (_: Throwable) {
                    }
                }
                _state.value = _state.value.copy(
                    is_syncing = false,
                    sync_message = if (imported > 0) context.getString(R.string.imported_contacts, imported) else context.getString(R.string.no_new_contacts),
                )
                load_contacts()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_syncing = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    private fun read_device_contacts(context: Context): List<Contact> {
        val contacts = mutableMapOf<String, Contact>()
        val resolver = context.contentResolver

        val name_projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )

        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            name_projection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { cursor ->
            val id_idx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val name_idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val contact_id = cursor.getString(id_idx) ?: continue
                val name = cursor.getString(name_idx) ?: ""
                if (name.isBlank()) continue
                contacts[contact_id] = Contact(
                    id = "",
                    name = name,
                    email = "",
                )
            }
        }

        val email_projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
        )

        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            email_projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val id_idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val email_idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val contact_id = cursor.getString(id_idx) ?: continue
                val email = cursor.getString(email_idx) ?: continue
                if (email.isBlank()) continue
                val existing = contacts[contact_id]
                if (existing != null && existing.email.isBlank()) {
                    contacts[contact_id] = existing.copy(email = email)
                }
            }
        }

        val phone_projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            phone_projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val id_idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val phone_idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contact_id = cursor.getString(id_idx) ?: continue
                val phone = cursor.getString(phone_idx) ?: continue
                val existing = contacts[contact_id]
                if (existing != null && existing.phone.isBlank()) {
                    contacts[contact_id] = existing.copy(phone = phone)
                }
            }
        }

        return contacts.values.toList()
    }

    fun clear_sync_message() {
        _state.value = _state.value.copy(sync_message = null)
    }

    fun clear_flags() {
        _state.value = _state.value.copy(save_success = false, delete_success = false, error = null)
    }

    private fun friendly_error(t: Throwable): String {
        val msg = t.message?.lowercase().orEmpty()
        return when {
            t is java.net.UnknownHostException -> context.getString(R.string.error_no_connection)
            t is java.net.ConnectException -> context.getString(R.string.error_no_connection)
            t is java.net.SocketTimeoutException -> context.getString(R.string.error_timeout)
            t is javax.net.ssl.SSLException -> context.getString(R.string.error_ssl)
            "timeout" in msg || "timed out" in msg -> context.getString(R.string.error_timeout)
            "connection" in msg && ("refused" in msg || "reset" in msg) -> context.getString(R.string.error_no_connection)
            else -> t.message ?: context.getString(R.string.something_went_wrong)
        }
    }
}
