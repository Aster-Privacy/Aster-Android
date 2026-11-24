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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.CloudOff
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Mail
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Search
import compose.icons.tablericons.Trash
import compose.icons.tablericons.UserPlus
import compose.icons.tablericons.Users
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.contacts.ContactGroup
import org.astermail.android.contacts.ContactsUiState
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.ui.common.page_surface

private const val group_view_refresh_ms = 30_000L

internal enum class GroupViewBody { loading, failed, empty, members }

internal fun group_view_body(state: ContactsUiState, group_id: String): GroupViewBody {
    val contacts_failed = !state.contacts_loaded && !state.is_loading && state.error != null
    val memberships_failed = state.group_memberships == null && state.group_memberships_failed
    if (!state.memberships_ready) {
        return if (contacts_failed || memberships_failed) GroupViewBody.failed else GroupViewBody.loading
    }
    return if (state.group_member_ids(group_id).isEmpty()) GroupViewBody.empty else GroupViewBody.members
}

internal fun group_view_is_gone(state: ContactsUiState, group_id: String): Boolean =
    state.groups_loaded && !state.groups_load_failed && state.group_by_id(group_id) == null

internal fun group_add_candidates(state: ContactsUiState, group_id: String, query: String): List<Contact> {
    val members = state.group_member_ids(group_id)
    val needle = query.trim()
    return state.contacts
        .asSequence()
        .filter { it.id !in members }
        .filter {
            needle.isEmpty() ||
                it.name.contains(needle, ignoreCase = true) ||
                it.email.contains(needle, ignoreCase = true)
        }
        .sortedWith(
            compareBy<Contact, String>(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.email } }
                .thenBy { it.email },
        )
        .toList()
}

@Composable
fun ContactGroupScreen(
    group_id: String,
    on_back: () -> Unit,
    on_open_contact: (String) -> Unit,
    on_compose_to: (List<String>) -> Unit,
    vm: ContactsViewModel,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val ui_state by vm.state.collectAsStateWithLifecycle()
    val lifecycle_owner = LocalLifecycleOwner.current
    var last_known by remember { mutableStateOf<ContactGroup?>(null) }
    var closing by remember { mutableStateOf(false) }
    var show_menu by remember { mutableStateOf(false) }
    var show_edit by rememberSaveable { mutableStateOf(false) }
    var show_delete by rememberSaveable { mutableStateOf(false) }
    var show_add by rememberSaveable { mutableStateOf(false) }
    var pending_remove by remember { mutableStateOf<Contact?>(null) }
    val list_state = rememberLazyListState()

    val live_group = ui_state.group_by_id(group_id)
    if (live_group != null) last_known = live_group
    val group = live_group ?: last_known

    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.load_groups()
                if (!vm.state.value.contacts_loaded) vm.load_contacts(quiet = true)
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle_owner) {
        lifecycle_owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(group_view_refresh_ms)
                vm.load_groups()
            }
        }
    }
    val gone = group_view_is_gone(ui_state, group_id)
    LaunchedEffect(gone) {
        if (gone && !closing) {
            closing = true
            Toast.makeText(context, context.getString(R.string.group_deleted), Toast.LENGTH_SHORT).show()
            on_back()
        }
    }

    val members = remember(ui_state, group_id) { ui_state.group_members(group_id) }
    val body = group_view_body(ui_state, group_id)
    val count = if (ui_state.memberships_ready) members.size else (group?.contact_count ?: 0)
    val members_label = context.resources.getQuantityString(R.plurals.group_members_plural, count, count)
    val member_emails = remember(members) { members.map { it.email }.filter { it.isNotBlank() }.distinct() }
    val add_members_label = stringResource(R.string.add_members)
    val remove_label = stringResource(R.string.remove_from_group)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .systemBarsPadding()
            .testTag("contact_group_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = AsterSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterIconButton(
                    icon = TablerIcons.ArrowLeft,
                    content_description = stringResource(R.string.back),
                    onClick = on_back,
                    auto_mirror = true,
                    modifier = Modifier.testTag("back"),
                )
                if (group != null) {
                    group_avatar(color = group.color, icon = group.icon, size = 40.dp)
                    Spacer(Modifier.width(AsterSpacing.md))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { heading() },
                ) {
                    Text(
                        text = group?.name.orEmpty(),
                        color = colors.text_primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("contact_group_title"),
                    )
                    if (group != null && body != GroupViewBody.loading && body != GroupViewBody.failed) {
                        Text(
                            text = members_label,
                            color = colors.text_muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("contact_group_count"),
                        )
                    }
                }
                if (group != null) {
                    Box {
                        AsterIconButton(
                            icon = TablerIcons.DotsVertical,
                            content_description = stringResource(R.string.more_options),
                            onClick = { show_menu = true },
                            modifier = Modifier.testTag("contact_group_menu"),
                        )
                        aster_menu(expanded = show_menu, on_dismiss = { show_menu = false }) {
                            aster_menu_item(
                                label = stringResource(R.string.email_group),
                                icon = TablerIcons.Mail,
                                enabled = member_emails.isNotEmpty(),
                                test_tag = "contact_group_compose",
                                on_click = {
                                    show_menu = false
                                    on_compose_to(member_emails)
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.edit_group),
                                icon = TablerIcons.Pencil,
                                test_tag = "contact_group_menu_edit",
                                on_click = {
                                    show_menu = false
                                    show_edit = true
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.delete_group),
                                icon = TablerIcons.Trash,
                                destructive = true,
                                test_tag = "contact_group_menu_delete",
                                on_click = {
                                    show_menu = false
                                    show_delete = true
                                },
                            )
                        }
                    }
                }
            }
            when {
                group == null && ui_state.groups_load_failed -> group_view_message(
                    icon = TablerIcons.CloudOff,
                    title = stringResource(R.string.group_load_failed),
                    action_label = stringResource(R.string.retry),
                    on_action = { vm.load_groups() },
                    modifier = Modifier.weight(1f),
                    test_tag = "contact_group_failed",
                )
                group == null || body == GroupViewBody.loading -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = colors.accent_blue,
                        modifier = Modifier.testTag("contact_group_loading"),
                    )
                }
                body == GroupViewBody.failed -> group_view_message(
                    icon = TablerIcons.CloudOff,
                    title = stringResource(R.string.group_load_failed),
                    action_label = stringResource(R.string.retry),
                    on_action = {
                        vm.load_groups()
                        if (!ui_state.contacts_loaded) vm.load_contacts()
                    },
                    modifier = Modifier.weight(1f),
                    test_tag = "contact_group_failed",
                )
                body == GroupViewBody.empty -> group_view_message(
                    icon = TablerIcons.Users,
                    title = stringResource(R.string.group_empty_title),
                    subtitle = stringResource(R.string.group_empty_hint),
                    action_label = add_members_label,
                    on_action = { show_add = true },
                    modifier = Modifier.weight(1f),
                    test_tag = "contact_group_empty",
                )
                else -> LazyColumn(
                    state = list_state,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("contact_group_members"),
                    contentPadding = PaddingValues(top = AsterSpacing.sm, bottom = 88.dp),
                ) {
                    itemsIndexed(members, key = { _, contact -> contact.id }) { index, contact ->
                        Box(modifier = Modifier.animateItem().testTag("contact_group_member_" + contact.id)) {
                            ContactRow(
                                contact = contact,
                                is_selected = false,
                                is_selecting = false,
                                is_pending = false,
                                is_first = index == 0,
                                is_last = index == members.size - 1,
                                on_click = { on_open_contact(contact.id) },
                                on_toggle_selection = { pending_remove = contact },
                                avatar_toggles_selection = false,
                                long_click_label = remove_label,
                            )
                        }
                    }
                }
            }
        }
        if (group != null && body == GroupViewBody.members) {
            ExtendedFloatingActionButton(
                onClick = { show_add = true },
                containerColor = colors.accent_blue,
                contentColor = colors.on_accent,
                shape = SquircleShape(AsterRadius.pill),
                icon = { Icon(imageVector = TablerIcons.UserPlus, contentDescription = null) },
                text = { Text(text = add_members_label, fontWeight = FontWeight.Medium) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AsterSpacing.lg)
                    .semantics { contentDescription = add_members_label }
                    .testTag("contact_group_add_fab"),
            )
        }
    }

    if (show_add && group != null && ui_state.memberships_ready) {
        add_members_dialog(
            state = ui_state,
            group_id = group.id,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = { show_add = false },
            on_add = { ids ->
                vm.add_contacts_to_group(group.id, ids) { added ->
                    if (added > 0) {
                        show_add = false
                        Toast.makeText(
                            context,
                            context.resources.getQuantityString(R.plurals.members_added, added, added),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }
    if (show_edit && group != null) {
        new_group_dialog(
            existing_count = ui_state.groups.size,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = { show_edit = false },
            on_create = { name, color, icon ->
                vm.update_group(group.id, name, color, icon) { ok ->
                    if (ok) {
                        show_edit = false
                        Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            existing = group,
        )
    }
    if (show_delete && group != null) {
        AsterAlertDialog(
            on_dismiss = { show_delete = false },
            title = stringResource(R.string.delete_group),
            message = stringResource(R.string.delete_group_message),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                vm.delete_group(group.id) { ok ->
                    show_delete = false
                    if (ok && !closing) {
                        closing = true
                        Toast.makeText(context, context.getString(R.string.group_deleted), Toast.LENGTH_SHORT).show()
                        on_back()
                    }
                }
            },
        )
    }
    pending_remove?.let { contact ->
        AsterAlertDialog(
            on_dismiss = { pending_remove = null },
            title = remove_label,
            message = stringResource(
                R.string.remove_from_group_message,
                contact.name.ifBlank { contact.email },
                group?.name.orEmpty(),
            ),
            confirm_label = stringResource(R.string.remove),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                pending_remove = null
                vm.remove_contact_from_group(group_id, contact.id) { ok ->
                    if (ok) {
                        Toast.makeText(context, context.getString(R.string.removed_from_group), Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun group_view_message(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    action_label: String,
    on_action: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    test_tag: String,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AsterSpacing.xxl)
            .testTag(test_tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = title,
            color = colors.text_primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = subtitle,
                color = colors.text_muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(AsterSpacing.lg))
        AsterDialogPrimaryButton(
            label = action_label,
            onClick = on_action,
            modifier = Modifier.testTag(test_tag + "_action"),
        )
    }
}

@Composable
private fun add_members_dialog(
    state: ContactsUiState,
    group_id: String,
    is_busy: Boolean,
    on_dismiss: () -> Unit,
    on_add: (Set<String>) -> Unit,
) {
    val colors = AsterMaterial.colors
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val all_candidates = remember(state.contacts, state.group_memberships, group_id) {
        group_add_candidates(state, group_id, "")
    }
    val candidates = remember(all_candidates, query) {
        if (query.isBlank()) all_candidates else group_add_candidates(state, group_id, query)
    }
    val valid_ids = remember(all_candidates) { all_candidates.mapTo(HashSet()) { it.id } }
    val picked_set = picked.filterTo(LinkedHashSet()) { it in valid_ids }
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.add_members),
        is_busy = is_busy,
        body = {
            Column(modifier = Modifier.testTag("add_members_dialog")) {
                if (all_candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.add_members_none_left),
                        color = colors.text_secondary,
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("add_members_none_left"),
                    )
                    return@Column
                }
                AsterTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_contacts),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_members_search"),
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                if (candidates.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = TablerIcons.Search,
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.no_contacts_match, query.trim()),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .clip(SquircleShape(AsterRadius.lg))
                            .background(colors.text_primary.copy(alpha = 0.04f))
                            .border(
                                width = 1.dp,
                                color = colors.text_primary.copy(alpha = 0.08f),
                                shape = SquircleShape(AsterRadius.lg),
                            )
                            .padding(6.dp)
                            .testTag("add_members_list"),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(candidates, key = { _, contact -> contact.id }) { _, contact ->
                            val checked = contact.id in picked_set
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SquircleShape(AsterRadius.sm))
                                    .background(
                                        if (checked) colors.accent_blue.copy(alpha = 0.12f) else Color.Transparent,
                                    )
                                    .clickable(role = Role.Checkbox, enabled = !is_busy) {
                                        picked = if (checked) picked - contact.id else picked + contact.id
                                    }
                                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs)
                                    .testTag("add_members_row_" + contact.id),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colors.accent_blue,
                                        checkmarkColor = colors.on_accent,
                                    ),
                                )
                                Spacer(Modifier.width(AsterSpacing.sm))
                                ContactAvatar(
                                    avatar_url = contact.avatar_url,
                                    email = contact.email,
                                    name = contact.name,
                                    size = 32.dp,
                                    profile_color = contact.profile_color,
                                )
                                Spacer(Modifier.width(AsterSpacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name.ifBlank { contact.email },
                                        color = colors.text_primary,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (contact.name.isNotBlank() && contact.email.isNotBlank()) {
                                        Text(
                                            text = contact.email,
                                            color = colors.text_muted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        footer = {
            if (all_candidates.isEmpty()) {
                AsterDialogPrimaryButton(
                    label = stringResource(R.string.done),
                    onClick = on_dismiss,
                    modifier = Modifier.weight(1f),
                )
            } else {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = on_dismiss,
                    enabled = !is_busy,
                    modifier = Modifier.weight(1f),
                )
                AsterDialogPrimaryButton(
                    label = if (picked_set.isEmpty()) {
                        stringResource(R.string.add_members)
                    } else {
                        stringResource(R.string.add_members_count, picked_set.size)
                    },
                    onClick = { on_add(picked_set) },
                    enabled = picked_set.isNotEmpty() && !is_busy,
                    is_loading = is_busy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_members_confirm"),
                )
            }
        },
    )
}
