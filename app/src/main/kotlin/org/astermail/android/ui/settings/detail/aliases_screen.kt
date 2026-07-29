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

package org.astermail.android.ui.settings.detail

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.ui.common.show_copied_toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.R
import org.astermail.android.api.settings.AliasDirectory
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.api.settings.UpdateAliasPreferencesRequest
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.settings.DecryptedDeletedAlias
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.auth.TurnstileWidget
import org.astermail.android.util.generate_random_local_part

@Composable
private fun tab_labels_computed(): List<String> = listOf(
    stringResource(R.string.aliases),
    stringResource(R.string.custom_domains),
    stringResource(R.string.aliases_tab_directories),
    stringResource(R.string.ghost_aliases),
    stringResource(R.string.aliases_tab_preferences),
)

@Composable
fun AliasesScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    open_create: Boolean = false,
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tab_labels = tab_labels_computed()
    val catch_all_locked = plan_vm.is_feature_locked("has_catch_all") && !plan_state.is_loading
    val alias_directories_locked = plan_vm.is_feature_locked("max_alias_directories") && !plan_state.is_loading
    val alias_restore_locked = plan_vm.is_feature_locked("has_advanced_aliases") && !plan_state.is_loading
    val alias_export_locked = plan_vm.is_feature_locked("has_advanced_aliases") && !plan_state.is_loading

    var selected_tab by remember { mutableStateOf(0) }
    var pending_delete_alias by remember { mutableStateOf<Pair<String, String>?>(null) }
    var show_create_alias by remember { mutableStateOf(false) }
    var show_add_domain by remember { mutableStateOf(false) }
    var expanded_domain_id by remember { mutableStateOf<String?>(null) }
    var domain_dns by remember { mutableStateOf<Map<String, List<DnsRecord>>>(emptyMap()) }
    var verifying_domain_id by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) {
        vm.load_aliases()
        vm.load_domains()
        vm.load_custom_domain_addresses()
        vm.load_deleted_aliases()
        vm.load_labels(folder_type = "folder")
    }

    LaunchedEffect(open_create) {
        if (open_create) show_create_alias = true
    }

    LaunchedEffect(selected_tab) {
        when (selected_tab) {
            2 -> vm.load_directories()
            3 -> vm.load_ghost_aliases()
            4 -> vm.load_alias_preferences()
        }
    }

    detail_scaffold(title = stringResource(R.string.aliases), on_back = on_back, scrollable = false) {
        ScrollableTabRow(
            selectedTabIndex = selected_tab,
            containerColor = colors.bg_primary,
            contentColor = colors.accent_blue,
            edgePadding = AsterSpacing.lg,
        ) {
            tab_labels.forEachIndexed { i, label ->
                Tab(
                    selected = selected_tab == i,
                    onClick = { selected_tab = i },
                    text = {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (selected_tab == i) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    selectedContentColor = colors.accent_blue,
                    unselectedContentColor = colors.text_muted,
                )
            }
        }
        v_gap(AsterSpacing.sm)

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when (selected_tab) {
                0 -> aliases_tab(
                    vm = vm,
                    state = state,
                    context = context,
                    scope = scope,
                    on_show_create = { show_create_alias = true },
                    restore_locked = alias_restore_locked,
                    export_locked = alias_export_locked,
                    on_upgrade = { on_open("billing") },
                )
                1 -> tab_scroll {
                    domains_tab(
                        vm = vm,
                        state = state,
                        scope = scope,
                        expanded_domain_id = expanded_domain_id,
                        domain_dns = domain_dns,
                        verifying_domain_id = verifying_domain_id,
                        on_expanded_change = { expanded_domain_id = it },
                        on_dns_loaded = { id, records -> domain_dns = domain_dns + (id to records) },
                        on_verifying_change = { verifying_domain_id = it },
                        on_show_add = { show_add_domain = true },
                        catch_all_locked = catch_all_locked,
                    )
                }
                2 -> tab_scroll {
                    directories_tab(
                        vm = vm,
                        state = state,
                        scope = scope,
                        locked = alias_directories_locked,
                        on_upgrade = { on_open("billing") },
                    )
                }
                3 -> {
                    val ghost_scroll = rememberScrollState()
                    tab_scroll(scroll_state = ghost_scroll) {
                        ghost_tab(
                            vm = vm,
                            state = state,
                            context = context,
                            scope = scope,
                            scroll_state = ghost_scroll,
                        )
                    }
                }
                4 -> tab_scroll { preferences_tab(vm = vm, state = state) }
            }
        }
    }

    if (show_create_alias) {
        create_alias_dialog(
            on_dismiss = { show_create_alias = false },
            on_create = { local_part, domain, token, display_name ->
                show_create_alias = false
                scope.launch {
                    val domain_id = domain.domain_id
                    if (domain_id == null) {
                        vm.create_alias_now(local_part, domain.domain_name, token, display_name)
                    } else {
                        vm.create_domain_address_now(local_part, domain_id, domain.domain_name, token, display_name)
                    }
                }
            },
            vm = vm,
        )
    }

    if (show_add_domain) {
        add_domain_dialog(
            on_dismiss = { show_add_domain = false },
            on_add = { domain_name, token ->
                show_add_domain = false
                vm.add_domain_now(domain_name, token) {}
            },
        )
    }

    pending_delete_alias?.let { (id, address) ->
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { pending_delete_alias = null },
            title = stringResource(R.string.delete_alias),
            message = stringResource(R.string.alias_delete_confirm_message, address),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { pending_delete_alias = null },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.delete),
                    onClick = { vm.delete_alias(id); pending_delete_alias = null },
                )
            },
        )
    }
}

@Composable
private fun tab_scroll(
    scroll_state: androidx.compose.foundation.ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll_state)
            .padding(horizontal = AsterSpacing.lg)
            .padding(bottom = AsterSpacing.lg),
        content = content,
    )
}

internal fun list_item_shape(idx: Int, last_index: Int): RoundedCornerShape = when {
    last_index == 0 -> RoundedCornerShape(14.dp)
    idx == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    idx == last_index -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    else -> RoundedCornerShape(0.dp)
}

@Composable
private fun aliases_tab(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    on_show_create: () -> Unit,
    restore_locked: Boolean = false,
    export_locked: Boolean = false,
    on_upgrade: () -> Unit = {},
) {
    var pending_delete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pending_domain_address_delete by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var alias_query by remember { mutableStateOf("") }
    var show_export by remember { mutableStateOf(false) }
    var note_editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    val alias_load_settled = remember_load_settled(state.is_loading)
    val always_expand_aliases = state.alias_preferences?.alias_always_expand == true
    val colors = AsterMaterial.colors
    val query = alias_query.trim()
    val visible_aliases = remember(state.aliases, query) {
        if (query.isBlank()) state.aliases
        else state.aliases.filter {
            it.address.contains(query, ignoreCase = true) ||
                (it.encrypted_display_name ?: "").contains(query, ignoreCase = true) ||
                (it.encrypted_note ?: "").contains(query, ignoreCase = true)
        }
    }
    val visible_domain_addresses = remember(state.custom_domain_addresses, query) {
        if (query.isBlank()) state.custom_domain_addresses
        else state.custom_domain_addresses.filter { it.address.contains(query, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AsterSpacing.lg,
                end = AsterSpacing.lg,
                top = AsterSpacing.sm,
                bottom = AsterSpacing.lg,
            ),
        ) {
            item(key = "alias_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.aliases_count, state.aliases.size),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (export_locked) on_upgrade() else show_export = true
                            },
                        ) {
                            Text(
                                stringResource(R.string.alias_export_csv),
                                color = if (export_locked) colors.text_muted else colors.accent_blue,
                                fontSize = 14.sp,
                            )
                        }
                        TextButton(onClick = on_show_create) {
                            Text(stringResource(R.string.create), color = colors.accent_blue, fontSize = 14.sp)
                        }
                    }
                }
                v_gap(AsterSpacing.sm)
            }

            if (state.aliases.isNotEmpty() || state.custom_domain_addresses.isNotEmpty()) {
                item(key = "alias_search") {
                    AsterTextField(
                        value = alias_query,
                        onValueChange = { alias_query = it },
                        placeholder = stringResource(R.string.search_aliases),
                        leading_icon = {
                            Icon(
                                imageVector = TablerIcons.Search,
                                contentDescription = null,
                                tint = colors.text_muted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailing_icon = if (alias_query.isEmpty()) {
                            null
                        } else {
                            {
                                Icon(
                                    imageVector = TablerIcons.X,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = colors.text_muted,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { alias_query = "" },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    v_gap(AsterSpacing.sm)
                }
            }

            if (state.aliases.isEmpty() && (state.is_loading || !alias_load_settled)) {
                item(key = "alias_loading") {
                    skeleton_card_list(rows = 5, leading_circle = true, trailing_width = 44.dp)
                }
            } else if (state.aliases.isEmpty()) {
                item(key = "alias_empty") {
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        detail_row(
                            title = stringResource(R.string.no_aliases),
                            subtitle = state.error ?: stringResource(R.string.no_aliases_subtitle),
                        )
                    }
                }
            } else if (visible_aliases.isEmpty() && visible_domain_addresses.isEmpty()) {
                item(key = "alias_no_results") {
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        detail_row(title = stringResource(R.string.no_results_found))
                    }
                }
            } else {
                itemsIndexed(
                    items = visible_aliases,
                    key = { _, alias -> "alias_${alias.id}" },
                ) { idx, alias ->
                    val expanded = state.expanded_alias_ids.contains(alias.id)
                    LaunchedEffect(alias.id, always_expand_aliases) {
                        if (always_expand_aliases) vm.set_alias_expanded(alias.id, true)
                    }
                    alias_list_row(
                        alias = alias,
                        idx = idx,
                        last_index = visible_aliases.lastIndex,
                        context = context,
                        on_toggle = { vm.toggle_alias(alias.id) },
                        delivery_folder_name = alias_delivery_folder_name(alias, state.labels),
                        on_delete = { pending_delete = alias.id to alias.address },
                        on_edit_note = { note_editing = alias.id to (alias.encrypted_note ?: "") },
                        expanded = expanded,
                        on_toggle_expanded = { vm.toggle_alias_expanded(alias.id) },
                        panel_content = {
                            alias_detail_panel(
                                alias = alias,
                                detail = state.alias_details[alias.id] ?: AliasDetailState(),
                                vm = vm,
                            )
                        },
                    )
                }
            }

            if (visible_domain_addresses.isNotEmpty()) {
                item(key = "custom_domain_header") {
                    v_gap(AsterSpacing.md)
                    Text(
                        text = stringResource(R.string.custom_domains),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    v_gap(AsterSpacing.sm)
                }
                itemsIndexed(
                    items = visible_domain_addresses,
                    key = { _, addr -> "cda_${addr.address}" },
                ) { idx, addr ->
                    custom_domain_address_row(
                        addr = addr,
                        idx = idx,
                        last_index = visible_domain_addresses.lastIndex,
                        context = context,
                        on_toggle = { vm.toggle_domain_address(addr.id, addr.domain_name) },
                        on_delete = { pending_domain_address_delete = Triple(addr.id, addr.domain_name, addr.address) },
                    )
                }
            }

            item(key = "recently_deleted") {
                recently_deleted_section(
                    vm = vm,
                    state = state,
                    restore_locked = restore_locked,
                    on_upgrade = on_upgrade,
                )
            }
        }

        if (show_export && !export_locked) {
            alias_export_dialog(
                state = state,
                on_dismiss = { show_export = false },
                on_load_directories = { vm.load_directories() },
                on_load_ghost_aliases = { vm.load_ghost_aliases() },
            )
        }

        pending_delete?.let { (id, address) ->
            org.astermail.android.design.components.AsterDialog(
                on_dismiss = { pending_delete = null },
                title = stringResource(R.string.delete_alias),
                message = stringResource(R.string.alias_delete_confirm_message, address),
                footer = {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.cancel),
                        onClick = { pending_delete = null },
                    )
                    org.astermail.android.design.components.AsterDialogDestructiveButton(
                        label = stringResource(R.string.delete),
                        onClick = { vm.delete_alias(id); pending_delete = null },
                    )
                },
            )
        }

        pending_domain_address_delete?.let { (address_id, domain_name, address) ->
            org.astermail.android.design.components.AsterDialog(
                on_dismiss = { pending_domain_address_delete = null },
                title = stringResource(R.string.delete_alias),
                message = stringResource(R.string.alias_delete_confirm_message, address),
                footer = {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.cancel),
                        onClick = { pending_domain_address_delete = null },
                    )
                    org.astermail.android.design.components.AsterDialogDestructiveButton(
                        label = stringResource(R.string.delete),
                        onClick = { vm.delete_domain_address(address_id, domain_name); pending_domain_address_delete = null },
                    )
                },
            )
        }

        note_editing?.let { (alias_id, initial_note) ->
            var note_value by remember(alias_id) { mutableStateOf(initial_note) }
            org.astermail.android.design.components.AsterDialog(
                on_dismiss = { note_editing = null },
                title = stringResource(R.string.alias_note_title),
                message = stringResource(R.string.alias_note_hint),
                body = {
                    org.astermail.android.design.components.AsterTextField(
                        value = note_value,
                        onValueChange = { if (it.length <= 500 || it.length < note_value.length) note_value = it },
                        placeholder = stringResource(R.string.alias_note_placeholder),
                        singleLine = false,
                        min_lines = 3,
                        max_lines = 5,
                        min_height = 96.dp,
                    )
                },
                footer = {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.cancel),
                        onClick = { note_editing = null },
                    )
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.save),
                        onClick = {
                            vm.update_alias_note(alias_id, note_value)
                            note_editing = null
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun alias_toggle_chip(label: String, active: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = if (active) colors.accent_blue else colors.text_muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) colors.accent_blue.copy(alpha = 0.14f) else colors.bg_secondary)
            .border(
                1.dp,
                if (active) colors.accent_blue.copy(alpha = 0.5f) else colors.border_secondary,
                CircleShape,
            )
            .clickable(onClick = on_click)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun copy_address(context: Context, address: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("alias", address))
    show_copied_toast(context, address)
}

@Composable
internal fun alias_delivery_folder_name(
    alias: org.astermail.android.api.settings.AliasInfo,
    labels: List<org.astermail.android.api.labels.LabelItem>,
): String? {
    val archive_name = stringResource(R.string.folder_archive)
    val token = alias.delivery_folder_token
    if (token != null) {
        return labels.firstOrNull {
            it.label_token == token && !it.encrypted_name.isNullOrBlank()
        }?.encrypted_name
    }
    return if (alias.never_inbox) archive_name else null
}

@Composable
internal fun alias_list_row(
    alias: org.astermail.android.api.settings.AliasInfo,
    idx: Int,
    last_index: Int,
    context: Context,
    on_toggle: () -> Unit,
    on_delete: () -> Unit,
    on_edit_note: (() -> Unit)? = null,
    delivery_folder_name: String? = null,
    expanded: Boolean = false,
    on_toggle_expanded: (() -> Unit)? = null,
    panel_content: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val shape = list_item_shape(idx, last_index)
    val note_val = alias.encrypted_note
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bg_card)
            .border(1.dp, colors.border_secondary, shape)
            .combinedClickable(
                onClick = {
                    if (on_toggle_expanded != null) on_toggle_expanded() else on_edit_note?.invoke()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    copy_address(context, alias.address)
                },
            )
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alias.address,
                    color = if (alias.decryption_failed) colors.text_muted else colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val display_name_val = alias.encrypted_display_name
                if (!display_name_val.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = display_name_val,
                        color = colors.text_secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    Text(
                        text = when {
                            !alias.is_enabled -> stringResource(R.string.alias_status_disabled_badge)
                            delivery_folder_name != null ->
                                stringResource(R.string.forwards_to_folder, delivery_folder_name)
                            else -> stringResource(R.string.forwards_to_inbox)
                        },
                        color = if (alias.is_enabled) colors.text_tertiary else colors.danger,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    if (on_edit_note != null) {
                        Text(text = "·", color = colors.text_muted, fontSize = 11.sp)
                        Text(
                            text = if (note_val.isNullOrBlank()) {
                                stringResource(R.string.alias_note_add)
                            } else {
                                note_val
                            },
                            color = if (note_val.isNullOrBlank()) colors.text_muted else colors.text_tertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            AsterSwitch(
                checked = alias.is_enabled,
                onCheckedChange = { on_toggle() },
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            AsterIconButton(
                icon = TablerIcons.Trash,
                content_description = stringResource(R.string.delete),
                onClick = on_delete,
                tint = colors.danger,
            )
            if (on_toggle_expanded != null) {
                AsterIconButton(
                    icon = if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                    content_description = if (expanded) {
                        stringResource(R.string.alias_collapse_settings)
                    } else {
                        stringResource(R.string.alias_expand_settings)
                    },
                    onClick = on_toggle_expanded,
                    modifier = Modifier.testTag("alias_expand_${alias.id}"),
                )
            }
        }
        if (expanded && panel_content != null) {
            panel_content()
        }
    }
}

@Composable
private fun custom_domain_address_row(
    addr: org.astermail.android.api.settings.CustomDomainAddressInfo,
    idx: Int,
    last_index: Int,
    context: Context,
    on_toggle: () -> Unit,
    on_delete: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val shape = list_item_shape(idx, last_index)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bg_card)
            .border(1.dp, colors.border_secondary, shape)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    copy_address(context, addr.address)
                },
            )
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = addr.address,
                color = if (addr.decryption_failed) colors.text_muted else colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (addr.is_enabled) {
                    stringResource(R.string.forwards_to_inbox)
                } else {
                    stringResource(R.string.alias_status_disabled_badge)
                },
                color = if (addr.is_enabled) colors.text_tertiary else colors.danger,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        AsterSwitch(
            checked = addr.is_enabled,
            onCheckedChange = { on_toggle() },
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        AsterIconButton(
            icon = TablerIcons.Trash,
            content_description = stringResource(R.string.delete),
            onClick = on_delete,
            tint = colors.danger,
        )
    }
}

@Composable
private fun recently_deleted_section(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
    restore_locked: Boolean,
    on_upgrade: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val deleted = state.deleted_aliases
    if (deleted.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    var pending_purge by remember { mutableStateOf<DecryptedDeletedAlias?>(null) }
    var confirm_empty by remember { mutableStateOf(false) }

    v_gap(AsterSpacing.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Trash,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.recently_deleted_aliases_title),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "(${deleted.size})",
            color = colors.text_muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(20.dp),
        )
    }

    if (expanded) {
        v_gap(AsterSpacing.xs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recently_deleted_aliases_description),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (!restore_locked) {
                TextButton(onClick = { confirm_empty = true }) {
                    Text(
                        text = stringResource(R.string.empty_trash),
                        color = colors.danger,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        v_gap(AsterSpacing.sm)
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            deleted.forEachIndexed { idx, alias ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alias.address,
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.alias_deleted_at, format_deleted_date(alias.deleted_at)),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                        )
                    }
                    if (restore_locked) {
                        AsterGhostButton(
                            label = stringResource(R.string.upgrade),
                            onClick = on_upgrade,
                        )
                    } else {
                        AsterGhostButton(
                            label = stringResource(R.string.alias_restore),
                            onClick = { vm.restore_deleted_alias(alias.id) },
                        )
                        AsterIconButton(
                            icon = TablerIcons.Trash,
                            content_description = stringResource(R.string.alias_delete_permanently),
                            onClick = { pending_purge = alias },
                            tint = colors.danger,
                        )
                    }
                }
                if (idx < deleted.lastIndex) AsterDivider(modifier = Modifier)
            }
        }
    }

    pending_purge?.let { alias ->
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { pending_purge = null },
            title = stringResource(R.string.purge_alias_confirm_title),
            message = stringResource(R.string.purge_alias_confirm_message, alias.address),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { pending_purge = null },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.alias_delete_permanently),
                    onClick = { vm.purge_deleted_alias(alias.id); pending_purge = null },
                )
            },
        )
    }

    if (confirm_empty) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { confirm_empty = false },
            title = stringResource(R.string.empty_trash_confirm_title),
            message = stringResource(R.string.empty_trash_confirm_message),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirm_empty = false },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.empty_trash),
                    onClick = { vm.empty_deleted_aliases(); confirm_empty = false },
                )
            },
        )
    }
}

private fun format_deleted_date(iso: String): String {
    return try {
        val date_part = iso.substringBefore('T')
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date_part)
        if (parsed != null) {
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(parsed)
        } else {
            date_part
        }
    } catch (_: Throwable) {
        iso
    }
}

@Composable
private fun domains_tab(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    expanded_domain_id: String?,
    domain_dns: Map<String, List<DnsRecord>>,
    verifying_domain_id: String?,
    on_expanded_change: (String?) -> Unit,
    on_dns_loaded: (String, List<DnsRecord>) -> Unit,
    on_verifying_change: (String?) -> Unit,
    on_show_add: () -> Unit,
    catch_all_locked: Boolean = false,
) {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.alias_domains_count, state.domains.size),
            color = colors.text_tertiary,
            fontSize = 13.sp,
        )
        TextButton(onClick = on_show_add) {
            Text(stringResource(R.string.alias_action_add), color = colors.accent_blue, fontSize = 14.sp)
        }
    }
    v_gap(AsterSpacing.sm)

    val domains_settled = remember_load_settled(state.domains_loading)
    if (state.domains.isEmpty() && (state.domains_loading || !domains_settled)) {
        skeleton_card_list(rows = 2, leading_circle = true)
    } else if (state.domains.isEmpty()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.no_custom_domains),
                subtitle = stringResource(R.string.no_custom_domains_subtitle),
            )
        }
    } else {
        state.domains.forEach { domain ->
            domain_card(
                domain = domain,
                is_expanded = expanded_domain_id == domain.id,
                dns_records = domain_dns[domain.id] ?: emptyList(),
                is_verifying = verifying_domain_id == domain.id,
                on_expand = {
                    if (expanded_domain_id == domain.id) {
                        on_expanded_change(null)
                    } else {
                        on_expanded_change(domain.id)
                        if (!domain_dns.containsKey(domain.id)) {
                            scope.launch {
                                try {
                                    val records = vm.get_dns_records_now(domain.id)
                                    on_dns_loaded(domain.id, records)
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                },
                on_toggle_catch_all = { vm.toggle_domain_catch_all(domain.id) },
                catch_all_locked = catch_all_locked,
                on_verify = {
                    on_verifying_change(domain.id)
                    scope.launch {
                        try {
                            vm.trigger_domain_verification_now(domain.id)
                        } finally {
                            on_verifying_change(null)
                        }
                    }
                },
                on_delete = { vm.delete_domain(domain.id) },
            )
            v_gap(AsterSpacing.md)
        }
    }
}

@Composable
private fun directories_tab(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    locked: Boolean = false,
    on_upgrade: () -> Unit = {},
) {
    if (locked) {
        UpgradeGate(
            title = stringResource(R.string.alias_directories),
            description = stringResource(R.string.alias_directories_description),
            plan_name = "Nova",
            on_upgrade = on_upgrade,
            requires_label = stringResource(R.string.requires_plan, "Nova"),
            button_label = stringResource(R.string.upgrade),
        )
        return
    }
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var dir_key by remember { mutableStateOf("") }
    var dir_separator by remember { mutableStateOf(".") }
    var dir_domain by remember { mutableStateOf("astermail.org") }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableStateOf(0) }
    var separator_menu_open by remember { mutableStateOf(false) }
    var is_creating by remember { mutableStateOf(false) }
    var dir_availability by remember { mutableStateOf<Boolean?>(null) }
    var dir_checking by remember { mutableStateOf(false) }
    val separators = listOf(".", "+", "#")
    val key_valid = dir_key.matches(Regex("[a-z0-9-]{2,}"))

    LaunchedEffect(dir_key, dir_domain) {
        if (!key_valid) {
            dir_availability = null
            dir_checking = false
            return@LaunchedEffect
        }
        dir_checking = true
        dir_availability = null
        delay(500)
        dir_availability = vm.check_directory_availability(dir_key, dir_domain)
        dir_checking = false
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        AsterTextField(
            value = dir_key,
            onValueChange = { dir_key = it.trim().lowercase().filter { c -> c.isLetterOrDigit() || c == '-' } },
            label = stringResource(R.string.alias_directory_key_label),
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(
                modifier = Modifier
                    .height(52.dp)
                    .clip(SquircleShape(18.dp))
                    .background(colors.input_bg, SquircleShape(18.dp))
                    .border(1.5.dp, colors.input_border, SquircleShape(18.dp))
                    .clickable { separator_menu_open = true }
                    .padding(horizontal = AsterSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(dir_separator, color = colors.text_primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.text_tertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            aster_dropdown_menu(
                expanded = separator_menu_open,
                on_dismiss = { separator_menu_open = false },
                min_width = 96.dp,
            ) {
                separators.forEach { sep ->
                    aster_dropdown_item(
                        label = sep,
                        selected = sep == dir_separator,
                        on_click = { dir_separator = sep; separator_menu_open = false },
                    )
                }
            }
        }
    }
    v_gap(AsterSpacing.sm)

    if (dir_key.isNotBlank()) {
        Text(
            text = stringResource(R.string.alias_directory_example, dir_separator, dir_key),
            color = colors.text_tertiary,
            fontSize = 12.sp,
        )
        when {
            dir_checking -> {
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.checking_availability),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
            dir_availability == true -> {
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.alias_directory_available),
                    color = colors.success,
                    fontSize = 12.sp,
                )
            }
            dir_availability == false -> {
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.alias_directory_not_available),
                    color = colors.danger,
                    fontSize = 12.sp,
                )
            }
        }
        v_gap(AsterSpacing.sm)
        TurnstileWidget(
            on_token = { token -> captcha_token = token },
            on_error = { captcha_token = null; captcha_reset++ },
            on_expired = { captcha_token = null; captcha_reset++ },
            reset_trigger = captcha_reset,
            modifier = Modifier.height(65.dp).fillMaxWidth(),
        )
        v_gap(AsterSpacing.sm)
    }

    AsterButton(
        label = if (is_creating) stringResource(R.string.alias_creating) else stringResource(R.string.create_directory),
        enabled = key_valid && captcha_token != null && !is_creating && dir_availability != false,
        onClick = {
            is_creating = true
            scope.launch {
                val ok = vm.create_directory_now(dir_key, dir_domain, captcha_token)
                is_creating = false
                if (ok) {
                    dir_key = ""
                    captcha_token = null
                    captcha_reset++
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.lg)

    val directories_settled = remember_load_settled(state.directories_loading)
    if (state.directories.isEmpty() && (state.directories_loading || !directories_settled)) {
        skeleton_card_list(rows = 3)
    } else if (state.directories.isEmpty()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.no_directories_yet),
                subtitle = stringResource(R.string.no_directories_subtitle),
            )
        }
    } else {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            state.directories.forEachIndexed { idx, dir ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = dir.decrypted_label.ifBlank { "…" }
                    val dir_address = "anything.${label}@${dir.domain}"
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("directory", dir_address))
                                    show_copied_toast(context, dir_address)
                                },
                            ),
                    ) {
                        Text(
                            text = dir_address,
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.alias_separator_hint),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                        )
                    }
                    AsterSwitch(
                        checked = dir.auto_create_enabled,
                        onCheckedChange = { vm.toggle_directory_auto_create(dir.id) },
                    )
                    AsterIconButton(
                        icon = TablerIcons.Trash,
                        content_description = stringResource(R.string.alias_delete_directory),
                        onClick = { vm.delete_directory(dir.id) },
                        tint = colors.danger,
                    )
                }
                if (idx < state.directories.lastIndex) AsterDivider(modifier = Modifier)
            }
        }
    }
}

private fun parse_iso_millis(iso: String): Long? = try {
    java.time.Instant.parse(iso).toEpochMilli()
} catch (_: Throwable) {
    try {
        java.time.LocalDateTime.parse(iso.take(19)).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun ghost_expiry_label(iso: String?, enabled: Boolean): String? {
    if (!enabled) return stringResource(R.string.expired)
    if (iso.isNullOrBlank()) return null
    val millis = parse_iso_millis(iso) ?: return iso.take(10)
    val remaining = millis - System.currentTimeMillis()
    if (remaining <= 0) return stringResource(R.string.expired)
    val minutes = remaining / 60_000L
    return when {
        minutes >= 1440 -> stringResource(R.string.ghost_expires_in_days, (minutes / 1440).toInt())
        minutes >= 60 -> stringResource(R.string.ghost_expires_in_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.ghost_expires_in_minutes, minutes.coerceAtLeast(1L).toInt())
    }
}

@Composable
internal fun ghost_tab(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    scroll_state: androidx.compose.foundation.ScrollState,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus_manager = androidx.compose.ui.platform.LocalFocusManager.current
    var show_create_dialog by remember { mutableStateOf(false) }
    var create_note by remember { mutableStateOf("") }
    var is_creating by remember { mutableStateOf(false) }
    var measured_list_height by remember { mutableStateOf(0) }
    var measured_ghost_count by remember { mutableStateOf(state.ghost_aliases.size) }
    var pending_extend by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pending_expire by remember { mutableStateOf<Pair<String, String>?>(null) }
    var list_top_px by remember { mutableStateOf(0) }
    var scroll_to_new_ghost by remember { mutableStateOf(false) }

    val report_action = { ok: Boolean, success_message: Int ->
        android.widget.Toast.makeText(
            context,
            context.getString(if (ok) success_message else R.string.ghost_action_failed),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AsterSpacing.lg)) {
            Text(
                text = stringResource(R.string.ghost_aliases_description),
                color = colors.text_secondary,
                fontSize = 14.sp,
            )
        }
    }
    v_gap(AsterSpacing.lg)

    AsterButton(
        label = if (is_creating) stringResource(R.string.ghost_alias_creating) else stringResource(R.string.generate_ghost_alias),
        onClick = { show_create_dialog = true },
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.lg)

    val ghosts_settled = remember_load_settled(state.is_loading)
    if (state.ghost_aliases.isEmpty() && (state.is_loading || !ghosts_settled)) {
        skeleton_card_list(rows = 3, leading_circle = true, trailing_width = 44.dp)
    } else if (state.ghost_aliases.isEmpty()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.no_ghost_aliases),
                subtitle = state.error ?: stringResource(R.string.no_ghost_aliases_subtitle),
            )
        }
    } else {
        section_label(stringResource(R.string.ghosts_count, state.ghost_aliases.size))
        AsterCard(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    list_top_px = coords.positionInParent().y.toInt()
                }
                .onSizeChanged { size ->
                    val grew = state.ghost_aliases.size > measured_ghost_count
                    val delta = size.height - measured_list_height
                    if (grew && scroll_to_new_ghost) {
                        scroll_to_new_ghost = false
                        scope.launch {
                            scroll_state.animateScrollTo((list_top_px - 48).coerceAtLeast(0))
                        }
                    } else if (grew && delta > 0 && measured_list_height > 0 && scroll_state.value > 0) {
                        scope.launch { scroll_state.scrollBy(delta.toFloat()) }
                    }
                    measured_ghost_count = state.ghost_aliases.size
                    measured_list_height = size.height
                },
        ) {
            state.ghost_aliases.forEachIndexed { idx, g ->
                val ghost_address = g.address.ifBlank { stringResource(R.string.ghost_unnamed, g.id.take(8)) }
                val expiry_label = ghost_expiry_label(g.expires_at, g.enabled)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("ghost", ghost_address))
                                show_copied_toast(context, ghost_address)
                            },
                        )
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = ghost_address,
                            color = if (g.enabled) colors.text_primary else colors.text_muted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (g.note.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = g.note,
                                color = colors.text_secondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                            Text(
                                text = stringResource(R.string.ghost_forwarded_count, g.forward_count),
                                color = colors.text_tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (expiry_label != null) {
                                Text(text = "·", color = colors.text_muted, fontSize = 11.sp)
                                Text(
                                    text = expiry_label,
                                    color = if (g.enabled) colors.text_tertiary else colors.text_muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    if (g.enabled) {
                        AsterIconButton(
                            icon = TablerIcons.Refresh,
                            content_description = stringResource(R.string.ghost_extend),
                            onClick = { pending_extend = g.id to ghost_address },
                        )
                        AsterIconButton(
                            icon = TablerIcons.Ban,
                            content_description = stringResource(R.string.ghost_expire_now),
                            onClick = { pending_expire = g.id to ghost_address },
                            tint = colors.danger,
                        )
                    }
                }
                if (idx < state.ghost_aliases.lastIndex) AsterDivider(modifier = Modifier)
            }
        }
    }

    pending_extend?.let { (id, address) ->
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { pending_extend = null },
            title = stringResource(R.string.ghost_extend),
            message = stringResource(R.string.ghost_extend_confirm_message, address),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { pending_extend = null },
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.ghost_extend),
                    onClick = {
                        pending_extend = null
                        scope.launch {
                            report_action(vm.extend_ghost_alias_now(id), R.string.ghost_extended)
                        }
                    },
                )
            },
        )
    }

    pending_expire?.let { (id, address) ->
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { pending_expire = null },
            title = stringResource(R.string.ghost_expire_now),
            message = stringResource(R.string.ghost_expire_confirm_message, address),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { pending_expire = null },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.ghost_expire_now),
                    onClick = {
                        pending_expire = null
                        scope.launch {
                            report_action(vm.expire_ghost_alias_now(id), R.string.ghost_expired_toast)
                        }
                    },
                )
            },
        )
    }

    if (show_create_dialog) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { if (!is_creating) { show_create_dialog = false; create_note = "" } },
            title = stringResource(R.string.generate_ghost_alias),
            body = {
                AsterTextField(
                    value = create_note,
                    onValueChange = { create_note = it },
                    label = stringResource(R.string.ghost_alias_note_label),
                    placeholder = stringResource(R.string.ghost_alias_note_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    enabled = !is_creating,
                    onClick = { show_create_dialog = false; create_note = "" },
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = if (is_creating) stringResource(R.string.ghost_alias_creating) else stringResource(R.string.create),
                    enabled = !is_creating,
                    is_loading = is_creating,
                    onClick = {
                        is_creating = true
                        focus_manager.clearFocus(force = true)
                        keyboard?.hide()
                        scope.launch {
                            val result = vm.create_ghost_alias_now(create_note.trim())
                            is_creating = false
                            show_create_dialog = false
                            create_note = ""
                            when (result) {
                                is SettingsViewModel.GhostAliasResult.Success -> {
                                    scroll_to_new_ghost = true
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.ghost_alias_created, result.address),
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                                is SettingsViewModel.GhostAliasResult.Failure ->
                                    android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun preferences_tab(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
) {
    val colors = AsterMaterial.colors
    val prefs = state.alias_preferences

    var show_unsubscribe_dialog by remember { mutableStateOf(false) }

    if (prefs == null) {
        skeleton_section_label()
        skeleton_hero_card(lines = 3)
        v_gap(AsterSpacing.lg)
        skeleton_card_list(rows = 4, trailing_width = 48.dp)
        return
    }

    section_label(stringResource(R.string.alias_forwarding))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.alias_sender_format), color = colors.text_primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(5.dp))
                info_dialog_button(stringResource(R.string.alias_sender_format_info_title), stringResource(R.string.alias_sender_format_info_desc))
            }
            Text(stringResource(R.string.alias_sender_format_subtitle), color = colors.text_tertiary, fontSize = 12.sp)
            v_gap(AsterSpacing.sm)
            pref_segment_toggle(
                options = listOf("via" to stringResource(R.string.alias_sender_format_via), "at" to stringResource(R.string.alias_sender_format_at)),
                selected = prefs?.alias_sender_format ?: "via",
                on_select = { vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_sender_format = it)) },
            )
        }
        AsterDivider()
        detail_row(
            title = stringResource(R.string.alias_unsubscribe_action),
            subtitle = when (prefs?.alias_unsubscribe_action) {
                "disable_alias" -> stringResource(R.string.alias_unsubscribe_subtitle)
                "block_contact" -> stringResource(R.string.alias_unsubscribe_block_desc)
                else -> stringResource(R.string.alias_unsubscribe_preserve_desc)
            },
            trailing = {
                Icon(imageVector = TablerIcons.ChevronRight, contentDescription = null, tint = colors.text_muted, modifier = Modifier.size(20.dp))
            },
            on_click = { show_unsubscribe_dialog = true },
            info_title = stringResource(R.string.alias_unsubscribe_action),
            info_description = stringResource(R.string.alias_unsubscribe_preserve_desc),
        )
    }

    v_gap(AsterSpacing.md)
    section_label(stringResource(R.string.alias_behavior))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.alias_disabled_response), color = colors.text_primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(5.dp))
                info_dialog_button(stringResource(R.string.alias_disabled_response_info_title), stringResource(R.string.alias_disabled_response_info_desc))
            }
            Text(stringResource(R.string.alias_disabled_response_subtitle), color = colors.text_tertiary, fontSize = 12.sp)
            v_gap(AsterSpacing.sm)
            pref_segment_toggle(
                options = listOf("ignore" to stringResource(R.string.alias_disabled_response_ignore), "reject" to stringResource(R.string.alias_disabled_response_reject)),
                selected = prefs?.alias_disabled_response ?: "ignore",
                on_select = { vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_disabled_response = it)) },
            )
        }
        AsterDivider()
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.alias_delete_behavior), color = colors.text_primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(5.dp))
                info_dialog_button(stringResource(R.string.alias_delete_behavior_info_title), stringResource(R.string.alias_delete_behavior_info_desc))
            }
            Text(stringResource(R.string.alias_delete_behavior_subtitle), color = colors.text_tertiary, fontSize = 12.sp)
            v_gap(AsterSpacing.sm)
            pref_segment_toggle(
                options = listOf("trash" to stringResource(R.string.alias_delete_behavior_trash), "immediate" to stringResource(R.string.alias_delete_behavior_immediate)),
                selected = prefs?.alias_delete_action ?: "trash",
                on_select = { vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_delete_action = it)) },
            )
        }
        AsterDivider()
        detail_row(
            title = stringResource(R.string.alias_always_expand),
            subtitle = stringResource(R.string.alias_always_expand_subtitle),
            info_title = stringResource(R.string.alias_always_expand_info_title),
            info_description = stringResource(R.string.alias_always_expand_info_desc),
            trailing = {
                AsterSwitch(
                    checked = prefs?.alias_always_expand == true,
                    onCheckedChange = { v -> vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_always_expand = v)) },
                )
            },
        )
        AsterDivider()
        detail_row(
            title = stringResource(R.string.alias_readable_reverse),
            subtitle = stringResource(R.string.alias_readable_reverse_subtitle),
            info_title = stringResource(R.string.alias_readable_reverse_info_title),
            info_description = stringResource(R.string.alias_readable_reverse_info_desc),
            trailing = {
                AsterSwitch(
                    checked = prefs?.readable_reverse_aliases == true,
                    onCheckedChange = { v -> vm.update_alias_preference(UpdateAliasPreferencesRequest(readable_reverse_aliases = v)) },
                )
            },
        )
    }

    if (show_unsubscribe_dialog) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_unsubscribe_dialog = false },
            title = stringResource(R.string.alias_unsubscribe_action),
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    preference_option(
                        label = stringResource(R.string.alias_unsubscribe_preserve),
                        description = stringResource(R.string.alias_unsubscribe_preserve_desc),
                        selected = prefs?.alias_unsubscribe_action.let { it == null || it == "preserve" },
                        onClick = { vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_unsubscribe_action = "preserve")); show_unsubscribe_dialog = false },
                    )
                    preference_option(
                        label = stringResource(R.string.alias_unsubscribe_disable),
                        description = stringResource(R.string.alias_unsubscribe_disable_desc),
                        selected = prefs?.alias_unsubscribe_action == "disable_alias",
                        onClick = { vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_unsubscribe_action = "disable_alias")); show_unsubscribe_dialog = false },
                    )
                    preference_option(
                        label = stringResource(R.string.alias_unsubscribe_block),
                        description = stringResource(R.string.alias_unsubscribe_block_desc),
                        selected = prefs?.alias_unsubscribe_action == "block_contact",
                        onClick = { vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_unsubscribe_action = "block_contact")); show_unsubscribe_dialog = false },
                    )
                }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_unsubscribe_dialog = false },
                )
            },
        )
    }

}


@Composable
private fun pref_segment_toggle(
    options: List<Pair<String, String>>,
    selected: String,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.input_bg, SquircleShape(12.dp))
            .border(1.dp, colors.input_border, SquircleShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(SquircleShape(9.dp))
                    .background(if (active) colors.accent_blue else Color.Transparent)
                    .clickable { on_select(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else colors.text_muted,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun preference_chip(label: String) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .clip(SquircleShape(999.dp))
            .background(colors.accent_blue.copy(alpha = 0.12f))
            .border(1.dp, colors.accent_blue.copy(alpha = 0.4f), SquircleShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = colors.accent_blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun preference_option(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accent_blue.copy(alpha = 0.12f) else colors.bg_secondary)
            .border(
                1.dp,
                if (selected) colors.accent_blue else colors.border_secondary,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = colors.text_tertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accent_blue else Color.Transparent)
                .border(
                    1.5.dp,
                    if (selected) colors.accent_blue else colors.border_primary,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun domain_card(
    domain: CustomDomain,
    is_expanded: Boolean,
    dns_records: List<DnsRecord>,
    is_verifying: Boolean,
    on_expand: () -> Unit,
    on_toggle_catch_all: () -> Unit,
    on_verify: () -> Unit,
    on_delete: () -> Unit,
    catch_all_locked: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val is_active = domain.txt_verified && domain.mx_verified && domain.spf_verified && domain.dkim_verified
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TablerIcons.Building,
                    contentDescription = null,
                    tint = colors.text_tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = domain.domain_name, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (is_active) stringResource(R.string.domain_status_active) else stringResource(R.string.domain_status_setup_required),
                        color = if (is_active) colors.success else colors.warning,
                        fontSize = 12.sp,
                    )
                }
                AsterIconButton(
                    icon = if (is_expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                    content_description = if (is_expanded) stringResource(R.string.domain_collapse) else stringResource(R.string.domain_expand),
                    onClick = on_expand,
                )
                AsterIconButton(
                    icon = TablerIcons.Trash,
                    content_description = stringResource(R.string.domain_delete_domain),
                    onClick = on_delete,
                    tint = colors.danger,
                )
            }

            if (is_expanded) {
                v_gap(AsterSpacing.md)
                AsterDivider()
                v_gap(AsterSpacing.md)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.catch_all), color = colors.text_primary.copy(alpha = if (catch_all_locked) 0.4f else 1f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    AsterSwitch(
                        checked = domain.catch_all_enabled && !catch_all_locked,
                        onCheckedChange = { if (!catch_all_locked) on_toggle_catch_all() },
                        enabled = !catch_all_locked,
                    )
                }

                v_gap(AsterSpacing.md)
                Text(stringResource(R.string.domain_dns_records), color = colors.text_secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                v_gap(AsterSpacing.sm)

                dns_record_row("TXT", domain.txt_verified)
                dns_record_row("MX", domain.mx_verified)
                dns_record_row("SPF", domain.spf_verified)
                dns_record_row("DKIM", domain.dkim_verified)
                dns_record_row("DMARC", domain.dmarc_configured)

                v_gap(AsterSpacing.md)
                AsterSecondaryButton(
                    label = if (is_verifying) stringResource(R.string.domain_verifying) else stringResource(R.string.verify_dns_records),
                    onClick = { if (!is_verifying) on_verify() },
                    enabled = !is_verifying,
                )
            }
        }
    }
}

@Composable
private fun dns_record_row(label: String, verified: Boolean) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Check,
            contentDescription = null,
            tint = if (verified) colors.success else colors.text_muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(text = label, color = if (verified) colors.text_primary else colors.text_muted, fontSize = 13.sp)
    }
}

private data class AliasDomainOption(
    val domain_name: String,
    val domain_id: String? = null,
) {
    val is_platform: Boolean get() = domain_id == null
}

@Composable
private fun create_alias_dialog(
    on_dismiss: () -> Unit,
    on_create: (String, AliasDomainOption, String, String?) -> Unit,
    vm: SettingsViewModel,
) {
    var local_part by remember { mutableStateOf("") }
    var display_name by remember { mutableStateOf("") }
    var selected_domain by remember { mutableStateOf(AliasDomainOption("astermail.org")) }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableStateOf(0) }
    var availability by remember { mutableStateOf<SettingsViewModel.AliasAvailability?>(null) }
    var checking by remember { mutableStateOf(false) }
    var domain_menu_open by remember { mutableStateOf(false) }
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()
    val settings_state by vm.state.collectAsStateWithLifecycle()
    val available_domains = remember(settings_state.domains) {
        listOf(AliasDomainOption("astermail.org"), AliasDomainOption("aster.cx")) +
            settings_state.domains
                .filter { it.status == "active" }
                .map { AliasDomainOption(it.domain_name, it.id) }
    }

    val local_part_valid = remember(local_part) {
        local_part.length in 3..64 &&
            local_part.matches(Regex("^[a-z0-9][a-z0-9._-]*[a-z0-9]$")) &&
            !local_part.contains("..") &&
            !local_part.matches(Regex("^[0-9]+$"))
    }

    LaunchedEffect(local_part, selected_domain) {
        if (!local_part_valid) {
            availability = null
            checking = false
            return@LaunchedEffect
        }
        if (selected_domain.is_platform) {
            checking = true
            availability = null
            delay(500)
            availability = vm.check_alias_availability(local_part, selected_domain.domain_name)
            checking = false
        } else {
            checking = false
            availability = vm.domain_address_availability(local_part, selected_domain.domain_name)
        }
    }

    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.create_alias_dialog_title),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                AsterTextField(
                    value = local_part,
                    onValueChange = { local_part = it.trim().lowercase() },
                    label = stringResource(R.string.create_alias_username_label),
                    placeholder = stringResource(R.string.create_alias_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    error_text = if (local_part.isNotEmpty() && !local_part_valid) {
                        stringResource(R.string.create_alias_invalid_chars)
                    } else {
                        null
                    },
                    trailing_icon = when {
                        checking -> {
                            { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.text_muted, strokeWidth = 2.dp) }
                        }
                        availability is SettingsViewModel.AliasAvailability.Available -> {
                            { Icon(TablerIcons.CircleCheck, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp)) }
                        }
                        availability is SettingsViewModel.AliasAvailability.Taken -> {
                            { Icon(TablerIcons.X, contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp)) }
                        }
                        availability is SettingsViewModel.AliasAvailability.CheckFailed -> {
                            { Icon(TablerIcons.AlertCircle, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp)) }
                        }
                        else -> null
                    },
                )
                val availability_hint: Pair<String, Color>? = when {
                    checking -> stringResource(R.string.checking_availability) to colors.text_muted
                    availability is SettingsViewModel.AliasAvailability.Available ->
                        stringResource(R.string.alias_available) to colors.success
                    availability is SettingsViewModel.AliasAvailability.Taken ->
                        stringResource(R.string.alias_not_available) to colors.danger
                    availability is SettingsViewModel.AliasAvailability.CheckFailed ->
                        stringResource(R.string.alias_check_failed) to colors.warning
                    else -> null
                }
                availability_hint?.let { (text, color) ->
                    Text(text = text, color = color, fontSize = 12.sp)
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(18.dp))
                            .background(colors.input_bg, SquircleShape(18.dp))
                            .border(1.dp, colors.input_border, SquircleShape(18.dp))
                            .clickable { domain_menu_open = true }
                            .padding(horizontal = AsterSpacing.lg, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "@${selected_domain.domain_name}",
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = TablerIcons.ChevronDown,
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    aster_dropdown_menu(
                        expanded = domain_menu_open,
                        on_dismiss = { domain_menu_open = false },
                    ) {
                        available_domains.forEach { domain ->
                            aster_dropdown_item(
                                label = "@${domain.domain_name}",
                                selected = domain.domain_name == selected_domain.domain_name,
                                on_click = {
                                    selected_domain = domain
                                    domain_menu_open = false
                                },
                            )
                        }
                    }
                }
                if (local_part.isNotBlank()) {
                    Text(
                        text = "$local_part@${selected_domain.domain_name}",
                        color = colors.text_secondary,
                        fontSize = 12.sp,
                    )
                }
                AsterTextField(
                    value = display_name,
                    onValueChange = { display_name = it },
                    label = stringResource(R.string.display_name_optional),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .clip(SquircleShape(8.dp))
                        .border(1.dp, colors.border_primary, SquircleShape(8.dp))
                        .clickable {
                            local_part = generate_random_local_part()
                        }
                        .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                ) {
                    Text(stringResource(R.string.create_alias_generate_random), color = colors.text_secondary, fontSize = 13.sp)
                }
                TurnstileWidget(
                    on_token = { token -> captcha_token = token },
                    on_error = { captcha_token = null; captcha_reset++ },
                    on_expired = { captcha_token = null; captcha_reset++ },
                    reset_trigger = captcha_reset,
                    modifier = Modifier.height(65.dp).fillMaxWidth(),
                )
            }
        },
        footer = {
            val can_create = local_part.isNotBlank() &&
                captcha_token != null &&
                !checking &&
                availability !is SettingsViewModel.AliasAvailability.Taken
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.create),
                enabled = can_create,
                onClick = {
                    val t = captcha_token
                    if (can_create && t != null) on_create(local_part, selected_domain, t, display_name.trim().ifBlank { null })
                },
            )
        },
    )
}

@Composable
private fun add_domain_dialog(on_dismiss: () -> Unit, on_add: (String, String) -> Unit) {
    var domain_name by remember { mutableStateOf("") }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableStateOf(0) }
    val colors = AsterMaterial.colors

    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.add_custom_domain_dialog_title),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                AsterTextField(
                    value = domain_name,
                    onValueChange = { domain_name = it.trim().lowercase() },
                    label = stringResource(R.string.domain_name_label),
                    placeholder = stringResource(R.string.domain_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TurnstileWidget(
                    on_token = { token -> captcha_token = token },
                    on_error = { captcha_token = null; captcha_reset++ },
                    on_expired = { captcha_token = null; captcha_reset++ },
                    reset_trigger = captcha_reset,
                    modifier = Modifier.height(65.dp).fillMaxWidth(),
                )
            }
        },
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.alias_action_add),
                enabled = domain_name.isNotBlank() && captcha_token != null,
                onClick = { val t = captcha_token; if (domain_name.isNotBlank() && t != null) on_add(domain_name, t) },
            )
        },
    )
}

