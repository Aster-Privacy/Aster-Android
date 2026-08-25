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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.ui.common.show_copied_toast
import androidx.compose.ui.res.pluralStringResource
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
import org.astermail.android.settings.DomainPurchaseViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.auth.TurnstileWidget
import org.astermail.android.util.generate_random_local_part
import org.astermail.android.settings.shared_settings_view_model

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
    on_open_buy_domain: () -> Unit = {},
    on_open_domain_order: (String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
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
    val instant_alias_delete_locked = plan_vm.is_feature_locked("has_instant_alias_delete") && !plan_state.is_loading

    var selected_tab by remember { mutableStateOf(0) }
    var pending_delete_alias by remember { mutableStateOf<Pair<String, String>?>(null) }
    var show_create_alias by remember { mutableStateOf(false) }
    var show_add_domain by remember { mutableStateOf(false) }
    var expanded_domain_id by remember { mutableStateOf<String?>(null) }
    var domain_dns by remember { mutableStateOf<Map<String, List<DnsRecord>>>(emptyMap()) }
    var verifying_domain_id by remember { mutableStateOf<String?>(null) }
    var domain_verify_results by remember { mutableStateOf<Map<String, SettingsViewModel.DomainVerifyOutcome>>(emptyMap()) }

    val purchase_vm: DomainPurchaseViewModel = hiltViewModel()
    val purchase_state by purchase_vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        purchase_vm.load_orders()
        purchase_vm.check_pending_order()
    }

    LaunchedEffect(purchase_state.checkout_url) {
        val url = purchase_state.checkout_url ?: return@LaunchedEffect
        open_url(context, url)
        purchase_vm.consume_checkout_url()
    }

    LaunchedEffect(purchase_state.resume_order_id) {
        val id = purchase_state.resume_order_id ?: return@LaunchedEffect
        purchase_vm.consume_resume_order()
        on_open_domain_order(id)
    }

    val lifecycle_owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle_owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                purchase_vm.check_pending_order()
                purchase_vm.load_orders()
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

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
        vm.load_alias_preferences()
        vm.load_mail_rules()
    }

    LaunchedEffect(open_create) {
        if (open_create) show_create_alias = true
    }

    LaunchedEffect(selected_tab) {
        when (selected_tab) {
            1 -> purchase_vm.load_orders()
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
                    instant_delete_locked = instant_alias_delete_locked,
                    alias_limit = plan_state.limits?.limits?.get("max_email_aliases")?.limit,
                    on_upgrade = { on_open("billing") },
                )
                1 -> tab_scroll {
                    domain_purchase_area(
                        state = purchase_state,
                        on_buy = on_open_buy_domain,
                        on_open_order = { on_open_domain_order(it.id) },
                        on_cancel = { purchase_vm.cancel_order(it) },
                        on_complete_purchase = { purchase_vm.complete_purchase(it) },
                        on_renew = { purchase_vm.renew_order(it) },
                    )
                    domains_tab(
                        vm = vm,
                        state = state,
                        scope = scope,
                        expanded_domain_id = expanded_domain_id,
                        domain_dns = domain_dns,
                        verifying_domain_id = verifying_domain_id,
                        verify_results = domain_verify_results,
                        on_expanded_change = { expanded_domain_id = it },
                        on_dns_loaded = { id, records -> domain_dns = domain_dns + (id to records) },
                        on_verifying_change = { verifying_domain_id = it },
                        on_verify_result = { id, outcome -> domain_verify_results = domain_verify_results + (id to outcome) },
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
                        required_plan_name = org.astermail.android.billing.plan_display_name(plan_state.plans, "nova"),
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
            on_create = { local_part, domain, token, display_name, note ->
                show_create_alias = false
                scope.launch {
                    val domain_id = domain.domain_id
                    if (domain_id == null) {
                        vm.create_alias_now(local_part, domain.domain_name, token, display_name, note)
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
            on_add = { domain_name, token, on_result ->
                vm.add_domain_now(domain_name, token) { domain, error ->
                    on_result(error)
                    if (domain != null) {
                        show_add_domain = false
                        selected_tab = 1
                        expanded_domain_id = domain.id
                        scope.launch {
                            val records = vm.get_dns_records_now(domain.id)
                            if (records.isNotEmpty()) domain_dns = domain_dns + (domain.id to records)
                        }
                    }
                }
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
    instant_delete_locked: Boolean = false,
    alias_limit: Int? = null,
    on_upgrade: () -> Unit = {},
) {
    var pending_delete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var alias_too_new_date by remember { mutableStateOf<String?>(null) }
    var pending_domain_address_delete by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var alias_query by remember { mutableStateOf("") }
    var show_export by remember { mutableStateOf(false) }
    var show_import by remember { mutableStateOf(false) }
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
      Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg_primary)
                .padding(
                    start = AsterSpacing.lg,
                    end = AsterSpacing.lg,
                    top = AsterSpacing.sm,
                    bottom = AsterSpacing.sm,
                ),
        ) {
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
                if (org.astermail.android.billing.alias_limit_near(state.aliases.size, alias_limit)) {
                    Text(
                        text = stringResource(R.string.alias_limit_notice, state.aliases.size, alias_limit ?: 0),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = on_upgrade),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { show_import = true }) {
                        Text(
                            stringResource(R.string.alias_import_action),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                        )
                    }
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
            if (state.aliases.isNotEmpty() || state.custom_domain_addresses.isNotEmpty()) {
                v_gap(AsterSpacing.xs)
                org.astermail.android.ui.common.list_search_bar(
                    query = alias_query,
                    on_query_change = { alias_query = it },
                    placeholder = stringResource(R.string.search_aliases),
                    test_tag = "alias_search_bar",
                )
            }
        }
        AsterDivider(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = AsterSpacing.lg,
                end = AsterSpacing.lg,
                top = AsterSpacing.sm,
                bottom = AsterSpacing.lg,
            ),
        ) {
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
                        on_delete = {
                            val eligible_at = alias_delete_eligible_at(alias.created_at)
                            if (instant_delete_locked && eligible_at != null && System.currentTimeMillis() < eligible_at) {
                                alias_too_new_date = format_alias_date(eligible_at)
                            } else {
                                pending_delete = alias.id to alias.address
                            }
                        },
                        on_edit_note = { note_editing = alias.id to (alias.encrypted_note ?: "") },
                        on_restore = { claimed -> vm.restore_orphaned_alias(alias.id, claimed) },
                        expanded = expanded,
                        on_toggle_expanded = { vm.toggle_alias_expanded(alias.id) },
                        panel_content = {
                            alias_detail_panel(
                                alias = alias,
                                detail = state.alias_details[alias.id] ?: AliasDetailState(),
                                vm = vm,
                                rule_delivery = alias_rule_delivery_note(alias, state.mail_rules, state.labels),
                                rule_label = alias_rule_label_note(alias, state.mail_rules, state.tags),
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
      }

        if (show_import) {
            alias_import_dialog(
                vm = vm,
                state = state,
                on_dismiss = { show_import = false },
            )
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

        alias_too_new_date?.let { date ->
            org.astermail.android.design.components.AsterDialog(
                on_dismiss = { alias_too_new_date = null },
                title = stringResource(R.string.alias_too_new_title),
                message = stringResource(R.string.alias_too_new_message, date),
                footer = {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.close),
                        onClick = { alias_too_new_date = null },
                    )
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.upgrade),
                        onClick = { alias_too_new_date = null; on_upgrade() },
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
internal fun alias_rule_delivery_note(
    alias: org.astermail.android.api.settings.AliasInfo,
    rules: List<org.astermail.android.api.mail_rules.MailRule>,
    labels: List<org.astermail.android.api.labels.LabelItem>,
): AliasRuleDeliveryNote? {
    val delivery = org.astermail.android.mail_rules.alias_rule_delivery(rules, alias.address) ?: return null
    val missing_name = stringResource(R.string.alias_delivery_folder_missing)
    val folder_name = labels.firstOrNull {
        it.label_token == delivery.folder_token && !it.encrypted_name.isNullOrBlank()
    }?.encrypted_name ?: missing_name
    return AliasRuleDeliveryNote(
        rule_name = delivery.rule_name,
        folder_name = folder_name,
        matches_alias_delivery = alias.delivery_folder_token == delivery.folder_token,
    )
}

@Composable
internal fun alias_rule_label_note(
    alias: org.astermail.android.api.settings.AliasInfo,
    rules: List<org.astermail.android.api.mail_rules.MailRule>,
    tags: List<org.astermail.android.api.tags.TagItem>,
): AliasRuleLabelNote? {
    val applied = org.astermail.android.mail_rules.alias_rule_label(rules, alias.address) ?: return null
    val missing_name = stringResource(R.string.alias_delivery_label_missing)
    val label_names = applied.label_tokens.joinToString(", ") { token ->
        tags.firstOrNull { it.tag_token == token && it.encrypted_name.isNotBlank() }?.encrypted_name
            ?: missing_name
    }
    return AliasRuleLabelNote(
        rule_name = applied.rule_name,
        label_names = label_names,
        matches_alias_label = alias.delivery_label_token != null &&
            applied.label_tokens.contains(alias.delivery_label_token),
    )
}

@Composable
private fun alias_meta_row(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    max_lines: Int = 1,
) {
    val font_size = 12.sp
    val line_height = font_size * 1.4f
    val line_height_dp = with(LocalDensity.current) { line_height.toDp() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.height(line_height_dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = font_size,
            lineHeight = line_height,
            maxLines = max_lines,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
    on_restore: (suspend (String) -> SettingsViewModel.AliasRestoreResult)? = null,
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
                    if (alias.decryption_failed) return@combinedClickable
                    if (on_toggle_expanded != null) on_toggle_expanded() else on_edit_note?.invoke()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    copy_address(context, alias.address)
                },
            )
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
    ) {
        val grace_ends = remember(alias.downgrade_grace_expires_at) {
            format_alias_grace_end(alias.downgrade_grace_expires_at)
        }

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
                if (alias.decryption_failed) {
                    Spacer(Modifier.height(3.dp))
                    alias_meta_row(
                        icon = TablerIcons.Lock,
                        text = stringResource(R.string.alias_locked_title),
                        color = colors.danger,
                        max_lines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.alias_locked_body),
                        color = colors.text_tertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    if (on_restore != null && alias.routing_address_hash.isNotBlank()) {
                        alias_restore_section(alias = alias, on_restore = on_restore)
                    }
                } else {
                    val display_name_val = alias.encrypted_display_name
                    if (!display_name_val.isNullOrBlank()) {
                        Spacer(Modifier.height(3.dp))
                        alias_meta_row(
                            icon = TablerIcons.Id,
                            text = display_name_val,
                            color = colors.text_secondary,
                        )
                    }
                    if (on_edit_note != null) {
                        Spacer(Modifier.height(3.dp))
                        alias_meta_row(
                            icon = TablerIcons.Edit,
                            text = if (note_val.isNullOrBlank()) {
                                stringResource(R.string.alias_note_add)
                            } else {
                                note_val
                            },
                            color = if (note_val.isNullOrBlank()) colors.text_muted else colors.text_tertiary,
                            max_lines = 3,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            grace_ends != null ->
                                stringResource(R.string.alias_grace_ends, grace_ends)
                            !alias.is_enabled -> stringResource(R.string.alias_status_disabled_badge)
                            delivery_folder_name != null ->
                                stringResource(R.string.forwards_to_folder, delivery_folder_name)
                            else -> stringResource(R.string.forwards_to_inbox)
                        },
                        color = when {
                            grace_ends != null -> colors.warning
                            alias.is_enabled -> colors.text_tertiary
                            else -> colors.danger
                        },
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    if (grace_ends != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.alias_grace_upgrade_hint),
                            color = colors.warning,
                            fontSize = 11.sp,
                            maxLines = 2,
                        )
                    }
                }
            }
            AsterSwitch(
                checked = alias.is_enabled,
                enabled = grace_ends == null,
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
            val panel_interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = panel_interaction,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                panel_content()
            }
        }
    }
}

@Composable
private fun alias_restore_section(
    alias: org.astermail.android.api.settings.AliasInfo,
    on_restore: suspend (String) -> SettingsViewModel.AliasRestoreResult,
) {
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()
    var open by remember(alias.id) { mutableStateOf(false) }
    var claimed_local_part by remember(alias.id) { mutableStateOf("") }
    var restoring by remember(alias.id) { mutableStateOf(false) }
    var restore_error by remember(alias.id) { mutableStateOf<String?>(null) }
    val mismatch_text = stringResource(R.string.alias_restore_mismatch)
    val failed_text = stringResource(R.string.alias_restore_failed)

    Spacer(Modifier.height(AsterSpacing.sm))
    if (!open) {
        AsterGhostButton(
            label = stringResource(R.string.alias_restore_action),
            onClick = { open = true },
            modifier = Modifier.testTag("alias_restore_open_${alias.id}"),
        )
        return
    }
    Text(
        text = stringResource(R.string.alias_restore_prompt),
        color = colors.text_tertiary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
    Spacer(Modifier.height(AsterSpacing.sm))
    AsterTextField(
        value = claimed_local_part,
        onValueChange = {
            claimed_local_part = it.substringBefore("@").trim().lowercase()
            restore_error = null
        },
        label = stringResource(R.string.alias_restore_placeholder),
        placeholder = "name@${alias.domain}",
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alias_restore_input_${alias.id}"),
    )
    Spacer(Modifier.height(AsterSpacing.sm))
    AsterButton(
        label = stringResource(R.string.alias_restore_confirm),
        onClick = {
            if (restoring || claimed_local_part.isBlank()) return@AsterButton
            scope.launch {
                restoring = true
                restore_error = null
                when (val outcome = on_restore(claimed_local_part)) {
                    is SettingsViewModel.AliasRestoreResult.Restored -> open = false
                    is SettingsViewModel.AliasRestoreResult.AddressMismatch ->
                        restore_error = mismatch_text
                    is SettingsViewModel.AliasRestoreResult.Unverifiable ->
                        restore_error = failed_text
                    is SettingsViewModel.AliasRestoreResult.Failed ->
                        restore_error = outcome.message.ifBlank { failed_text }
                }
                restoring = false
            }
        },
        enabled = !restoring && claimed_local_part.isNotBlank(),
        is_loading = restoring,
        modifier = Modifier.testTag("alias_restore_confirm_${alias.id}"),
    )
    restore_error?.let { message ->
        Spacer(Modifier.height(AsterSpacing.xs))
        Text(
            text = message,
            color = colors.danger,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
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
    verify_results: Map<String, SettingsViewModel.DomainVerifyOutcome>,
    on_expanded_change: (String?) -> Unit,
    on_dns_loaded: (String, List<DnsRecord>) -> Unit,
    on_verifying_change: (String?) -> Unit,
    on_verify_result: (String, SettingsViewModel.DomainVerifyOutcome) -> Unit,
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
            text = pluralStringResource(R.plurals.domains_count_plural, state.domains.size, state.domains.size),
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
                verify_message = verify_results[domain.id]?.message,
                verify_failed = verify_results[domain.id]?.verified == false,
                on_verify = {
                    on_verifying_change(domain.id)
                    scope.launch {
                        try {
                            val outcome = vm.trigger_domain_verification_now(domain.id)
                            on_verify_result(domain.id, outcome)
                            if (!outcome.rate_limited) {
                                val records = vm.get_dns_records_now(domain.id)
                                if (records.isNotEmpty()) on_dns_loaded(domain.id, records)
                            }
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
    required_plan_name: String? = null,
) {
    if (locked) {
        val gate_plan = org.astermail.android.billing.required_plan_name(
            required_plan_name,
            stringResource(R.string.plan_name_nova),
        )
        UpgradeGate(
            title = stringResource(R.string.alias_directories),
            description = stringResource(R.string.alias_directories_description),
            plan_name = gate_plan,
            on_upgrade = on_upgrade,
            requires_label = stringResource(R.string.requires_plan, gate_plan),
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

private const val ALIAS_DELETE_COOLDOWN_MILLIS = 30L * 24L * 60L * 60L * 1000L

private fun alias_delete_eligible_at(created_at: String): Long? {
    val created = parse_iso_millis(created_at) ?: return null
    return created + ALIAS_DELETE_COOLDOWN_MILLIS
}

private fun format_alias_date(millis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))

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
    var show_default_domain_dialog by remember { mutableStateOf(false) }

    if (prefs == null) {
        skeleton_section_label()
        skeleton_hero_card(lines = 3)
        v_gap(AsterSpacing.lg)
        skeleton_card_list(rows = 4, trailing_width = 48.dp)
        return
    }

    val available_domains = remember(state.domains) { alias_domain_options(state.domains) }
    val default_domain = resolve_default_alias_domain(prefs.alias_default_domain, available_domains)

    section_label(stringResource(R.string.alias_domains_section))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        detail_row(
            title = stringResource(R.string.alias_default_domain),
            subtitle = stringResource(R.string.alias_default_domain_subtitle),
            info_title = stringResource(R.string.alias_default_domain),
            info_description = stringResource(R.string.alias_default_domain_info_desc),
            on_click = { show_default_domain_dialog = true },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(default_domain, color = colors.text_secondary, fontSize = 14.sp)
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Icon(imageVector = TablerIcons.ChevronRight, contentDescription = null, tint = colors.text_muted, modifier = Modifier.size(20.dp))
                }
            },
        )
    }

    v_gap(AsterSpacing.md)
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

    if (show_default_domain_dialog) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_default_domain_dialog = false },
            title = stringResource(R.string.alias_default_domain),
            body = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    available_domains.forEach { option ->
                        preference_option(
                            label = "@${option.domain_name}",
                            description = if (option.is_platform) {
                                stringResource(R.string.alias_default_domain_platform)
                            } else {
                                stringResource(R.string.alias_default_domain_custom)
                            },
                            selected = option.domain_name == default_domain,
                            onClick = {
                                vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_default_domain = option.domain_name))
                                show_default_domain_dialog = false
                            },
                        )
                    }
                }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_default_domain_dialog = false },
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
    verify_message: String?,
    verify_failed: Boolean,
    on_expand: () -> Unit,
    on_toggle_catch_all: () -> Unit,
    on_verify: () -> Unit,
    on_delete: () -> Unit,
    catch_all_locked: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val is_active = domain.txt_verified && domain.mx_verified && domain.spf_verified && domain.dkim_verified
    var confirm_delete by remember(domain.id) { mutableStateOf(false) }
    var name_expanded by remember(domain.id) { mutableStateOf(false) }

    if (confirm_delete) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { confirm_delete = false },
            title = stringResource(R.string.domain_delete_confirm_title),
            message = stringResource(R.string.domain_delete_confirm_message, domain.domain_name),
            confirm_label = stringResource(R.string.domain_delete_domain),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                confirm_delete = false
                on_delete()
            },
        )
    }

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
                    Text(
                        text = domain.domain_name,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (name_expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.combinedClickable(
                            onClick = { name_expanded = !name_expanded },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                copy_dns_value(context, "domain", domain.domain_name)
                            },
                        ),
                    )
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
                    onClick = { confirm_delete = true },
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
                v_gap(4.dp)
                Text(
                    text = stringResource(R.string.domain_dns_instructions),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
                v_gap(AsterSpacing.sm)

                val record_states = listOf(
                    "TXT" to domain.txt_verified,
                    "MX" to domain.mx_verified,
                    "SPF" to domain.spf_verified,
                    "DKIM" to domain.dkim_verified,
                    "DMARC" to domain.dmarc_configured,
                )
                Text(
                    text = stringResource(
                        R.string.domain_records_verified,
                        record_states.count { it.second },
                        record_states.size,
                    ),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                v_gap(4.dp)
                record_states.forEach { (label, verified) -> dns_record_row(label, verified) }

                if (dns_records.isNotEmpty()) {
                    v_gap(AsterSpacing.sm)
                    dns_records.forEachIndexed { index, record ->
                        if (index > 0) v_gap(AsterSpacing.sm)
                        dns_record_detail(
                            record = record,
                            on_copy = { label, value -> copy_dns_value(context, label, value) },
                        )
                    }
                }

                if (!verify_message.isNullOrBlank()) {
                    v_gap(AsterSpacing.md)
                    Text(
                        text = verify_message,
                        color = if (verify_failed) colors.warning else colors.success,
                        fontSize = 12.sp,
                    )
                }

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

private fun copy_dns_value(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.copied_to_clipboard),
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}

@Composable
private fun dns_record_detail(record: DnsRecord, on_copy: (String, String) -> Unit) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_secondary, RoundedCornerShape(10.dp))
            .padding(AsterSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (record.verified) TablerIcons.Check else TablerIcons.Clock,
                contentDescription = null,
                tint = if (record.verified) colors.success else colors.text_muted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = record.type.uppercase(),
                color = colors.text_primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = if (record.verified) {
                    stringResource(R.string.domain_record_found)
                } else if (record.required) {
                    stringResource(R.string.domain_record_required)
                } else {
                    stringResource(R.string.domain_record_recommended)
                },
                color = if (record.verified) colors.success else colors.text_tertiary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        }
        v_gap(4.dp)
        dns_record_field(
            label = stringResource(R.string.domain_record_host),
            value = record.name,
            on_copy = { on_copy(record.type, record.name) },
        )
        dns_record_field(
            label = stringResource(R.string.domain_record_value),
            value = if (record.priority != null) "${record.priority} ${record.value}" else record.value,
            on_copy = { on_copy(record.type, record.value) },
        )
    }
}

@Composable
private fun dns_record_field(label: String, value: String, on_copy: () -> Unit) {
    val colors = AsterMaterial.colors
    var value_expanded by remember(value) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.text_tertiary,
            fontSize = 11.sp,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value,
            color = colors.text_secondary,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = if (value_expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { value_expanded = !value_expanded },
        )
        AsterIconButton(
            icon = TablerIcons.Copy,
            content_description = stringResource(R.string.copy_to_clipboard),
            onClick = on_copy,
        )
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

internal data class AliasDomainOption(
    val domain_name: String,
    val domain_id: String? = null,
) {
    val is_platform: Boolean get() = domain_id == null
}

internal fun alias_domain_options(domains: List<CustomDomain>): List<AliasDomainOption> =
    listOf(AliasDomainOption("astermail.org"), AliasDomainOption("aster.cx")) +
        domains
            .filter { it.status == "active" }
            .map { AliasDomainOption(it.domain_name, it.id) }

private fun resolve_default_alias_domain(
    preferred: String?,
    options: List<AliasDomainOption>,
): String =
    options.firstOrNull { it.domain_name == preferred }?.domain_name
        ?: options.firstOrNull()?.domain_name
        ?: "astermail.org"

@Composable
private fun create_alias_dialog(
    on_dismiss: () -> Unit,
    on_create: (String, AliasDomainOption, String, String?, String?) -> Unit,
    vm: SettingsViewModel,
) {
    var local_part by remember { mutableStateOf("") }
    var display_name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selected_domain by remember { mutableStateOf<AliasDomainOption?>(null) }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableStateOf(0) }
    var availability by remember { mutableStateOf<SettingsViewModel.AliasAvailability?>(null) }
    var checking by remember { mutableStateOf(false) }
    var domain_menu_open by remember { mutableStateOf(false) }
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()
    val settings_state by vm.state.collectAsStateWithLifecycle()
    val available_domains = remember(settings_state.domains) {
        alias_domain_options(settings_state.domains)
    }
    val preferred_domain = resolve_default_alias_domain(
        settings_state.alias_preferences?.alias_default_domain,
        available_domains,
    )
    val active_domain = selected_domain
        ?: available_domains.firstOrNull { it.domain_name == preferred_domain }
        ?: AliasDomainOption(preferred_domain)

    val local_part_valid = remember(local_part) {
        local_part.length in 3..64 &&
            local_part.matches(Regex("^[a-z0-9][a-z0-9._-]*[a-z0-9]$")) &&
            !local_part.contains("..") &&
            !local_part.matches(Regex("^[0-9]+$"))
    }

    LaunchedEffect(local_part, active_domain) {
        if (!local_part_valid) {
            availability = null
            checking = false
            return@LaunchedEffect
        }
        if (active_domain.is_platform) {
            checking = true
            availability = null
            delay(500)
            availability = vm.check_alias_availability(local_part, active_domain.domain_name)
            checking = false
        } else {
            checking = false
            availability = vm.domain_address_availability(local_part, active_domain.domain_name)
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
                            text = "@${active_domain.domain_name}",
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
                                selected = domain.domain_name == active_domain.domain_name,
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
                        text = "$local_part@${active_domain.domain_name}",
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
                if (active_domain.is_platform) {
                    AsterTextField(
                        value = note,
                        onValueChange = { if (it.length <= 500) note = it },
                        label = stringResource(R.string.create_alias_note_label),
                        placeholder = stringResource(R.string.create_alias_note_placeholder),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                    if (can_create && t != null) {
                        on_create(
                            local_part,
                            active_domain,
                            t,
                            display_name.trim().ifBlank { null },
                            note.replace(Regex("[\\x00-\\x08\\x0B-\\x1F\\x7F]"), "").trim().ifBlank { null },
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun add_domain_dialog(
    on_dismiss: () -> Unit,
    on_add: (String, String, (String?) -> Unit) -> Unit,
) {
    var domain_name by remember { mutableStateOf("") }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var error_message by remember { mutableStateOf<String?>(null) }
    val colors = AsterMaterial.colors
    val name_valid = remember(domain_name) { is_valid_domain_name(domain_name) }

    org.astermail.android.design.components.AsterDialog(
        on_dismiss = { if (!submitting) on_dismiss() },
        title = stringResource(R.string.add_custom_domain_dialog_title),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                AsterTextField(
                    value = domain_name,
                    onValueChange = {
                        domain_name = it.trim().lowercase()
                        error_message = null
                    },
                    label = stringResource(R.string.domain_name_label),
                    placeholder = stringResource(R.string.domain_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (domain_name.isNotBlank() && !name_valid) {
                    Text(
                        text = stringResource(R.string.domain_add_invalid),
                        color = colors.warning,
                        fontSize = 12.sp,
                    )
                }
                error_message?.let { message ->
                    Text(text = message, color = colors.danger, fontSize = 12.sp)
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
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = { if (!submitting) on_dismiss() },
            )
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = if (submitting) stringResource(R.string.domain_adding) else stringResource(R.string.alias_action_add),
                enabled = name_valid && captcha_token != null && !submitting,
                onClick = {
                    val token = captcha_token
                    if (name_valid && token != null && !submitting) {
                        submitting = true
                        error_message = null
                        on_add(domain_name, token) { failure ->
                            submitting = false
                            if (failure != null) {
                                error_message = failure
                                captcha_token = null
                                captcha_reset++
                            }
                        }
                    }
                },
            )
        },
    )
}

internal fun is_valid_domain_name(value: String): Boolean {
    val name = value.trim().lowercase().removeSuffix(".")
    if (name.length !in 4..253) return false
    val labels = name.split('.')
    if (labels.size < 2) return false
    val tld = labels.last()
    if (tld.length < 2 || !tld.all { it.isLetter() }) return false
    return labels.all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            !label.startsWith('-') &&
            !label.endsWith('-') &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

private fun format_alias_grace_end(value: String?): String? {
    if (value.isNullOrBlank()) return null

    val millis = runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
        ?: runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: return null

    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        .format(java.util.Date(millis))
}
