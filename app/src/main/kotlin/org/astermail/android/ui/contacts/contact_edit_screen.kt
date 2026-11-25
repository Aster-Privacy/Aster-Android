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

package org.astermail.android.ui.contacts

import org.astermail.android.ui.common.remember_image_picker
import org.astermail.android.ui.common.vertical_scroll_with_indicator
import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.contacts.ContactEventKind
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.contacts.ContactPhotoEncodeResult
import org.astermail.android.contacts.encode_contact_photo_result
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterActionRow
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.common.page_surface

@Composable
fun ContactEditScreen(
    contact_id: String?,
    on_back: () -> Unit,
    on_saved: () -> Unit,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val ui_state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(contact_id) {
        if (contact_id != null && vm.state.value.selected_contact?.id != contact_id) vm.load_contact(contact_id)
    }

    val context = LocalContext.current
    var save_requested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ui_state.save_success) {
        if (ui_state.save_success && save_requested) {
            save_requested = false
            vm.clear_flags()
            on_saved()
        }
    }

    LaunchedEffect(ui_state.error) {
        val message = ui_state.error
        if (message != null && save_requested) {
            save_requested = false
            org.astermail.android.ui.common.app_toast.show(org.astermail.android.ui.common.TopToastState(message = message.toString(), duration_ms = org.astermail.android.ui.common.toast_action_duration_ms))
            vm.clear_flags()
        }
    }

    val source = if (contact_id == null) {
        null
    } else {
        ui_state.selected_contact?.takeIf { it.id == contact_id }
            ?: ui_state.contacts.firstOrNull { it.id == contact_id }
    }
    val contact_unavailable = contact_id != null && source == null

    var name by rememberSaveable { mutableStateOf(source?.name.orEmpty()) }
    var emails by rememberSaveable(stateSaver = editable_entry_list_saver) {
        mutableStateOf(initial_email_rows(source))
    }
    var phones by rememberSaveable(stateSaver = editable_entry_list_saver) {
        mutableStateOf(initial_phone_rows(source))
    }
    var postals by rememberSaveable(stateSaver = editable_postal_list_saver) {
        mutableStateOf(initial_postal_rows(source))
    }
    var company by rememberSaveable { mutableStateOf(source?.company.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(source?.title.orEmpty()) }
    var events by rememberSaveable(stateSaver = editable_event_list_saver) {
        mutableStateOf(initial_event_rows(source))
    }
    var events_touched by rememberSaveable { mutableStateOf(false) }
    var website by rememberSaveable { mutableStateOf(source?.website.orEmpty()) }
    var twitter by rememberSaveable { mutableStateOf(source?.twitter.orEmpty()) }
    var linkedin by rememberSaveable { mutableStateOf(source?.linkedin.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(source?.notes.orEmpty()) }
    var relations by rememberSaveable(stateSaver = editable_relation_list_saver) {
        mutableStateOf(initial_relation_rows(source))
    }
    var relations_touched by rememberSaveable { mutableStateOf(false) }
    var socials by rememberSaveable(stateSaver = editable_social_list_saver) {
        mutableStateOf(initial_social_rows(source))
    }
    var socials_touched by rememberSaveable { mutableStateOf(false) }
    var avatar_url by rememberSaveable { mutableStateOf(source?.avatar_url.orEmpty()) }
    var loaded_contact_id by rememberSaveable { mutableStateOf<String?>(null) }
    var show_email_errors by rememberSaveable { mutableStateOf(false) }
    val invalid_email_text = stringResource(R.string.error_invalid_email)
    val email = emails.firstOrNull { it.value.isNotBlank() }?.value.orEmpty()

    LaunchedEffect(source) {
        if (source != null && source.id != loaded_contact_id) {
            loaded_contact_id = source.id
            name = source.name
            emails = initial_email_rows(source)
            phones = initial_phone_rows(source)
            postals = initial_postal_rows(source)
            company = source.company
            title = source.title
            events = initial_event_rows(source)
            events_touched = false
            website = source.website
            twitter = source.twitter
            linkedin = source.linkedin
            notes = source.notes
            relations = initial_relation_rows(source)
            relations_touched = false
            socials = initial_social_rows(source)
            socials_touched = false
            avatar_url = source.avatar_url
        }
    }

    val photo_scope = rememberCoroutineScope()
    var photo_busy by remember { mutableStateOf(false) }
    val photo_picker = remember_image_picker { uri ->
        if (!photo_busy) {
            photo_busy = true
            photo_scope.launch {
                val result = withContext(Dispatchers.Default) {
                    runCatching { encode_contact_photo_result(context, uri) }
                        .getOrDefault(ContactPhotoEncodeResult.Unreadable)
                }
                photo_busy = false
                when (result) {
                    is ContactPhotoEncodeResult.Encoded -> avatar_url = result.data_uri
                    ContactPhotoEncodeResult.TooLarge -> org.astermail.android.ui.common.app_toast.show(org.astermail.android.ui.common.TopToastState(message = context.getString(R.string.photo_too_large).toString(), duration_ms = org.astermail.android.ui.common.toast_action_duration_ms))
                    ContactPhotoEncodeResult.Unreadable -> org.astermail.android.ui.common.app_toast.show(org.astermail.android.ui.common.TopToastState(message = context.getString(R.string.contact_photo_unreadable).toString(), duration_ms = org.astermail.android.ui.common.toast_action_duration_ms))
                }
            }
        }
    }

    val baseline_fingerprint = remember(source?.id, loaded_contact_id) {
        contact_edit_fingerprint(
            name = source?.name.orEmpty(),
            emails = initial_email_rows(source),
            phones = initial_phone_rows(source),
            postals = initial_postal_rows(source),
            fields = listOf(
                source?.company.orEmpty(),
                source?.title.orEmpty(),
                event_fingerprint(initial_event_rows(source)),
                source?.website.orEmpty(),
                source?.twitter.orEmpty(),
                source?.linkedin.orEmpty(),
                source?.notes.orEmpty(),
            ),
            relations = initial_relation_rows(source),
            socials = initial_social_rows(source),
            avatar_url = source?.avatar_url.orEmpty(),
        )
    }
    val current_fingerprint = contact_edit_fingerprint(
        name = name,
        emails = emails,
        phones = phones,
        postals = postals,
        fields = listOf(company, title, event_fingerprint(events), website, twitter, linkedin, notes),
        relations = relations,
        socials = socials,
        avatar_url = avatar_url,
    )
    val is_dirty = current_fingerprint != baseline_fingerprint
    var show_discard_dialog by rememberSaveable { mutableStateOf(false) }
    val guard_back = is_dirty && !save_requested && !contact_unavailable
    val request_back: () -> Unit = {
        if (guard_back) show_discard_dialog = true else on_back()
    }
    androidx.activity.compose.BackHandler(enabled = guard_back) {
        show_discard_dialog = true
    }
    val nav_bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .exclude(androidx.compose.foundation.layout.WindowInsets.ime)
        .asPaddingValues()
        .calculateBottomPadding()

    if (show_discard_dialog) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_discard_dialog = false },
            title = stringResource(R.string.contact_discard_changes_title),
            message = stringResource(R.string.contact_discard_changes_message),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.contact_keep_editing),
                    onClick = { show_discard_dialog = false },
                    modifier = Modifier.weight(1f),
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.discard),
                    onClick = {
                        show_discard_dialog = false
                        on_back()
                    },
                    modifier = Modifier.weight(1f),
                )
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .statusBarsPadding()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.ime.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = TablerIcons.ArrowLeft,
                auto_mirror = true,
                content_description = stringResource(R.string.back),
                onClick = request_back,
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = if (contact_id == null) stringResource(R.string.new_contact) else stringResource(R.string.edit_contact),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val has_content = name.isNotBlank() ||
                emails.any { it.value.isNotBlank() } ||
                phones.any { it.value.isNotBlank() }
            val can_save = has_content && (contact_id == null || is_dirty)
            AsterGhostButton(
                label = stringResource(R.string.save),
                enabled = can_save && !save_requested && !photo_busy && !ui_state.is_loading && !contact_unavailable,
                onClick = {
                    if (save_requested) return@AsterGhostButton
                    if (emails.any { !is_valid_contact_email(it.value) }) {
                        show_email_errors = true
                        return@AsterGhostButton
                    }
                    val contact = Contact(
                        id = contact_id ?: "",
                        name = name.trim(),
                        email = "",
                        company = company.trim(),
                        title = title.trim(),
                        birthday = source?.birthday.orEmpty(),
                        events = source?.events,
                        website = website.trim(),
                        twitter = twitter.trim(),
                        linkedin = linkedin.trim(),
                        notes = notes.trim(),
                        avatar_url = avatar_url,
                        profile_color = source?.profile_color.orEmpty(),
                        is_favorite = source?.is_favorite ?: false,
                        groups = source?.groups ?: emptyList(),
                        raw_json = source?.raw_json.orEmpty(),
                        relations = if (relations_touched) relations.map { it.to_relation() } else source?.relations,
                        social_networks = if (socials_touched) socials.map { it.to_social() } else source?.social_networks,
                    ).with_typed_fields(
                        emails = emails.map { it.to_entry() },
                        phones = phones.map { it.to_entry() },
                        addresses = postals.map { it.to_postal() },
                    ).let { if (events_touched) apply_event_edits(it, source, events) else it }
                    save_requested = true
                    vm.save_contact(contact, contact_id)
                },
            )
        }
        AsterDivider()

        if (contact_unavailable && !ui_state.contact_loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.failed_to_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text_secondary,
                    modifier = Modifier.weight(1f),
                )
                AsterGhostButton(
                    label = stringResource(R.string.retry),
                    onClick = { if (contact_id != null) vm.load_contact(contact_id) },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .vertical_scroll_with_indicator()
                .padding(horizontal = AsterSpacing.md)
                .padding(top = AsterSpacing.md, bottom = AsterSpacing.xxl + nav_bottom),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
        ) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AsterSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (avatar_url.isBlank() && name.isBlank() && email.isBlank()) {
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = TablerIcons.User,
                            contentDescription = stringResource(R.string.contact_photo),
                            tint = colors.text_muted,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                } else {
                    ContactAvatar(
                        avatar_url = avatar_url,
                        email = email,
                        name = name,
                        size = 80.dp,
                        content_description = stringResource(R.string.contact_photo),
                        profile_color = source?.profile_color.orEmpty(),
                    )
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsterGhostButton(
                        label = if (avatar_url.isBlank()) {
                            stringResource(R.string.add_photo)
                        } else {
                            stringResource(R.string.change_photo)
                        },
                        enabled = !photo_busy,
                        onClick = {
                            photo_picker.launch()
                        },
                    )
                    if (avatar_url.isNotBlank() && !photo_busy) {
                        AsterGhostButton(
                            label = stringResource(R.string.remove_photo),
                            onClick = { avatar_url = "" },
                        )
                    }
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = name.trim().ifBlank { stringResource(R.string.unnamed_contact) },
                    color = if (name.isBlank()) colors.text_muted else colors.text_primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = AsterSpacing.lg),
                )
            }
            }

            FormSection(icon = TablerIcons.User, title = stringResource(R.string.personal)) {
                FormField(stringResource(R.string.name), name, keyboard_capitalization = KeyboardCapitalization.Words) { name = it }
            }

            FormSection(icon = TablerIcons.Mail, title = stringResource(R.string.email)) {
                ContactEntryRows(
                    kind = ContactEntryKind.EMAIL,
                    entries = emails,
                    on_change = { emails = it },
                    add_label = stringResource(R.string.contact_add_email),
                    field_label = stringResource(R.string.email),
                    keyboard_type = KeyboardType.Email,
                    error_for = { value, blurred ->
                        invalid_email_text.takeIf { (show_email_errors || blurred) && !is_valid_contact_email(value) }
                    },
                )
            }

            FormSection(icon = TablerIcons.Phone, title = stringResource(R.string.phone)) {
                ContactEntryRows(
                    kind = ContactEntryKind.PHONE,
                    entries = phones,
                    on_change = { phones = it },
                    add_label = stringResource(R.string.contact_add_phone),
                    field_label = stringResource(R.string.phone),
                    keyboard_type = KeyboardType.Phone,
                )
            }

            FormSection(icon = TablerIcons.Briefcase, title = stringResource(R.string.work)) {
                FormField(stringResource(R.string.company), company, keyboard_capitalization = KeyboardCapitalization.Words) { company = it }
                FormField(stringResource(R.string.title), title, keyboard_capitalization = KeyboardCapitalization.Words) { title = it }
            }

            FormSection(icon = TablerIcons.MapPin, title = stringResource(R.string.address)) {
                ContactPostalRows(postals = postals, on_change = { postals = it })
            }

            FormSection(icon = TablerIcons.Calendar, title = stringResource(R.string.contact_events)) {
                ContactEventRows(
                    events = events,
                    on_change = {
                        events = it
                        events_touched = true
                    },
                )
            }

            FormSection(icon = TablerIcons.Link, title = stringResource(R.string.social)) {
                FormField(stringResource(R.string.website), website, keyboard_type = KeyboardType.Uri) { website = it }
                FormField(stringResource(R.string.twitter), twitter) { twitter = it }
                FormField(stringResource(R.string.linkedin), linkedin) { linkedin = it }
                ContactSocialRows(
                    socials = socials,
                    on_change = {
                        socials = it
                        socials_touched = true
                    },
                )
            }

            FormSection(icon = TablerIcons.Users, title = stringResource(R.string.contact_relations)) {
                ContactRelationRows(
                    relations = relations,
                    on_change = {
                        relations = it
                        relations_touched = true
                    },
                )
            }

            FormSection(icon = TablerIcons.Notes, title = stringResource(R.string.notes)) {
                val notes_requester = remember { BringIntoViewRequester() }
                var notes_focused by remember { mutableStateOf(false) }
                LaunchedEffect(notes_focused, notes, ui_state.is_loading) {
                    if (notes_focused) {
                        delay(NOTES_SCROLL_DELAY_MS)
                        notes_requester.bringIntoView()
                    }
                }
                FormField(
                    label = stringResource(R.string.notes),
                    value = notes,
                    single_line = false,
                    keyboard_capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier
                        .bringIntoViewRequester(notes_requester)
                        .onFocusChanged { notes_focused = it.isFocused },
                    on_change = { notes = it },
                )
            }
        }
    }
}

@Composable
private fun FormSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.text_secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = title,
                    color = colors.text_secondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.6.sp,
                )
            }
            content()
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    keyboard_type: KeyboardType = KeyboardType.Text,
    keyboard_capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    single_line: Boolean = true,
    error_text: String? = null,
    modifier: Modifier = Modifier,
    on_change: (String) -> Unit,
) {
    AsterTextField(
        rest_border = true,
        value = value,
        onValueChange = on_change,
        modifier = modifier,
        label = label,
        error_text = error_text,
        singleLine = single_line,
        min_lines = if (single_line) 1 else 3,
        keyboard_options = KeyboardOptions(
            keyboardType = keyboard_type,
            capitalization = keyboard_capitalization,
            imeAction = if (single_line) ImeAction.Next else ImeAction.Default,
        ),
    )
}

private const val NOTES_SCROLL_DELAY_MS = 250L

private fun event_fingerprint(events: List<EditableEvent>): String =
    events.map { it.to_event() }.filter { it.date.isNotEmpty() }.joinToString("|") { "${it.kind}:${it.date}:${it.label}" }

private fun contact_edit_fingerprint(
    name: String,
    emails: List<EditableEntry>,
    phones: List<EditableEntry>,
    postals: List<EditablePostal>,
    fields: List<String>,
    relations: List<EditableRelation>,
    socials: List<EditableSocial>,
    avatar_url: String,
): List<Any> = listOf(
    name.trim(),
    emails.map { it.to_entry().trimmed() }.filter { it.value.isNotEmpty() },
    phones.map { it.to_entry().trimmed() }.filter { it.value.isNotEmpty() },
    postals.map { it.to_postal().trimmed() }.filterNot { it.is_blank() },
    fields.map { it.trim() },
    relations.map { it.to_relation() }.filter { it.value.isNotBlank() },
    socials.map { it.to_social() }.filter { it.value.isNotBlank() },
    avatar_url,
)

private fun initial_email_rows(source: Contact?): List<EditableEntry> =
    source?.email_entries()?.map { EditableEntry.of(it) }?.ifEmpty { null }
        ?: listOf(EditableEntry(type = ContactEntryKind.EMAIL.default_type))

private fun initial_phone_rows(source: Contact?): List<EditableEntry> =
    source?.phone_entries()?.map { EditableEntry.of(it) }?.ifEmpty { null }
        ?: listOf(EditableEntry(type = ContactEntryKind.PHONE.default_type))

private fun initial_postal_rows(source: Contact?): List<EditablePostal> =
    source?.address_entries()?.map { EditablePostal.of(it) } ?: emptyList()

private fun initial_relation_rows(source: Contact?): List<EditableRelation> =
    source?.relations.orEmpty().map { EditableRelation.of(it) }

private fun initial_event_rows(source: Contact?): List<EditableEvent> =
    source?.event_entries().orEmpty().map { EditableEvent.of(it) }

internal fun apply_event_edits(contact: Contact, source: Contact?, rows: List<EditableEvent>): Contact {
    val updated = contact.with_events(rows.map { it.to_event() })
    val legacy = source?.birthday.orEmpty()
    val unrepresented = legacy.isNotBlank() && source?.event_entries().orEmpty().none { it.kind == ContactEventKind.BIRTHDAY }
    return if (updated.birthday.isEmpty() && unrepresented) updated.copy(birthday = legacy) else updated
}

private fun initial_social_rows(source: Contact?): List<EditableSocial> =
    source?.social_networks.orEmpty().map { EditableSocial.of(it) }

private val contact_email_regex = Regex("""^[^\s@<>,;]+@[^\s@<>,;]+\.[^\s@<>,;]+$""")

internal fun is_valid_contact_email(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.isEmpty() || contact_email_regex.matches(trimmed)
}
