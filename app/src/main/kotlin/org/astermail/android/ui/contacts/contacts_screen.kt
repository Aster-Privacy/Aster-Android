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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_dropdown_divider
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.design.mirror_in_rtl
import org.astermail.android.ui.mail.SenderAvatar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star

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
    var show_delete_confirm by remember { mutableStateOf(false) }
    var show_new_group by remember { mutableStateOf(false) }
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
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    AsterIconButton(
                        icon = TablerIcons.SquareCheck,
                        content_description = stringResource(R.string.select_all),
                        onClick = { vm.set_selection(filtered.map { it.id }.toSet()) },
                    )
                    Box {
                        AsterIconButton(
                            icon = TablerIcons.DotsVertical,
                            content_description = stringResource(R.string.more_options),
                            onClick = { show_bulk_menu = true },
                            modifier = Modifier.testTag("contacts_bulk_menu"),
                        )
                        aster_dropdown_menu(
                            expanded = show_bulk_menu,
                            on_dismiss = { show_bulk_menu = false },
                        ) {
                            aster_dropdown_item(
                                label = stringResource(R.string.compose),
                                icon = TablerIcons.Mail,
                                test_tag = "bulk_compose",
                                on_click = {
                                    show_bulk_menu = false
                                    on_compose_selection()
                                },
                            )
                            aster_dropdown_item(
                                label = stringResource(R.string.add_to_group),
                                icon = TablerIcons.Users,
                                test_tag = "bulk_add_to_group",
                                on_click = {
                                    show_bulk_menu = false
                                    vm.load_groups()
                                    show_group_picker = true
                                },
                            )
                            aster_dropdown_divider()
                            aster_dropdown_item(
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
                    val sync_label = stringResource(
                        if (ui_state.is_syncing) R.string.syncing else R.string.sync_contacts,
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { show_sync_confirm = true }
                            .semantics { contentDescription = sync_label },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = ui_state.is_syncing,
                            transitionSpec = {
                                fadeIn(tween(org.astermail.android.design.AsterDuration.short_4)) togetherWith
                                    fadeOut(tween(org.astermail.android.design.AsterDuration.menu_exit))
                            },
                            label = "sync_icon",
                        ) { syncing ->
                            if (syncing) {
                                CircularProgressIndicator(
                                    color = colors.accent_blue,
                                    strokeWidth = 2.dp,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = TablerIcons.Refresh,
                                    contentDescription = stringResource(R.string.sync_contacts),
                                    tint = colors.text_secondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
            AsterDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
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
            }

            if (ui_state.tab == ContactsTab.GROUPS) {
                groups_pane(
                    groups = ui_state.groups,
                    modifier = Modifier.weight(1f),
                    on_delete = { pending_group_delete = it },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs),
                ) {
                    org.astermail.android.ui.common.list_search_bar(
                        query = query,
                        on_query_change = { query = it },
                        placeholder = stringResource(R.string.search_contacts),
                        test_tag = "contact_search_bar",
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        label = stringResource(R.string.tab_all),
                        active = !filter_favorites,
                        on_click = { filter_favorites = false },
                    )
                    FilterChip(
                        label = stringResource(R.string.tab_favorites),
                        active = filter_favorites,
                        on_click = { filter_favorites = true },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = context.resources.getQuantityString(
                            R.plurals.contacts_count_plural,
                            filtered.size,
                            filtered.size,
                        ),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                    )
                }

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
                AsterDivider()

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
                        Text(
                            text = ui_state.error ?: stringResource(R.string.no_contacts_found),
                            color = colors.text_muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        grouped.forEach { (letter, group) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.bg_secondary)
                                        .padding(
                                            horizontal = AsterSpacing.lg,
                                            vertical = 4.dp,
                                        ),
                                ) {
                                    Text(
                                        text = letter,
                                        color = colors.text_tertiary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            itemsIndexed(group, key = { _, c -> c.id }) { i, c ->
                                ContactRow(
                                    contact = c,
                                    is_selected = c.id in ui_state.selected_ids,
                                    is_selecting = ui_state.is_selecting,
                                    on_click = {
                                        if (ui_state.is_selecting) {
                                            vm.toggle_selection(c.id)
                                        } else {
                                            on_open_contact(c.id)
                                        }
                                    },
                                    on_toggle_selection = { vm.toggle_selection(c.id) },
                                )
                                if (i < group.size - 1) AsterDivider()
                            }
                        }
                    }
                }
            }
        }

        if (!ui_state.is_selecting) {
            FloatingActionButton(
                onClick = {
                    if (ui_state.tab == ContactsTab.GROUPS) show_new_group = true else on_create_contact()
                },
                containerColor = colors.accent_blue,
                contentColor = Color.White,
                shape = SquircleShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AsterSpacing.lg)
                    .testTag("fab_add_contact"),
            ) {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = stringResource(
                        if (ui_state.tab == ContactsTab.GROUPS) R.string.new_group else R.string.new_contact,
                    ),
                )
            }
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
        val deleted_message = context.resources.getQuantityString(
            R.plurals.contacts_deleted_plural,
            count,
            count,
        )
        AsterAlertDialog(
            on_dismiss = { show_delete_confirm = false },
            title = context.resources.getQuantityString(R.plurals.delete_contacts_title, count, count),
            message = stringResource(R.string.delete_contacts_message),
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
    val fg = if (active) Color.White else colors.text_secondary
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
                color = if (active) Color.White.copy(alpha = 0.8f) else colors.text_muted,
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
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs)
            .clip(SquircleShape(AsterRadius.md))
            .background(colors.accent_blue.copy(alpha = 0.10f))
            .border(1.dp, colors.accent_blue.copy(alpha = 0.28f), SquircleShape(AsterRadius.md))
            .padding(start = AsterSpacing.md, end = AsterSpacing.xs, top = AsterSpacing.sm, bottom = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Copy,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = context.resources.getQuantityString(R.plurals.duplicate_contacts_found, count, count),
            color = colors.text_primary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.review_duplicates),
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(SquircleShape(AsterRadius.sm))
                .clickable(onClick = on_review)
                .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs)
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
private fun groups_pane(
    groups: List<ContactGroup>,
    modifier: Modifier = Modifier,
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
                imageVector = TablerIcons.Users,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = stringResource(R.string.no_groups_yet),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = stringResource(R.string.no_groups_description),
                color = colors.text_muted,
                fontSize = 13.sp,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(groups, key = { it.id }) { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                group_glyph(color = group.color, icon = group.icon)
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
                    icon = TablerIcons.Trash,
                    content_description = stringResource(R.string.delete_group),
                    icon_size = 18,
                    onClick = { on_delete(group) },
                )
            }
            AsterDivider()
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
) {
    val colors = AsterMaterial.colors
    var name by remember { mutableStateOf("") }
    var color by remember {
        mutableStateOf(group_colors[existing_count % group_colors.size])
    }
    var icon by remember { mutableStateOf<String?>(null) }
    val accent = parse_group_color(color, colors.accent_blue)
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.new_group),
        message = stringResource(R.string.no_groups_description),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
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
                label = stringResource(R.string.create),
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
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                cluster.contacts.forEach { contact ->
                    val is_primary = contact.id == primary_id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AsterSpacing.xs)
                            .clip(SquircleShape(AsterRadius.md))
                            .background(
                                if (is_primary) colors.accent_blue.copy(alpha = 0.10f) else Color.Transparent,
                            )
                            .border(
                                1.dp,
                                if (is_primary) colors.accent_blue else colors.border_primary,
                                SquircleShape(AsterRadius.md),
                            )
                            .clickable { primary_id = contact.id }
                            .padding(AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SenderAvatar(email = contact.email, name = contact.name)
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
                            val subtitle = listOf(contact.email, contact.phone, contact.company)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    color = colors.text_muted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (is_primary) {
                                Text(
                                    text = stringResource(R.string.merge_keep_this),
                                    color = colors.accent_blue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (is_primary) {
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(18.dp),
                            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    is_selected: Boolean,
    is_selecting: Boolean,
    on_click: () -> Unit,
    on_toggle_selection: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (is_selected) colors.accent_blue.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(
                onClick = on_click,
                onLongClick = on_toggle_selection,
            )
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.accent_blue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                SenderAvatar(email = contact.email, name = contact.name)
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (contact.is_favorite) {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = colors.star,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            val subtitle = listOf(contact.company, contact.email)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        if (!is_selecting) {
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp).mirror_in_rtl(),
            )
        }
    }
}
