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

private const val CONTACT_TRASH_RETENTION_DAYS = 30L

private const val DEVICE_PHOTO_MAX_BYTES = 400 * 1024

fun contact_trash_days_left(deleted_at: String): Int {
    val deleted = runCatching { java.time.Instant.parse(deleted_at) }.getOrNull()
        ?: return CONTACT_TRASH_RETENTION_DAYS.toInt()
    val elapsed = java.time.Duration.between(deleted, java.time.Instant.now()).toDays()
    return maxOf(0L, CONTACT_TRASH_RETENTION_DAYS - elapsed).toInt()
}

enum class ContactExportFormat {
    VCARD,
    CSV,
}

enum class ContactsTab {
    CONTACTS,
    GROUPS,
    TRASH,
}

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val trashed_contacts: List<Contact> = emptyList(),
    val selected_contact: Contact? = null,
    val is_loading: Boolean = false,
    val is_syncing: Boolean = false,
    val sync_message: String? = null,
    val error: String? = null,
    val save_success: Boolean = false,
    val delete_success: Boolean = false,
    val groups: List<ContactGroup> = emptyList(),
    val tab: ContactsTab = ContactsTab.CONTACTS,
    val selected_ids: Set<String> = emptySet(),
    val duplicate_clusters: List<DuplicateCluster> = emptyList(),
    val duplicates_dismissed: Boolean = false,
    val is_bulk_working: Boolean = false,
    val is_transferring: Boolean = false,
) {
    val is_selecting: Boolean get() = selected_ids.isNotEmpty()

    val duplicate_count: Int get() = count_duplicate_contacts(duplicate_clusters)

    val selected_contacts: List<Contact> get() = contacts.filter { it.id in selected_ids }
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    private var list_in_flight = false
    private var mutation_in_flight = false
    private var groups_in_flight = false

    fun load_contacts() {
        if (list_in_flight) return
        list_in_flight = true
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            val outcome = repository.fetch_contacts()
            list_in_flight = false
            outcome.fold(
                onSuccess = { contacts ->
                    val expired = contacts.filter { is_trash_expired(it.deleted_at) }
                    val expired_ids = expired.map { it.id }.toSet()
                    val kept = contacts.filterNot { it.id in expired_ids }
                    _state.value = with_contacts(_state.value, kept).copy(is_loading = false)
                    if (expired_ids.isNotEmpty()) {
                        launch { repository.bulk_delete_contacts(expired_ids.toList()) }
                    }
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
        val target = _state.value.contacts.firstOrNull { it.id == contact_id }
            ?: _state.value.selected_contact?.takeIf { it.id == contact_id }
        mutation_in_flight = true
        _state.value = _state.value.copy(is_loading = true, error = null, delete_success = false)
        viewModelScope.launch {
            val trashed_at = java.time.Instant.now().toString()
            val outcome = if (target != null) {
                repository.trash_contact(target)
            } else {
                repository.delete_contact(contact_id).map { }
            }
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    val remaining = _state.value.contacts.filter { it.id != contact_id }
                    val trashed = _state.value.trashed_contacts +
                        listOfNotNull(target?.copy(deleted_at = trashed_at))
                    _state.value = with_contacts(_state.value, remaining + trashed).copy(
                        is_loading = false,
                        delete_success = true,
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
                val existing_emails = email_keys(_state.value.contacts)
                val existing_names = name_keys(_state.value.contacts)
                val trashed_emails = email_keys(_state.value.trashed_contacts)
                val trashed_names = name_keys(_state.value.trashed_contacts)
                val candidates = device_contacts.filterNot {
                    matches_any(it, existing_emails, existing_names)
                }
                val in_trash = candidates.count { matches_any(it, trashed_emails, trashed_names) }
                val new_contacts = candidates.filterNot {
                    matches_any(it, trashed_emails, trashed_names)
                }
                var imported = 0
                var last_failure: Throwable? = null
                var failed = 0
                for (contact in new_contacts) {
                    repository.create_contact(contact).fold(
                        onSuccess = { imported++ },
                        onFailure = { t ->
                            last_failure = t
                            failed++
                        },
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
                    sync_message = when {
                        imported > 0 -> context.resources.getQuantityString(
                            R.plurals.imported_contacts,
                            imported,
                            imported,
                        )
                        in_trash > 0 -> context.getString(R.string.contacts_already_in_trash)
                        failed == 0 -> context.getString(R.string.no_new_contacts)
                        else -> null
                    },
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

    fun import_contacts_from_file(context: Context, uri: android.net.Uri) {
        if (_state.value.is_transferring) return
        _state.value = _state.value.copy(is_transferring = true, error = null, sync_message = null)
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val name = display_name_for(context, uri)
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }.orEmpty()
                    parse_contacts_file(name, content)
                }
                if (parsed.isEmpty()) {
                    _state.value = _state.value.copy(
                        is_transferring = false,
                        error = context.getString(R.string.contacts_import_empty),
                    )
                    return@launch
                }
                val existing_emails = email_keys(_state.value.contacts)
                val existing_names = name_keys(_state.value.contacts)
                val trashed_emails = email_keys(_state.value.trashed_contacts)
                val trashed_names = name_keys(_state.value.trashed_contacts)
                val seen_emails = mutableSetOf<String>()
                val candidates = parsed.filter { contact ->
                    val email = contact.email.lowercase(java.util.Locale.ROOT).trim()
                    if (email.isNotBlank()) {
                        email !in existing_emails && seen_emails.add(email)
                    } else {
                        contact.name.lowercase(java.util.Locale.ROOT).trim() !in existing_names
                    }
                }
                val in_trash = candidates.count { matches_any(it, trashed_emails, trashed_names) }
                val new_contacts = candidates.filterNot {
                    matches_any(it, trashed_emails, trashed_names)
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
                        is_transferring = false,
                        error = friendly_error(failure),
                    )
                    return@launch
                }
                _state.value = _state.value.copy(
                    is_transferring = false,
                    sync_message = if (imported > 0) {
                        context.resources.getQuantityString(
                            R.plurals.imported_contacts,
                            imported,
                            imported,
                        )
                    } else if (in_trash > 0) {
                        context.getString(R.string.contacts_already_in_trash)
                    } else {
                        context.getString(R.string.no_new_contacts)
                    },
                )
                load_contacts()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(is_transferring = false)
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_transferring = false,
                    error = org.astermail.android.localized_api_error(
                        context,
                        t,
                        context.getString(R.string.contacts_import_failed),
                    ),
                )
            }
        }
    }

    fun export_contacts_to_file(
        context: Context,
        uri: android.net.Uri,
        format: ContactExportFormat,
        only_selected: Boolean,
    ) {
        if (_state.value.is_transferring) return
        val targets = if (only_selected) {
            _state.value.selected_contacts
        } else {
            _state.value.contacts
        }
        if (targets.isEmpty()) return
        _state.value = _state.value.copy(is_transferring = true, error = null, sync_message = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val payload = when (format) {
                        ContactExportFormat.VCARD -> contacts_to_vcard(targets)
                        ContactExportFormat.CSV -> contacts_to_csv(targets)
                    }
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException("stream unavailable")
                }
                _state.value = _state.value.copy(
                    is_transferring = false,
                    sync_message = context.resources.getQuantityString(
                        R.plurals.exported_contacts,
                        targets.size,
                        targets.size,
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(is_transferring = false)
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_transferring = false,
                    error = org.astermail.android.localized_api_error(
                        context,
                        t,
                        context.getString(R.string.contacts_export_failed),
                    ),
                )
            }
        }
    }

    private fun display_name_for(context: Context, uri: android.net.Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index) ?: ""
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun device_photo_data_uri(context: Context, contact_id: String): String {
        val id = contact_id.toLongOrNull() ?: return ""
        val uri = android.content.ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI,
            id,
        )
        val bytes = try {
            ContactsContract.Contacts
                .openContactPhotoInputStream(context.contentResolver, uri, true)
                ?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return ""
        if (bytes.isEmpty() || bytes.size > DEVICE_PHOTO_MAX_BYTES) return ""
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    private fun email_keys(contacts: List<Contact>): Set<String> =
        contacts
            .filter { it.email.isNotBlank() }
            .map { it.email.lowercase(java.util.Locale.ROOT).trim() }
            .toSet()

    private fun name_keys(contacts: List<Contact>): Set<String> =
        contacts.map { it.name.lowercase(java.util.Locale.ROOT).trim() }.toSet()

    private fun matches_any(contact: Contact, emails: Set<String>, names: Set<String>): Boolean =
        if (contact.email.isNotBlank()) {
            contact.email.lowercase(java.util.Locale.ROOT).trim() in emails
        } else {
            contact.name.lowercase(java.util.Locale.ROOT).trim() in names
        }

    private fun read_device_contacts(context: Context): List<Contact> {
        val contacts = linkedMapOf<String, Contact>()
        val resolver = context.contentResolver

        val name_projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_ID,
        )
        val with_photo = mutableSetOf<String>()

        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            name_projection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { cursor ->
            val id_idx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val name_idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photo_idx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_ID)
            if (id_idx < 0 || name_idx < 0) return@use
            while (cursor.moveToNext()) {
                val contact_id = cursor.getString(id_idx) ?: continue
                val name = cursor.getString(name_idx) ?: ""
                if (name.isBlank()) continue
                if (photo_idx >= 0 && !cursor.isNull(photo_idx)) with_photo.add(contact_id)
                contacts[contact_id] = Contact(
                    id = "",
                    name = name.trim(),
                    email = "",
                )
            }
        }

        if (contacts.isEmpty()) return emptyList()

        val mime_types = listOf(
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
        )

        val data_projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA7,
            ContactsContract.Data.DATA8,
            ContactsContract.Data.DATA9,
            ContactsContract.Data.DATA10,
        )

        val selection = ContactsContract.Data.MIMETYPE +
            " IN (" + mime_types.joinToString(",") { "?" } + ")"

        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            data_projection,
            selection,
            mime_types.toTypedArray(),
            null,
        )?.use { cursor ->
            val id_idx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val mime_idx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val d1_idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
            val d2_idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
            val d4_idx = cursor.getColumnIndex(ContactsContract.Data.DATA4)
            val d7_idx = cursor.getColumnIndex(ContactsContract.Data.DATA7)
            val d8_idx = cursor.getColumnIndex(ContactsContract.Data.DATA8)
            val d9_idx = cursor.getColumnIndex(ContactsContract.Data.DATA9)
            val d10_idx = cursor.getColumnIndex(ContactsContract.Data.DATA10)
            if (id_idx < 0 || mime_idx < 0 || d1_idx < 0) return@use

            fun column(index: Int): String =
                if (index < 0) "" else cursor.getString(index).orEmpty().trim()

            while (cursor.moveToNext()) {
                val contact_id = cursor.getString(id_idx) ?: continue
                val existing = contacts[contact_id] ?: continue
                val mime = cursor.getString(mime_idx) ?: continue
                val value = column(d1_idx)
                val kind = if (d2_idx < 0) 0 else cursor.getInt(d2_idx)

                contacts[contact_id] = when (mime) {
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        if (value.isBlank()) {
                            existing
                        } else if (kind == ContactsContract.CommonDataKinds.Email.TYPE_WORK) {
                            if (existing.work_email.isBlank()) {
                                existing.copy(work_email = value)
                            } else if (existing.email.isBlank()) {
                                existing.copy(email = value)
                            } else {
                                existing
                            }
                        } else if (existing.email.isBlank()) {
                            existing.copy(email = value)
                        } else if (existing.work_email.isBlank()) {
                            existing.copy(work_email = value)
                        } else {
                            existing
                        }
                    }

                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        if (value.isBlank()) {
                            existing
                        } else if (kind == ContactsContract.CommonDataKinds.Phone.TYPE_WORK ||
                            kind == ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE
                        ) {
                            if (existing.work_phone.isBlank()) {
                                existing.copy(work_phone = value)
                            } else if (existing.phone.isBlank()) {
                                existing.copy(phone = value)
                            } else {
                                existing
                            }
                        } else if (existing.phone.isBlank()) {
                            existing.copy(phone = value)
                        } else if (existing.work_phone.isBlank()) {
                            existing.copy(work_phone = value)
                        } else {
                            existing
                        }
                    }

                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        val job_title = column(d4_idx)
                        existing.copy(
                            company = if (existing.company.isBlank()) value else existing.company,
                            title = if (existing.title.isBlank()) job_title else existing.title,
                        )
                    }

                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        if (existing.address.isNotBlank() || existing.city.isNotBlank()) {
                            existing
                        } else {
                            existing.copy(
                                address = column(d4_idx).ifBlank { value },
                                city = column(d7_idx),
                                region = column(d8_idx),
                                postal_code = column(d9_idx),
                                country = column(d10_idx),
                            )
                        }
                    }

                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        if (kind == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY &&
                            value.isNotBlank() &&
                            existing.birthday.isBlank()
                        ) {
                            existing.copy(birthday = value)
                        } else {
                            existing
                        }
                    }

                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE ->
                        if (value.isNotBlank() && existing.website.isBlank()) {
                            existing.copy(website = value)
                        } else {
                            existing
                        }

                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE ->
                        if (value.isNotBlank() && existing.notes.isBlank()) {
                            existing.copy(notes = value)
                        } else {
                            existing
                        }

                    else -> existing
                }
            }
        }

        for (contact_id in with_photo) {
            val contact = contacts[contact_id] ?: continue
            val photo = device_photo_data_uri(context, contact_id)
            if (photo.isNotBlank()) contacts[contact_id] = contact.copy(avatar_url = photo)
        }

        return contacts.values.toList()
    }

    private val auto_save_in_flight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val auto_save_email_pattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun auto_save_recipients(recipients: List<String>, own_addresses: Set<String>) {
        val own = own_addresses.map { it.lowercase(java.util.Locale.ROOT).trim() }.toSet()
        val targets = recipients
            .map { it.lowercase(java.util.Locale.ROOT).trim() }
            .filter { it.matches(auto_save_email_pattern) }
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

    fun load_groups() {
        if (groups_in_flight) return
        groups_in_flight = true
        viewModelScope.launch {
            val outcome = repository.list_contact_groups()
            groups_in_flight = false
            outcome.fold(
                onSuccess = { groups ->
                    _state.value = _state.value.copy(groups = groups)
                },
                onFailure = { },
            )
        }
    }

    fun select_tab(tab: ContactsTab) {
        _state.value = _state.value.copy(tab = tab, selected_ids = emptySet())
        if (tab == ContactsTab.GROUPS) load_groups()
    }

    fun toggle_selection(contact_id: String) {
        val current = _state.value.selected_ids
        _state.value = _state.value.copy(
            selected_ids = if (contact_id in current) current - contact_id else current + contact_id,
        )
    }

    fun set_selection(ids: Set<String>) {
        _state.value = _state.value.copy(selected_ids = ids)
    }

    fun clear_selection() {
        _state.value = _state.value.copy(selected_ids = emptySet())
    }

    fun dismiss_duplicates() {
        _state.value = _state.value.copy(duplicates_dismissed = true)
    }

    fun create_group(
        name: String,
        color: String,
        icon: String? = null,
        on_complete: ((Boolean) -> Unit)? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || mutation_in_flight) {
            on_complete?.invoke(false)
            return
        }
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            val outcome = repository.create_contact_group(trimmed, color, icon)
            mutation_in_flight = false
            _state.value = _state.value.copy(is_bulk_working = false)
            outcome.fold(
                onSuccess = {
                    on_complete?.invoke(true)
                    load_groups()
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(error = friendly_error(t))
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun update_group(
        group_id: String,
        name: String,
        color: String,
        icon: String? = null,
        on_complete: ((Boolean) -> Unit)? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || mutation_in_flight) {
            on_complete?.invoke(false)
            return
        }
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            val outcome = repository.update_contact_group(group_id, trimmed, color, icon)
            mutation_in_flight = false
            _state.value = _state.value.copy(is_bulk_working = false)
            outcome.fold(
                onSuccess = {
                    on_complete?.invoke(true)
                    load_groups()
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(error = friendly_error(t))
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun delete_group(group_id: String, on_complete: ((Boolean) -> Unit)? = null) {
        if (mutation_in_flight) return
        mutation_in_flight = true
        viewModelScope.launch {
            val outcome = repository.delete_contact_group(group_id)
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        groups = _state.value.groups.filter { it.id != group_id },
                    )
                    on_complete?.invoke(true)
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(error = friendly_error(t))
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun add_selection_to_group(group_id: String, on_complete: ((Int) -> Unit)? = null) {
        val ids = _state.value.selected_ids.toList()
        if (ids.isEmpty() || mutation_in_flight) {
            on_complete?.invoke(0)
            return
        }
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            var added = 0
            var last_failure: Throwable? = null
            for (id in ids) {
                repository.add_contact_to_group(id, group_id).fold(
                    onSuccess = { added++ },
                    onFailure = { t -> last_failure = t },
                )
            }
            mutation_in_flight = false
            val failure = last_failure
            _state.value = _state.value.copy(
                is_bulk_working = false,
                selected_ids = emptySet(),
                error = if (added == 0 && failure != null) friendly_error(failure) else null,
            )
            on_complete?.invoke(added)
            load_groups()
            load_contacts()
        }
    }

    fun delete_selection(on_complete: ((Boolean) -> Unit)? = null) {
        val ids = _state.value.selected_ids.toList()
        if (ids.isEmpty() || mutation_in_flight) {
            on_complete?.invoke(false)
            return
        }
        val targets = _state.value.contacts.filter { it.id in ids }
        val untracked = ids.filterNot { id -> targets.any { it.id == id } }
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            val trashed_at = java.time.Instant.now().toString()
            val outcome = runCatching {
                for (contact in targets) repository.trash_contact(contact).getOrThrow()
                if (untracked.isNotEmpty()) repository.bulk_delete_contacts(untracked).getOrThrow()
            }
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    val remaining = _state.value.contacts.filterNot { it.id in ids }
                    val trashed = _state.value.trashed_contacts +
                        targets.map { it.copy(deleted_at = trashed_at) }
                    _state.value = with_contacts(_state.value, remaining + trashed).copy(
                        is_bulk_working = false,
                        delete_success = true,
                    )
                    on_complete?.invoke(true)
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_bulk_working = false,
                        error = friendly_error(t),
                    )
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun restore_contact(contact: Contact, on_complete: ((Boolean) -> Unit)? = null) {
        if (mutation_in_flight) return
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            val outcome = repository.restore_contact(contact)
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    val restored = contact.copy(deleted_at = "")
                    val trashed = _state.value.trashed_contacts.filterNot { it.id == contact.id }
                    _state.value = with_contacts(
                        _state.value,
                        _state.value.contacts + restored + trashed,
                    ).copy(is_bulk_working = false)
                    on_complete?.invoke(true)
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_bulk_working = false,
                        error = friendly_error(t),
                    )
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun delete_contact_forever(contact_id: String, on_complete: ((Boolean) -> Unit)? = null) {
        if (mutation_in_flight) return
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            val outcome = repository.delete_contact(contact_id)
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    val trashed = _state.value.trashed_contacts.filterNot { it.id == contact_id }
                    _state.value = with_contacts(
                        _state.value,
                        _state.value.contacts + trashed,
                    ).copy(is_bulk_working = false)
                    on_complete?.invoke(true)
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_bulk_working = false,
                        error = friendly_error(t),
                    )
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun empty_trash(on_complete: ((Boolean) -> Unit)? = null) {
        val ids = _state.value.trashed_contacts.map { it.id }
        if (ids.isEmpty() || mutation_in_flight) {
            on_complete?.invoke(false)
            return
        }
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            val outcome = repository.bulk_delete_contacts(ids)
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    _state.value = with_contacts(_state.value, _state.value.contacts).copy(
                        is_bulk_working = false,
                    )
                    on_complete?.invoke(true)
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_bulk_working = false,
                        error = friendly_error(t),
                    )
                    on_complete?.invoke(false)
                },
            )
        }
    }

    fun merge_duplicates(ordered: List<Contact>, on_complete: ((Boolean) -> Unit)? = null) {
        if (ordered.size < 2 || mutation_in_flight) {
            on_complete?.invoke(false)
            return
        }
        mutation_in_flight = true
        _state.value = _state.value.copy(is_bulk_working = true, error = null)
        viewModelScope.launch {
            val primary = ordered.first()
            val merged = merge_contacts(ordered)
            val discarded = ordered.drop(1).map { it.id }
            val outcome = repository.update_contact(primary.id, merged)
                .mapCatching { repository.bulk_delete_contacts(discarded).getOrThrow() }
            mutation_in_flight = false
            outcome.fold(
                onSuccess = {
                    val remaining = _state.value.contacts
                        .filterNot { it.id in discarded }
                        .map { if (it.id == primary.id) merged else it }
                    _state.value = with_contacts(
                        _state.value,
                        remaining + _state.value.trashed_contacts,
                    ).copy(
                        is_bulk_working = false,
                    )
                    on_complete?.invoke(true)
                    load_contacts()
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        is_bulk_working = false,
                        error = friendly_error(t),
                    )
                    on_complete?.invoke(false)
                },
            )
        }
    }

    private fun is_trash_expired(deleted_at: String): Boolean {
        if (deleted_at.isBlank()) return false
        runCatching { java.time.Instant.parse(deleted_at) }.getOrNull() ?: return false
        return contact_trash_days_left(deleted_at) == 0
    }

    private fun with_contacts(state: ContactsUiState, contacts: List<Contact>): ContactsUiState {
        val active = contacts.filter { it.deleted_at.isBlank() }
        val trashed = contacts.filter { it.deleted_at.isNotBlank() }
        val ids = active.map { it.id }.toSet()
        return state.copy(
            contacts = active,
            trashed_contacts = trashed.sortedByDescending { it.deleted_at },
            selected_ids = state.selected_ids.filter { it in ids }.toSet(),
            duplicate_clusters = find_duplicate_clusters(active),
        )
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
