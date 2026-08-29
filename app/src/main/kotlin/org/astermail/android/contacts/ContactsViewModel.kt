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

    private var list_in_flight = false
    private var mutation_in_flight = false

    fun load_contacts() {
        if (list_in_flight) return
        list_in_flight = true
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            val outcome = repository.fetch_contacts()
            list_in_flight = false
            outcome.fold(
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

    private var latest_contact_request: String? = null

    fun load_contact(contact_id: String) {
        latest_contact_request = contact_id
        _state.value = _state.value.copy(is_loading = true, error = null, selected_contact = null)
        viewModelScope.launch {
            val result = repository.fetch_contact(contact_id)
            if (latest_contact_request != contact_id) return@launch
            result.fold(
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

    fun save_contact(
        contact: Contact,
        existing_id: String? = null,
        on_complete: ((Boolean) -> Unit)? = null,
    ) {
        if (mutation_in_flight) return
        mutation_in_flight = true
        _state.value = _state.value.copy(is_loading = true, error = null, save_success = false)
        viewModelScope.launch {
            val result = if (existing_id != null) {
                repository.update_contact(existing_id, contact).map { }
            } else {
                repository.create_contact(contact).map { }
            }
            mutation_in_flight = false
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        is_loading = false,
                        save_success = true,
                    )
                    on_complete?.invoke(true)
                    load_contacts()
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_loading = false,
                        error = friendly_error(t),
                    )
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun delete_contact(contact_id: String, on_complete: ((Boolean) -> Unit)? = null) {
        if (mutation_in_flight) return
        mutation_in_flight = true
        _state.value = _state.value.copy(is_loading = true, error = null, delete_success = false)
        viewModelScope.launch {
            val outcome = repository.delete_contact(contact_id)
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        is_loading = false,
                        delete_success = true,
                        contacts = _state.value.contacts.filter { it.id != contact_id },
                    )
                    on_complete?.invoke(true)
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_loading = false,
                        error = friendly_error(t),
                    )
                    on_complete?.invoke(false)
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
                    .map { it.email.lowercase(java.util.Locale.ROOT).trim() }
                    .toSet()
                val existing_names = _state.value.contacts
                    .map { it.name.lowercase(java.util.Locale.ROOT).trim() }
                    .toSet()
                val new_contacts = device_contacts.filter { contact ->
                    if (contact.email.isNotBlank()) {
                        contact.email.lowercase(java.util.Locale.ROOT).trim() !in existing_emails
                    } else {
                        contact.name.lowercase(java.util.Locale.ROOT).trim() !in existing_names
                    }
                }
                var imported = 0
                var last_failure: Throwable? = null
                for (contact in new_contacts) {
                    repository.create_contact(contact).fold(
                        onSuccess = { imported++ },
                        onFailure = { t -> last_failure = t },
                    )
                }
                val failure = last_failure
                if (imported == 0 && failure != null) {
                    _state.value = _state.value.copy(
                        is_syncing = false,
                        error = friendly_error(failure),
                    )
                    return@launch
                }
                _state.value = _state.value.copy(
                    is_syncing = false,
                    sync_message = if (imported > 0) context.resources.getQuantityString(R.plurals.imported_contacts, imported, imported) else context.getString(R.string.no_new_contacts),
                )
                load_contacts()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(is_syncing = false)
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_syncing = false,
                    error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.something_went_wrong)),
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

    private val auto_save_in_flight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun auto_save_recipients(recipients: List<String>, own_addresses: Set<String>) {
        val own = own_addresses.map { it.lowercase(java.util.Locale.ROOT).trim() }.toSet()
        val targets = recipients
            .map { it.lowercase(java.util.Locale.ROOT).trim() }
            .filter { it.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) }
            .filterNot { it in own }
            .distinct()
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (email in targets) {
                if (!auto_save_in_flight.add(email)) continue
                try {
                    val existing = repository.search_contacts(email, "email", 1).getOrNull() ?: continue
                    if (existing.isNotEmpty()) continue
                    val local_part = email.substringBefore("@")
                    val derived_name = local_part.split(".").filter { it.isNotBlank() }
                        .joinToString(" ") { part -> part.replaceFirstChar { ch -> ch.uppercase(java.util.Locale.ROOT) } }
                    repository.create_contact(
                        Contact(
                            id = "",
                            name = derived_name.ifBlank { local_part },
                            email = email,
                        ),
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                } finally {
                    auto_save_in_flight.remove(email)
                }
            }
        }
    }

    fun clear_sync_message() {
        _state.value = _state.value.copy(sync_message = null)
    }

    fun clear_flags() {
        _state.value = _state.value.copy(save_success = false, delete_success = false, error = null)
    }

    private fun friendly_error(t: Throwable): String {
        val msg = t.message?.lowercase(java.util.Locale.ROOT).orEmpty()
        return when {
            t is java.net.UnknownHostException -> context.getString(R.string.error_no_connection)
            t is java.net.ConnectException -> context.getString(R.string.error_no_connection)
            t is java.net.SocketTimeoutException -> context.getString(R.string.error_timeout)
            t is javax.net.ssl.SSLException -> context.getString(R.string.error_ssl)
            "timeout" in msg || "timed out" in msg -> context.getString(R.string.error_timeout)
            "connection" in msg && ("refused" in msg || "reset" in msg) -> context.getString(R.string.error_no_connection)
            else -> org.astermail.android.localized_api_error(context, t, context.getString(R.string.something_went_wrong))
        }
    }
}
