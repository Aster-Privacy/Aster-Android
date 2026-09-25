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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.contacts.ContactGroup
import org.astermail.android.contacts.ContactsTab
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.contacts.DuplicateCluster
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.acrylic
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import org.astermail.android.ui.common.page_surface
import org.astermail.android.ui.mail.inbox_card_content_padding
import org.astermail.android.ui.mail.inbox_card_horizontal_margin
import org.astermail.android.ui.mail.inbox_card_read_color
import org.astermail.android.ui.mail.inbox_card_selected_color
import org.astermail.android.ui.mail.inbox_group_shape
import org.astermail.android.ui.mail.inbox_group_split

private val group_colors: List<String> =
    org.astermail.android.ui.common.label_color_palette

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    on_back: () -> Unit = {},
    on_open_contact: (String) -> Unit,
    on_create_contact: () -> Unit,
    on_open_drawer: (() -> Unit)? = null,
    on_compose_to: ((List<String>) -> Unit)? = null,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val ui_state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter_favorites by remember { mutableStateOf(false) }
    var show_sync_confirm by remember { mutableStateOf(false) }
    var show_bulk_menu by remember { mutableStateOf(false) }
    var show_overflow_menu by remember { mutableStateOf(false) }
    var pending_export_selected by remember { mutableStateOf(false) }
    var pending_export_format by remember {
        mutableStateOf(org.astermail.android.contacts.ContactExportFormat.VCARD)
    }
    val import_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.import_contacts_from_file(context, uri)
    }
    val on_export_target: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            vm.export_contacts_to_file(context, uri, pending_export_format, pending_export_selected)
        }
    }
    val vcard_export_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard"),
        on_export_target,
    )
    val csv_export_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
        on_export_target,
    )
    val start_export: (Boolean, org.astermail.android.contacts.ContactExportFormat) -> Unit =
        { selected, format ->
            pending_export_selected = selected
            pending_export_format = format
            val stamp = java.time.LocalDate.now().toString()
            if (format == org.astermail.android.contacts.ContactExportFormat.VCARD) {
                vcard_export_launcher.launch("aster-contacts-$stamp.vcf")
            } else {
                csv_export_launcher.launch("aster-contacts-$stamp.csv")
            }
        }
    var show_delete_confirm by remember { mutableStateOf(false) }
    var show_empty_trash_confirm by remember { mutableStateOf(false) }
    var pending_forever_delete by remember { mutableStateOf<Contact?>(null) }
    var show_new_group by remember { mutableStateOf(false) }
    var editing_group by remember { mutableStateOf<ContactGroup?>(null) }
    var show_group_picker by remember { mutableStateOf(false) }
    var merge_cluster by remember { mutableStateOf<DuplicateCluster?>(null) }
    var pending_group_delete by remember { mutableStateOf<ContactGroup?>(null) }

    val permission_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.sync_device_contacts(context)
        } else {
            Toast.makeText(context, context.getString(R.string.contacts_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ui_state.contacts.isEmpty()) vm.load_contacts()
        vm.load_groups()
    }

    LaunchedEffect(ui_state.sync_message) {
        ui_state.sync_message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clear_sync_message()
        }
    }

    LaunchedEffect(ui_state.error) {
        ui_state.error?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            vm.clear_flags()
        }
    }

    BackHandler(enabled = ui_state.is_selecting) { vm.clear_selection() }

    val filtered = remember(query, filter_favorites, ui_state.contacts) {
        ui_state.contacts
            .filter { if (filter_favorites) it.is_favorite else true }
            .filter {
                val q = query.trim().lowercase()
                if (q.isEmpty()) true
                else it.name.lowercase().contains(q) ||
                    it.email.lowercase().contains(q) ||
                    it.company.lowercase().contains(q)
            }
            .sortedBy { it.name.uppercase() }
    }

    val grouped = remember(filtered) {
        filtered.groupBy { letter_of(it.name) }.toSortedMap()
    }

    val filtered_groups = remember(query, ui_state.groups) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) ui_state.groups else ui_state.groups.filter { it.name.lowercase().contains(q) }
    }

    val filtered_trash = remember(query, ui_state.trashed_contacts) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            ui_state.trashed_contacts
        } else {
            ui_state.trashed_contacts.filter {
                it.name.lowercase().contains(q) ||
                    it.email.lowercase().contains(q) ||
                    it.company.lowercase().contains(q)
            }
        }
    }

    val on_compose_selection: () -> Unit = {
        val addresses = ui_state.selected_contacts
            .map { it.email.ifBlank { it.work_email } }
            .filter { it.isNotBlank() }
            .distinct()
        if (addresses.isNotEmpty()) on_compose_to?.invoke(addresses)
        vm.clear_selection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (ui_state.is_bulk_working && ui_state.bulk_total > 1) {
                bulk_progress_banner(
                    done = ui_state.bulk_done,
                    total = ui_state.bulk_total,
                )
            }
            if (ui_state.is_selecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = AsterSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsterIconButton(
                        icon = TablerIcons.X,
                        content_description = stringResource(R.string.clear_selection),
                        onClick = { vm.clear_selection() },
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.count_selected, ui_state.selected_ids.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text_primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    val all_selected = filtered.isNotEmpty() &&
                        filtered.all { ui_state.selected_ids.contains(it.id) }
                    AsterIconButton(
                        icon = if (all_selected) TablerIcons.SquareCheck else TablerIcons.Square,
                        content_description = if (all_selected) {
                            stringResource(R.string.deselect_all)
                        } else {
                            stringResource(R.string.select_all)
                        },
                        onClick = {
                            if (all_selected) {
                                vm.clear_selection()
                            } else {
                                vm.set_selection(filtered.map { it.id }.toSet())
                            }
                        },
                    )
                    Box {
                        AsterIconButton(
                            icon = TablerIcons.DotsVertical,
                            content_description = stringResource(R.string.more_options),
                            onClick = { show_bulk_menu = true },
                            modifier = Modifier.testTag("contacts_bulk_menu"),
                        )
                        aster_menu(
                            expanded = show_bulk_menu,
                            on_dismiss = { show_bulk_menu = false },
                        ) {
                            aster_menu_item(
                                label = stringResource(R.string.compose),
                                icon = TablerIcons.Mail,
                                test_tag = "bulk_compose",
                                on_click = {
                                    show_bulk_menu = false
                                    on_compose_selection()
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.add_to_group),
                                icon = TablerIcons.Users,
                                test_tag = "bulk_add_to_group",
                                on_click = {
                                    show_bulk_menu = false
                                    vm.load_groups()
                                    show_group_picker = true
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_vcard),
                                icon = TablerIcons.Download,
                                test_tag = "bulk_export_vcard",
                                on_click = {
                                    show_bulk_menu = false
                                    start_export(
                                        true,
                                        org.astermail.android.contacts.ContactExportFormat.VCARD,
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_csv),
                                icon = TablerIcons.Download,
                                test_tag = "bulk_export_csv",
                                on_click = {
                                    show_bulk_menu = false
                                    start_export(
                                        true,
                                        org.astermail.android.contacts.ContactExportFormat.CSV,
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.delete),
                                icon = TablerIcons.Trash,
                                destructive = true,
                                test_tag = "bulk_delete",
                                on_click = {
                                    show_bulk_menu = false
                                    show_delete_confirm = true
                                },
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = AsterSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (on_open_drawer != null) {
                        AsterIconButton(
                            icon = TablerIcons.Menu2,
                            content_description = stringResource(R.string.open_drawer),
                            onClick = on_open_drawer,
                        )
                    } else {
                        AsterIconButton(
                            icon = TablerIcons.ArrowLeft,
                            auto_mirror = true,
                            content_description = stringResource(R.string.back),
                            onClick = on_back,
                        )
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.contacts),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text_primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (ui_state.is_syncing) {
                        CircularProgressIndicator(
                            color = colors.accent_blue,
                            strokeWidth = 2.dp,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = AsterSpacing.sm),
                        )
                    }
                    Box {
                        AsterIconButton(
                            icon = TablerIcons.DotsVertical,
                            content_description = stringResource(R.string.more_options),
                            onClick = { show_overflow_menu = true },
                            modifier = Modifier.testTag("contacts_overflow_menu"),
                        )
                        aster_menu(
                            expanded = show_overflow_menu,
                            on_dismiss = { show_overflow_menu = false },
                        ) {
                            aster_menu_item(
                                label = stringResource(
                                    if (ui_state.is_syncing) R.string.syncing else R.string.sync_contacts,
                                ),
                                icon = TablerIcons.Refresh,
                                enabled = !ui_state.is_syncing,
                                test_tag = "contacts_sync",
                                on_click = {
                                    show_overflow_menu = false
                                    show_sync_confirm = true
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.import_contacts_file),
                                icon = TablerIcons.Upload,
                                test_tag = "contacts_import_file",
                                on_click = {
                                    show_overflow_menu = false
                                    import_launcher.launch(
                                        arrayOf(
                                            "text/x-vcard",
                                            "text/vcard",
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/vnd.ms-excel",
                                            "application/octet-stream",
                                            "text/*",
                                        ),
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_vcard),
                                icon = TablerIcons.Download,
                                enabled = ui_state.contacts.isNotEmpty(),
                                test_tag = "contacts_export_vcard",
                                on_click = {
                                    show_overflow_menu = false
                                    start_export(
                                        false,
                                        org.astermail.android.contacts.ContactExportFormat.VCARD,
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_csv),
                                icon = TablerIcons.Download,
                                enabled = ui_state.contacts.isNotEmpty(),
                                test_tag = "contacts_export_csv",
                                on_click = {
                                    show_overflow_menu = false
                                    start_export(
                                        false,
                                        org.astermail.android.contacts.ContactExportFormat.CSV,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            AsterDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = inbox_card_horizontal_margin, vertical = AsterSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val search_placeholder = stringResource(
                    when (ui_state.tab) {
                        ContactsTab.GROUPS -> R.string.search_groups
                        ContactsTab.TRASH -> R.string.search_trash
                        else -> R.string.search_contacts
                    },
                )
                Box(modifier = Modifier.weight(1f)) {
                    org.astermail.android.ui.common.list_search_bar(
                        query = query,
                        on_query_change = { query = it },
                        placeholder = search_placeholder,
                        test_tag = "contact_search_bar",
                    )
                }
                if (ui_state.tab == ContactsTab.CONTACTS) {
                    val favorites_label = stringResource(R.string.tab_favorites)
                    AsterIconButton(
                        icon = if (filter_favorites) Icons.Filled.Star else TablerIcons.Star,
                        content_description = favorites_label,
                        tint = if (filter_favorites) colors.star else colors.text_secondary,
                        onClick = { filter_favorites = !filter_favorites },
                        modifier = Modifier.semantics { contentDescription = favorites_label },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = inbox_card_horizontal_margin, vertical = AsterSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                FilterChip(
                    label = stringResource(R.string.contacts),
                    active = ui_state.tab == ContactsTab.CONTACTS,
                    count = ui_state.contacts.size,
                    on_click = { vm.select_tab(ContactsTab.CONTACTS) },
                )
                FilterChip(
                    label = stringResource(R.string.contact_groups),
                    active = ui_state.tab == ContactsTab.GROUPS,
                    count = ui_state.groups.size,
                    on_click = { vm.select_tab(ContactsTab.GROUPS) },
                )
                FilterChip(
                    label = stringResource(R.string.trash),
                    active = ui_state.tab == ContactsTab.TRASH,
                    count = ui_state.trashed_contacts.size,
                    on_click = { vm.select_tab(ContactsTab.TRASH) },
                )
            }

            if (ui_state.tab == ContactsTab.GROUPS) {
                groups_pane(
                    groups = filtered_groups,
                    query = query.trim(),
                    modifier = Modifier.weight(1f),
                    on_edit = { editing_group = it },
                    on_delete = { pending_group_delete = it },
                )
            } else if (ui_state.tab == ContactsTab.TRASH) {
                trash_pane(
                    contacts = filtered_trash,
                    query = query.trim(),
                    is_busy = ui_state.is_bulk_working,
                    modifier = Modifier.weight(1f),
                    on_restore = { contact ->
                        vm.restore_contact(contact) { ok ->
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.contact_restored),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    on_delete_forever = { pending_forever_delete = it },
                    on_empty_trash = { show_empty_trash_confirm = true },
                )
            } else {
                if (
                    !ui_state.duplicates_dismissed &&
                    ui_state.duplicate_clusters.isNotEmpty() &&
                    !ui_state.is_selecting
                ) {
                    duplicate_banner(
                        count = ui_state.duplicate_count,
                        on_review = { merge_cluster = ui_state.duplicate_clusters.first() },
                        on_dismiss = { vm.dismiss_duplicates() },
                    )
                }

                if (ui_state.is_loading && ui_state.contacts.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent_blue)
                    }
                } else if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(AsterSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        val empty_message = ui_state.error
                            ?: if (query.isBlank()) {
                                stringResource(R.string.no_contacts_yet)
                            } else {
                                stringResource(R.string.no_contacts_match, query.trim())
                            }
                        Text(
                            text = empty_message,
                            color = colors.text_muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        if (!filter_favorites && query.isBlank()) {
                            val favorites = filtered.filter { it.is_favorite }
                            if (favorites.isNotEmpty()) {
                                item(key = "favorites_strip") {
                                    favorites_strip(
                                        favorites = favorites,
                                        on_open_contact = on_open_contact,
                                    )
                                }
                            }
                        }
                        grouped.entries.forEachIndexed { group_index, (letter, group) ->
                            item(key = "letter_$letter", contentType = "letter_header") {
                                section_letter_header(letter = letter, is_first_group = group_index == 0)
                            }
                            itemsIndexed(group, key = { _, c -> c.id }) { index, c ->
                                ContactRow(
                                    contact = c,
                                    is_selected = c.id in ui_state.selected_ids,
                                    is_selecting = ui_state.is_selecting,
                                    is_first = index == 0,
                                    is_last = index == group.size - 1,
                                    on_click = {
                                        if (ui_state.is_selecting) {
                                            vm.toggle_selection(c.id)
                                        } else {
                                            on_open_contact(c.id)
                                        }
                                    },
                                    on_toggle_selection = { vm.toggle_selection(c.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!ui_state.is_selecting && ui_state.tab != ContactsTab.TRASH) {
            val fab_label = stringResource(
                if (ui_state.tab == ContactsTab.GROUPS) R.string.new_group else R.string.new_contact,
            )
            ExtendedFloatingActionButton(
                onClick = {
                    if (ui_state.tab == ContactsTab.GROUPS) show_new_group = true else on_create_contact()
                },
                containerColor = colors.accent_blue,
                contentColor = colors.on_accent,
                shape = SquircleShape(AsterRadius.pill),
                icon = {
                    Icon(
                        imageVector = TablerIcons.Plus,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = fab_label,
                        fontWeight = FontWeight.Medium,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AsterSpacing.lg)
                    .semantics { contentDescription = fab_label }
                    .testTag("fab_add_contact"),
            )
        }
    }

    if (show_sync_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_sync_confirm = false },
            title = stringResource(R.string.sync_contacts),
            message = stringResource(R.string.sync_contacts_description),
            confirm_label = stringResource(R.string.sync),
            cancel_label = stringResource(R.string.cancel),
            on_confirm = {
                show_sync_confirm = false
                permission_launcher.launch(Manifest.permission.READ_CONTACTS)
            },
        )
    }

    if (show_delete_confirm) {
        val count = ui_state.selected_ids.size
        val deleted_message = context.getString(R.string.contact_moved_to_trash)
        AsterAlertDialog(
            on_dismiss = { show_delete_confirm = false },
            title = context.resources.getQuantityString(R.plurals.delete_contacts_title, count, count),
            message = stringResource(R.string.contact_trash_confirm_message),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            is_busy = ui_state.is_bulk_working,
            on_confirm = {
                show_delete_confirm = false
                vm.delete_selection { ok ->
                    if (ok) Toast.makeText(context, deleted_message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (show_empty_trash_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_empty_trash_confirm = false },
            title = stringResource(R.string.empty_trash),
            message = stringResource(R.string.empty_contacts_trash_confirm_message),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            is_busy = ui_state.is_bulk_working,
            on_confirm = {
                show_empty_trash_confirm = false
                vm.empty_trash()
            },
        )
    }

    pending_forever_delete?.let { target ->
        AsterAlertDialog(
            on_dismiss = { pending_forever_delete = null },
            title = stringResource(R.string.swipe_delete_forever),
            message = stringResource(
                R.string.alias_delete_confirm_message,
                target.name.ifBlank { target.email },
            ),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            is_busy = ui_state.is_bulk_working,
            on_confirm = {
                pending_forever_delete = null
                vm.delete_contact_forever(target.id)
            },
        )
    }

    if (show_new_group) {
        new_group_dialog(
            existing_count = ui_state.groups.size,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = { show_new_group = false },
            on_create = { name, color, icon ->
                show_new_group = false
                vm.create_group(name, color, icon) { ok ->
                    if (ok) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_created),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }

    editing_group?.let { group ->
        new_group_dialog(
            existing_count = ui_state.groups.size,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = { editing_group = null },
            on_create = { name, color, icon ->
                editing_group = null
                vm.update_group(group.id, name, color, icon) { ok ->
                    if (ok) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.saved),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            existing = group,
        )
    }

    if (show_group_picker) {
        group_picker_dialog(
            groups = ui_state.groups,
            on_dismiss = { show_group_picker = false },
            on_pick = { group ->
                show_group_picker = false
                vm.add_selection_to_group(group.id) { added ->
                    if (added > 0) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.added_to_group),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            on_new_group = {
                show_group_picker = false
                show_new_group = true
            },
        )
    }

    merge_cluster?.let { cluster ->
        merge_dialog(
            cluster = cluster,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = { merge_cluster = null },
            on_merge = { ordered ->
                merge_cluster = null
                vm.merge_duplicates(ordered) { ok ->
                    if (ok) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.contacts_merged),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }

    pending_group_delete?.let { group ->
        AsterAlertDialog(
            on_dismiss = { pending_group_delete = null },
            title = stringResource(R.string.delete_group),
            message = stringResource(R.string.delete_group_message),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                pending_group_delete = null
                vm.delete_group(group.id)
            },
        )
    }
}

private fun letter_of(name: String): String {
    val t = name.trim()
    if (t.isEmpty()) return "#"
    val first = t.first().uppercaseChar()
    return if (first.isLetter()) first.toString() else "#"
}

private fun parse_group_color(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)

@Composable
private fun group_glyph(
    color: String,
    icon: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val tint = parse_group_color(color, colors.accent_blue)
    val vector = org.astermail.android.ui.common.label_icon_or_null(icon)
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(16.dp),
        )
    } else {
        Box(
            modifier = modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tint),
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    on_click: () -> Unit,
    count: Int? = null,
) {
    val colors = AsterMaterial.colors
    val bg = if (active) {
        colors.accent_blue
    } else if (colors.is_dark) {
        colors.input_bg
    } else {
        colors.bg_secondary
    }
    val fg = if (active) colors.on_accent else colors.text_secondary
    Row(
        modifier = Modifier
            .clip(SquircleShape(AsterRadius.pill))
            .background(bg)
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (count != null) {
            Spacer(Modifier.width(AsterSpacing.xs))
            Text(
                text = count.toString(),
                color = if (active) colors.on_accent.copy(alpha = 0.8f) else colors.text_muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun duplicate_banner(count: Int, on_review: () -> Unit, on_dismiss: () -> Unit) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = inbox_card_horizontal_margin, vertical = AsterSpacing.sm)
            .clip(SquircleShape(AsterRadius.xl))
            .acrylic(colors, SquircleShape(AsterRadius.xl), colors.bg_card)
            .border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.xl))
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Users,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.merge_contacts),
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = context.resources.getQuantityString(R.plurals.duplicate_contacts_found, count, count),
                color = colors.text_secondary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.review_duplicates),
            color = colors.on_accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(SquircleShape(AsterRadius.pill))
                .background(colors.accent_blue)
                .clickable(onClick = on_review)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
                .testTag("review_duplicates"),
        )
        AsterIconButton(
            icon = TablerIcons.X,
            content_description = stringResource(R.string.close),
            icon_size = 16,
            onClick = on_dismiss,
        )
    }
}

@Composable
private fun trash_pane(
    contacts: List<Contact>,
    query: String,
    is_busy: Boolean,
    modifier: Modifier = Modifier,
    on_restore: (Contact) -> Unit,
    on_delete_forever: (Contact) -> Unit,
    on_empty_trash: () -> Unit,
) {
    val colors = AsterMaterial.colors
    if (contacts.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(AsterSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (query.isBlank()) TablerIcons.Trash else TablerIcons.Search,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.no_contacts_in_trash)
                } else {
                    stringResource(R.string.no_contacts_match, query)
                },
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (query.isBlank()) {
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.contacts_in_trash_notice),
                    color = colors.text_muted,
                    fontSize = 13.sp,
                )
            }
        }
        return
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.contacts_in_trash_notice),
                color = colors.text_muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.empty_trash),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .clickable(enabled = !is_busy, onClick = on_empty_trash)
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
            )
        }
        AsterDivider()
        LazyColumn(contentPadding = PaddingValues(top = AsterSpacing.sm, bottom = 88.dp)) {
            itemsIndexed(contacts, key = { _, c -> c.id }) { index, contact ->
                Row(
                    modifier = contact_card_modifier(
                        is_first = index == 0,
                        is_last = index == contacts.size - 1,
                    )
                        .defaultMinSize(minHeight = contact_row_min_height)
                        .padding(
                            horizontal = inbox_card_content_padding,
                            vertical = AsterSpacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(
                        avatar_url = contact.avatar_url,
                        email = contact.email,
                        name = contact.name,
                        profile_color = contact.profile_color,
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name.ifBlank { contact.email },
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.contact_trash_days_left,
                                org.astermail.android.contacts.contact_trash_days_left(contact.deleted_at),
                            ),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AsterIconButton(
                        icon = TablerIcons.ArrowBackUp,
                        content_description = stringResource(R.string.restore),
                        onClick = { on_restore(contact) },
                        enabled = !is_busy,
                        tint = colors.text_muted,
                        icon_size = 20,
                    )
                    AsterIconButton(
                        icon = TablerIcons.Trash,
                        content_description = stringResource(R.string.swipe_delete_forever),
                        onClick = { on_delete_forever(contact) },
                        enabled = !is_busy,
                        tint = colors.danger,
                        icon_size = 20,
                    )
                }
            }
        }
    }
}

@Composable
private fun groups_pane(
    groups: List<ContactGroup>,
    query: String,
    modifier: Modifier = Modifier,
    on_edit: (ContactGroup) -> Unit,
    on_delete: (ContactGroup) -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    if (groups.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(AsterSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (query.isBlank()) TablerIcons.Users else TablerIcons.Search,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.no_groups_yet)
                } else {
                    stringResource(R.string.no_groups_match, query)
                },
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (query.isBlank()) {
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.no_groups_description),
                    color = colors.text_muted,
                    fontSize = 13.sp,
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = AsterSpacing.sm, bottom = 88.dp),
    ) {
        itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
            Row(
                modifier = contact_card_modifier(
                    is_first = index == 0,
                    is_last = index == groups.size - 1,
                )
                    .defaultMinSize(minHeight = contact_row_min_height)
                    .padding(
                        horizontal = inbox_card_content_padding,
                        vertical = AsterSpacing.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(contact_row_avatar_size),
                    contentAlignment = Alignment.Center,
                ) {
                    group_glyph(color = group.color, icon = group.icon)
                }
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = context.resources.getQuantityString(
                            R.plurals.group_members_plural,
                            group.contact_count,
                            group.contact_count,
                        ),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
                AsterIconButton(
                    icon = TablerIcons.Pencil,
                    content_description = stringResource(R.string.edit_group),
                    icon_size = 18,
                    onClick = { on_edit(group) },
                )
                AsterIconButton(
                    icon = TablerIcons.Trash,
                    content_description = stringResource(R.string.delete_group),
                    icon_size = 18,
                    onClick = { on_delete(group) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun new_group_dialog(
    existing_count: Int,
    is_busy: Boolean,
    on_dismiss: () -> Unit,
    on_create: (String, String, String?) -> Unit,
    existing: ContactGroup? = null,
) {
    val colors = AsterMaterial.colors
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var color by remember(existing?.id) {
        mutableStateOf(
            existing?.color?.takeIf { it.isNotBlank() }
                ?: group_colors[existing_count % group_colors.size],
        )
    }
    var icon by remember(existing?.id) { mutableStateOf(existing?.icon) }
    val accent = parse_group_color(color, colors.accent_blue)
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(
            if (existing == null) R.string.new_group else R.string.edit_group,
        ),
        message = stringResource(R.string.no_groups_description),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    group_glyph(color = color, icon = icon)
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = name.trim().ifBlank { stringResource(R.string.group_name) },
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.group_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.color_label),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    group_colors.forEach { hex ->
                        val is_selected = hex.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parse_group_color(hex, colors.accent_blue))
                                .then(
                                    if (is_selected)
                                        Modifier.border(2.dp, colors.text_primary, CircleShape)
                                    else
                                        Modifier.border(1.dp, colors.border_secondary, CircleShape)
                                )
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (is_selected) {
                                Icon(
                                    imageVector = TablerIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.icon_label),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                org.astermail.android.ui.common.label_icon_grid(
                    selected_icon = icon,
                    accent_color = accent,
                    on_select = { icon = it },
                )
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(
                    if (existing == null) R.string.create else R.string.save,
                ),
                enabled = name.isNotBlank(),
                is_loading = is_busy,
                onClick = { on_create(name, color, icon) },
            )
        },
    )
}

@Composable
private fun group_picker_dialog(
    groups: List<ContactGroup>,
    on_dismiss: () -> Unit,
    on_pick: (ContactGroup) -> Unit,
    on_new_group: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.add_to_group),
        message = if (groups.isEmpty()) stringResource(R.string.no_groups_description) else null,
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(AsterRadius.md))
                            .clickable { on_pick(group) }
                            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group_glyph(color = group.color, icon = group.icon)
                        Spacer(Modifier.width(AsterSpacing.md))
                        Text(
                            text = group.name,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = context.resources.getQuantityString(
                                R.plurals.group_members_plural,
                                group.contact_count,
                                group.contact_count,
                            ),
                            color = colors.text_muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.new_group),
                onClick = on_new_group,
            )
        },
    )
}

@Composable
private fun merge_dialog(
    cluster: DuplicateCluster,
    is_busy: Boolean,
    on_dismiss: () -> Unit,
    on_merge: (List<Contact>) -> Unit,
) {
    val colors = AsterMaterial.colors
    var primary_id by remember(cluster.key) { mutableStateOf(cluster.contacts.first().id) }
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.merge_contacts),
        message = stringResource(R.string.merge_contacts_description),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                cluster.contacts.forEachIndexed { index, contact ->
                    val is_primary = contact.id == primary_id
                    if (index > 0) Spacer(Modifier.height(AsterSpacing.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(AsterRadius.lg))
                            .background(
                                if (is_primary) colors.accent_blue.copy(alpha = 0.08f) else colors.bg_secondary,
                            )
                            .border(
                                1.dp,
                                if (is_primary) colors.accent_blue else colors.border_secondary,
                                SquircleShape(AsterRadius.lg),
                            )
                            .clickable { primary_id = contact.id }
                            .padding(AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        merge_radio_dot(selected = is_primary)
                        Spacer(Modifier.width(AsterSpacing.md))
                        ContactAvatar(
                            avatar_url = contact.avatar_url,
                            email = contact.email,
                            name = contact.name,
                            profile_color = contact.profile_color,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(AsterSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.name.ifBlank { contact.email },
                                    color = colors.text_primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (is_primary) {
                                    Spacer(Modifier.width(AsterSpacing.sm))
                                    Text(
                                        text = stringResource(R.string.merge_keep_this),
                                        color = colors.accent_blue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(SquircleShape(AsterRadius.pill))
                                            .background(colors.accent_blue.copy(alpha = 0.14f))
                                            .padding(horizontal = AsterSpacing.sm, vertical = 2.dp),
                                    )
                                }
                            }
                            if (contact.email.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = contact.email,
                                    color = colors.text_secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            val details = listOf(contact.phone, contact.company)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (details.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = details,
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
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.merge),
                is_loading = is_busy,
                onClick = {
                    val primary = cluster.contacts.first { it.id == primary_id }
                    on_merge(listOf(primary) + cluster.contacts.filter { it.id != primary_id })
                },
            )
        },
    )
}

@Composable
private fun merge_radio_dot(selected: Boolean) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent_blue else Color.Transparent)
            .border(1.5.dp, if (selected) colors.accent_blue else colors.text_tertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.on_accent),
            )
        }
    }
}

@Composable
private fun favorites_strip(
    favorites: List<Contact>,
    on_open_contact: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val side_inset = inbox_card_horizontal_margin + inbox_card_content_padding
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tab_favorites),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = side_inset,
                end = AsterSpacing.lg,
                top = AsterSpacing.md,
                bottom = AsterSpacing.xs,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = inbox_card_horizontal_margin, vertical = AsterSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
        ) {
            favorites.forEach { contact ->
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(SquircleShape(AsterRadius.xl))
                        .clickable { on_open_contact(contact.id) }
                        .padding(vertical = AsterSpacing.sm, horizontal = AsterSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ContactAvatar(
                        avatar_url = contact.avatar_url,
                        email = contact.email,
                        name = contact.name,
                        size = 56.dp,
                        profile_color = contact.profile_color,
                    )
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = contact.name.ifBlank { contact.email }.substringBefore(" "),
                        color = colors.text_secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val contact_row_avatar_size = 44.dp
private val contact_row_min_height = 72.dp

@Composable
private fun section_letter_header(letter: String, is_first_group: Boolean) {
    val colors = AsterMaterial.colors
    Text(
        text = letter,
        color = colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin + inbox_card_content_padding,
                end = AsterSpacing.lg,
                top = if (is_first_group) AsterSpacing.sm else AsterSpacing.lg,
                bottom = AsterSpacing.sm,
            ),
    )
}

@Composable
private fun contact_card_modifier(
    is_first: Boolean,
    is_last: Boolean,
    is_selected: Boolean = false,
): Modifier {
    val colors = AsterMaterial.colors
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val surface = if (is_selected) inbox_card_selected_color(colors) else inbox_card_read_color(colors)
    return Modifier
        .fillMaxWidth()
        .padding(
            start = inbox_card_horizontal_margin,
            end = inbox_card_horizontal_margin,
            bottom = if (is_last) 0.dp else inbox_group_split,
        )
        .clip(shape)
        .acrylic_backdrop(colors)
        .drawBehind { drawRect(surface) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    is_selected: Boolean,
    is_selecting: Boolean,
    is_first: Boolean,
    is_last: Boolean,
    on_click: () -> Unit,
    on_toggle_selection: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = contact_card_modifier(is_first, is_last, is_selected)
            .combinedClickable(
                onClick = on_click,
                onLongClick = on_toggle_selection,
            )
            .defaultMinSize(minHeight = contact_row_min_height)
            .padding(
                horizontal = inbox_card_content_padding,
                vertical = AsterSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = on_toggle_selection)
                .semantics { contentDescription = contact.name },
            contentAlignment = Alignment.Center,
        ) {
            if (is_selected) {
                Box(
                    modifier = Modifier
                        .size(contact_row_avatar_size)
                        .clip(CircleShape)
                        .background(colors.accent_blue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = colors.on_accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                ContactAvatar(
                    avatar_url = contact.avatar_url,
                    email = contact.email,
                    name = contact.name,
                    size = contact_row_avatar_size,
                    profile_color = contact.profile_color,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name.ifBlank { contact.email },
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (contact.is_favorite) {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.tab_favorites),
                        tint = colors.star,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            val subtitle = listOf(contact.company, contact.email)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun bulk_progress_banner(done: Int, total: Int) {
    val colors = AsterMaterial.colors
    val safe_total = total.coerceAtLeast(1)
    val target = (done.toFloat() / safe_total).coerceIn(0f, 1f)
    val progress by animateFloatAsState(targetValue = target, label = "bulk_progress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.contacts_bulk_progress, done.coerceAtMost(total), total),
            color = colors.text_secondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(AsterSpacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.accent_blue.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.accent_blue),
            )
        }
    }
}
