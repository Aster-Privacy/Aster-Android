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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.settings.DecryptedDeletedAlias
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.auth.TurnstileWidget

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

    detail_scaffold(title = stringResource(R.string.aliases), on_back = on_back) {
        ScrollableTabRow(
            selectedTabIndex = selected_tab,
            containerColor = colors.bg_primary,
            contentColor = colors.accent_blue,
            edgePadding = AsterSpacing.md,
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

        when (selected_tab) {
            0 -> aliases_tab(
                vm = vm,
                state = state,
                context = context,
                scope = scope,
                on_show_create = { show_create_alias = true },
                restore_locked = alias_restore_locked,
                on_upgrade = { on_open("billing") },
            )
            1 -> domains_tab(
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
            2 -> directories_tab(
                vm = vm,
                state = state,
                scope = scope,
                locked = alias_directories_locked,
                on_upgrade = { on_open("billing") },
            )
            3 -> ghost_tab(vm = vm, state = state, context = context, scope = scope)
            4 -> preferences_tab(vm = vm, state = state)
        }
    }

    if (show_create_alias) {
        create_alias_dialog(
            on_dismiss = { show_create_alias = false },
            on_create = { local_part, domain, token ->
                show_create_alias = false
                scope.launch { vm.create_alias_now(local_part, domain, token) }
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
private fun aliases_tab(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    on_show_create: () -> Unit,
    restore_locked: Boolean = false,
    on_upgrade: () -> Unit = {},
) {
    var pending_delete by remember { mutableStateOf<Pair<String, String>?>(null) }
    val colors = AsterMaterial.colors

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
        TextButton(onClick = on_show_create) {
            Text(stringResource(R.string.create), color = colors.accent_blue, fontSize = 14.sp)
        }
    }
    v_gap(AsterSpacing.sm)

    if (state.is_loading && state.aliases.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
        }
    } else if (state.aliases.isEmpty()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.no_aliases),
                subtitle = state.error ?: stringResource(R.string.no_aliases_subtitle),
            )
        }
    } else {
        val page_size = 100
        val page_count = (state.aliases.size + page_size - 1) / page_size
        var alias_page by remember(state.aliases.size) { mutableStateOf(0) }
        val current_page = alias_page.coerceIn(0, (page_count - 1).coerceAtLeast(0))
        val page_from = current_page * page_size
        val page_to = minOf(page_from + page_size, state.aliases.size)
        val page_items = state.aliases.subList(page_from, page_to)

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            page_items.forEachIndexed { idx, alias ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                ) {
                    Text(
                        text = alias.address,
                        color = if (alias.decryption_failed) colors.text_muted else colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val display_name_val = alias.encrypted_display_name
                    if (!display_name_val.isNullOrBlank()) {
                        Text(
                            text = display_name_val,
                            color = colors.text_secondary,
                            fontSize = 12.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (alias.is_enabled) stringResource(R.string.forwards_to_inbox) else stringResource(R.string.alias_status_disabled_badge),
                            color = if (alias.is_enabled) colors.text_tertiary else colors.danger,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = alias.is_enabled,
                            onCheckedChange = { vm.toggle_alias(alias.id) },
                            modifier = Modifier.size(36.dp, 20.dp),
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = colors.accent_blue,
                                uncheckedTrackColor = colors.text_muted.copy(alpha = 0.35f),
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        AsterIconButton(
                            icon = Icons.Outlined.ContentCopy,
                            content_description = stringResource(R.string.copy),
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("alias", alias.address))
                                android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                        AsterIconButton(
                            icon = Icons.Outlined.Delete,
                            content_description = stringResource(R.string.delete),
                            onClick = { pending_delete = alias.id to alias.address },
                            tint = colors.danger,
                        )
                    }
                }
                if (idx < page_items.lastIndex) AsterDivider(modifier = Modifier)
            }
        }

        if (page_count > 1) {
            v_gap(AsterSpacing.sm)
            alias_pager(
                current_page = current_page,
                page_count = page_count,
                on_prev = { alias_page = (current_page - 1).coerceAtLeast(0) },
                on_next = { alias_page = (current_page + 1).coerceAtMost(page_count - 1) },
            )
        }
    }

    if (state.custom_domain_addresses.isNotEmpty()) {
        v_gap(AsterSpacing.md)
        Text(
            text = stringResource(R.string.custom_domains),
            color = colors.text_tertiary,
            fontSize = 13.sp,
        )
        v_gap(AsterSpacing.sm)
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            state.custom_domain_addresses.forEachIndexed { idx, addr ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = addr.address,
                        color = if (addr.decryption_failed) colors.text_muted else colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    AsterIconButton(
                        icon = Icons.Outlined.ContentCopy,
                        content_description = stringResource(R.string.copy),
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("alias", addr.address))
                            android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (idx < state.custom_domain_addresses.lastIndex) AsterDivider(modifier = Modifier)
            }
        }
    }

    recently_deleted_section(
        vm = vm,
        state = state,
        restore_locked = restore_locked,
        on_upgrade = on_upgrade,
    )

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
}

@Composable
private fun alias_pager(
    current_page: Int,
    page_count: Int,
    on_prev: () -> Unit,
    on_next: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsterIconButton(
            icon = Icons.Outlined.KeyboardArrowLeft,
            content_description = stringResource(R.string.back),
            onClick = on_prev,
            enabled = current_page > 0,
        )
        Text(
            text = stringResource(R.string.alias_page_indicator, current_page + 1, page_count),
            color = colors.text_secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = AsterSpacing.md),
        )
        AsterIconButton(
            icon = Icons.Outlined.KeyboardArrowRight,
            content_description = stringResource(R.string.next),
            onClick = on_next,
            enabled = current_page < page_count - 1,
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
            imageVector = Icons.Outlined.Delete,
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
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
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
                            icon = Icons.Outlined.Delete,
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

    if (state.domains_loading && state.domains.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
        }
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

    LaunchedEffect(dir_key) {
        if (!key_valid) {
            dir_availability = null
            dir_checking = false
            return@LaunchedEffect
        }
        dir_checking = true
        dir_availability = null
        delay(500)
        dir_availability = vm.check_directory_availability(dir_key)
        dir_checking = false
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = dir_key,
            onValueChange = { dir_key = it.trim().lowercase().filter { c -> c.isLetterOrDigit() || c == '-' } },
            label = { Text(stringResource(R.string.alias_directory_key_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(SquircleShape(12.dp))
                    .border(1.dp, colors.border_primary, SquircleShape(12.dp))
                    .clickable { separator_menu_open = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(dir_separator, color = colors.text_primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = separator_menu_open,
                onDismissRequest = { separator_menu_open = false },
            ) {
                separators.forEach { sep ->
                    DropdownMenuItem(
                        text = { Text(sep, fontSize = 16.sp) },
                        onClick = { dir_separator = sep; separator_menu_open = false },
                    )
                }
            }
        }
    }
    v_gap(AsterSpacing.xs)

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

    if (state.directories_loading && state.directories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
        }
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
                    Column(modifier = Modifier.weight(1f)) {
                        val label = dir.decrypted_label.ifBlank { "…" }
                        Text(
                            text = "anything.${label}@${dir.domain}",
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
                    Switch(
                        checked = dir.auto_create_enabled,
                        onCheckedChange = { vm.toggle_directory_auto_create(dir.id) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.accent_blue,
                            uncheckedTrackColor = colors.text_muted.copy(alpha = 0.35f),
                        ),
                    )
                    AsterIconButton(
                        icon = Icons.Outlined.Delete,
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

@Composable
private fun ghost_tab(
    vm: SettingsViewModel,
    state: org.astermail.android.settings.SettingsUiState,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val colors = AsterMaterial.colors
    var show_create_dialog by remember { mutableStateOf(false) }
    var create_note by remember { mutableStateOf("") }
    var is_creating by remember { mutableStateOf(false) }

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

    if (state.is_loading && state.ghost_aliases.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
        }
    } else if (state.ghost_aliases.isEmpty()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.no_ghost_aliases),
                subtitle = state.error ?: stringResource(R.string.no_ghost_aliases_subtitle),
            )
        }
    } else {
        section_label(stringResource(R.string.ghosts_count, state.ghost_aliases.size))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            state.ghost_aliases.forEachIndexed { idx, g ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                g.address.ifBlank { "Ghost #${g.id.take(8)}" },
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (g.note.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(g.note, color = colors.text_secondary, fontSize = 12.sp)
                            }
                        }
                        if (g.enabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                                AsterGhostButton(
                                    label = stringResource(R.string.extend),
                                    onClick = { vm.extend_ghost_alias(g.id) },
                                )
                                AsterGhostButton(
                                    label = stringResource(R.string.expire),
                                    onClick = { vm.expire_ghost_alias(g.id) },
                                )
                            }
                        } else {
                            Text(stringResource(R.string.expired), color = colors.text_muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                        Text(
                            "${g.forward_count} forwarded",
                            color = colors.text_tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (!g.expires_at.isNullOrBlank()) {
                            Text(
                                "Expires ${g.expires_at}",
                                color = if (g.enabled) colors.text_tertiary else colors.text_muted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                if (idx < state.ghost_aliases.lastIndex) AsterDivider(modifier = Modifier)
            }
        }
    }

    if (show_create_dialog) {
        AlertDialog(
            onDismissRequest = { if (!is_creating) { show_create_dialog = false; create_note = "" } },
            containerColor = colors.bg_card,
            title = {
                Text(stringResource(R.string.generate_ghost_alias), color = colors.text_primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(stringResource(R.string.ghost_alias_note_label), color = colors.text_muted, fontSize = 12.sp)
                    Spacer(Modifier.height(AsterSpacing.sm))
                    OutlinedTextField(
                        value = create_note,
                        onValueChange = { create_note = it },
                        placeholder = { Text(stringResource(R.string.ghost_alias_note_placeholder)) },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent_blue)
                        .clickable(enabled = !is_creating) {
                            is_creating = true
                            scope.launch {
                                val result = vm.create_ghost_alias_now(create_note.trim())
                                is_creating = false
                                show_create_dialog = false
                                create_note = ""
                                when (result) {
                                    is SettingsViewModel.GhostAliasResult.Success ->
                                        android.widget.Toast.makeText(context, "Created: ${result.address}", android.widget.Toast.LENGTH_LONG).show()
                                    is SettingsViewModel.GhostAliasResult.Failure ->
                                        android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                ) {
                    Text(
                        if (is_creating) stringResource(R.string.ghost_alias_creating) else stringResource(R.string.create),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.border_primary, RoundedCornerShape(10.dp))
                        .clickable(enabled = !is_creating) { show_create_dialog = false; create_note = "" }
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                ) {
                    Text(stringResource(R.string.cancel), color = colors.text_primary, fontSize = 14.sp)
                }
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
        Box(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
        }
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
                Icon(imageVector = Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.text_muted, modifier = Modifier.size(20.dp))
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
                Switch(
                    checked = prefs?.alias_always_expand == true,
                    onCheckedChange = { v -> vm.update_alias_preference(UpdateAliasPreferencesRequest(alias_always_expand = v)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent_blue, uncheckedTrackColor = colors.text_muted.copy(alpha = 0.35f)),
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
                Switch(
                    checked = prefs?.readable_reverse_aliases == true,
                    onCheckedChange = { v -> vm.update_alias_preference(UpdateAliasPreferencesRequest(readable_reverse_aliases = v)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent_blue, uncheckedTrackColor = colors.text_muted.copy(alpha = 0.35f)),
                )
            },
        )
    }

    if (show_unsubscribe_dialog) {
        AlertDialog(
            onDismissRequest = { show_unsubscribe_dialog = false },
            containerColor = colors.bg_card,
            title = { Text(stringResource(R.string.alias_unsubscribe_action), color = colors.text_primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
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
            confirmButton = {},
            dismissButton = { TextButton(onClick = { show_unsubscribe_dialog = false }) { Text(stringResource(R.string.cancel)) } },
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
            .clip(SquircleShape(8.dp))
            .background(colors.accent_blue.copy(alpha = 0.12f))
            .border(1.dp, colors.accent_blue.copy(alpha = 0.4f), SquircleShape(8.dp))
            .padding(horizontal = AsterSpacing.sm, vertical = 4.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .background(if (selected) colors.accent_blue.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = colors.text_tertiary, fontSize = 13.sp)
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(20.dp),
            )
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
                    imageVector = Icons.Outlined.Domain,
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
                    icon = if (is_expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    content_description = if (is_expanded) stringResource(R.string.domain_collapse) else stringResource(R.string.domain_expand),
                    onClick = on_expand,
                )
                AsterIconButton(
                    icon = Icons.Outlined.Delete,
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
                    Switch(
                        checked = domain.catch_all_enabled && !catch_all_locked,
                        onCheckedChange = { if (!catch_all_locked) on_toggle_catch_all() },
                        enabled = !catch_all_locked,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.accent_blue,
                            uncheckedTrackColor = colors.text_muted.copy(alpha = 0.35f),
                        ),
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
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = if (verified) colors.success else colors.text_muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(text = label, color = if (verified) colors.text_primary else colors.text_muted, fontSize = 13.sp)
    }
}

@Composable
private fun create_alias_dialog(
    on_dismiss: () -> Unit,
    on_create: (String, String, String) -> Unit,
    vm: SettingsViewModel,
) {
    var local_part by remember { mutableStateOf("") }
    var display_name by remember { mutableStateOf("") }
    var selected_domain by remember { mutableStateOf("astermail.org") }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableStateOf(0) }
    var availability by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()

    LaunchedEffect(local_part, selected_domain) {
        if (local_part.length < 3) {
            availability = null
            checking = false
            return@LaunchedEffect
        }
        checking = true
        availability = null
        delay(500)
        val result = vm.check_alias_availability(local_part, selected_domain)
        availability = result
        checking = false
    }

    AlertDialog(
        onDismissRequest = on_dismiss,
        containerColor = colors.bg_card,
        title = { Text(stringResource(R.string.create_alias_dialog_title), color = colors.text_primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                OutlinedTextField(
                    value = local_part,
                    onValueChange = { local_part = it.trim().lowercase() },
                    label = { Text(stringResource(R.string.create_alias_username_label)) },
                    placeholder = { Text(stringResource(R.string.create_alias_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = when {
                        checking -> {
                            { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.text_muted, strokeWidth = 2.dp) }
                        }
                        availability == true -> {
                            { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp)) }
                        }
                        availability == false -> {
                            { Icon(Icons.Outlined.Cancel, contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp)) }
                        }
                        else -> null
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.input_bg, SquircleShape(18.dp))
                        .border(1.dp, colors.input_border, SquircleShape(18.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("astermail.org", "aster.cx").forEach { domain ->
                        val active = selected_domain == domain
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(SquircleShape(14.dp))
                                .background(if (active) colors.accent_blue else Color.Transparent)
                                .clickable { selected_domain = domain },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "@$domain",
                                color = if (active) Color.White else colors.text_muted,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
                if (local_part.isNotBlank()) {
                    Text(
                        text = "$local_part@$selected_domain",
                        color = colors.text_secondary,
                        fontSize = 12.sp,
                    )
                }
                OutlinedTextField(
                    value = display_name,
                    onValueChange = { display_name = it },
                    label = { Text(stringResource(R.string.display_name_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .clip(SquircleShape(8.dp))
                        .border(1.dp, colors.border_primary, SquircleShape(8.dp))
                        .clickable {
                            val adjectives = listOf("quick", "bright", "calm", "swift", "bold")
                            val nouns = listOf("river", "cloud", "stone", "leaf", "wave")
                            val rnd = java.util.Random()
                            local_part = "${adjectives[rnd.nextInt(adjectives.size)]}.${nouns[rnd.nextInt(nouns.size)]}${(10..99).random()}"
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
        confirmButton = {
            TextButton(
                onClick = {
                    val t = captcha_token
                    if (local_part.isNotBlank() && t != null) on_create(local_part, selected_domain, t)
                },
                enabled = local_part.isNotBlank() && captcha_token != null && availability != false,
            ) {
                Text(stringResource(R.string.create), color = colors.accent_blue)
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun add_domain_dialog(on_dismiss: () -> Unit, on_add: (String, String) -> Unit) {
    var domain_name by remember { mutableStateOf("") }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableStateOf(0) }
    val colors = AsterMaterial.colors

    AlertDialog(
        onDismissRequest = on_dismiss,
        containerColor = colors.bg_card,
        title = { Text(stringResource(R.string.add_custom_domain_dialog_title), color = colors.text_primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                OutlinedTextField(
                    value = domain_name,
                    onValueChange = { domain_name = it.trim().lowercase() },
                    label = { Text(stringResource(R.string.domain_name_label)) },
                    placeholder = { Text(stringResource(R.string.domain_placeholder)) },
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
        confirmButton = {
            TextButton(
                onClick = { val t = captcha_token; if (domain_name.isNotBlank() && t != null) on_add(domain_name, t) },
                enabled = domain_name.isNotBlank() && captcha_token != null,
            ) {
                Text(stringResource(R.string.alias_action_add), color = colors.accent_blue)
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
