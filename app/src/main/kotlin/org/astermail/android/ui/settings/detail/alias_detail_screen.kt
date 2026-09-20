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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Copy
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Trash
import org.astermail.android.R
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.icons.pin_icon
import org.astermail.android.ui.icons.pin_icon_filled

@Composable
fun alias_detail_screen(
    alias_id: String,
    on_back: () -> Unit,
    on_open: (String) -> Unit = {},
    on_open_alias_mail: (id: String, address: String, routing_token: String) -> Unit = { _, _, _ -> },
    on_compose_from: (String) -> Unit = {},
) {
    val vm = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val avatars_locked = plan_vm.is_feature_locked("has_alias_avatars") && !plan_state.is_loading
    val pin_locked = plan_vm.is_feature_locked("has_advanced_aliases") && !plan_state.is_loading
    var menu_open by remember { mutableStateOf(false) }
    var confirm_delete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.load_aliases()
        vm.load_labels(folder_type = "folder")
        vm.load_mail_rules()
    }

    LaunchedEffect(alias_id) {
        vm.load_alias_detail(alias_id)
    }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val alias = state.aliases.firstOrNull { it.id == alias_id }

    if (alias == null) {
        detail_scaffold(title = stringResource(R.string.aliases), on_back = on_back) {
            if (state.aliases_loading) {
                skeleton_card_list(rows = 4)
            } else {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    detail_row(title = stringResource(R.string.alias_detail_unavailable))
                }
            }
        }
        return
    }

    detail_scaffold(
        title = alias.address,
        on_back = on_back,
        trailing = {
            Box {
                AsterIconButton(
                    icon = TablerIcons.DotsVertical,
                    content_description = stringResource(R.string.more_options),
                    onClick = { menu_open = true },
                    modifier = Modifier.testTag("alias_detail_overflow"),
                )
                aster_menu(expanded = menu_open, on_dismiss = { menu_open = false }) {
                    aster_menu_item(
                        label = stringResource(R.string.copy_address),
                        icon = TablerIcons.Copy,
                        test_tag = "alias_detail_copy",
                        on_click = {
                            menu_open = false
                            copy_address(context, alias.address)
                        },
                    )
                    aster_menu_item(
                        label = if (alias.is_pinned) {
                            stringResource(R.string.alias_unpin)
                        } else {
                            stringResource(R.string.alias_pin)
                        },
                        icon = if (alias.is_pinned) pin_icon_filled else pin_icon,
                        test_tag = "alias_detail_pin",
                        on_click = {
                            menu_open = false
                            if (pin_locked) on_open("billing") else vm.toggle_alias_pin(alias.id)
                        },
                    )
                }
            }
        },
    ) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alias.address,
                            color = colors.text_primary,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("alias_detail_address"),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (alias.is_enabled) {
                                stringResource(R.string.alias_status_active)
                            } else {
                                stringResource(R.string.alias_status_disabled_badge)
                            },
                            color = if (alias.is_enabled) colors.text_tertiary else colors.text_muted,
                            fontSize = 12.sp,
                        )
                    }
                    AsterSwitch(
                        checked = alias.is_enabled,
                        enabled = alias.downgrade_grace_expires_at == null,
                        onCheckedChange = { vm.toggle_alias(alias.id) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    AsterButton(
                        label = stringResource(R.string.new_message),
                        onClick = { on_compose_from(alias.address) },
                        modifier = Modifier.weight(1f).testTag("alias_detail_compose"),
                    )
                    AsterSecondaryButton(
                        label = stringResource(R.string.copy_address),
                        onClick = { copy_address(context, alias.address) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        v_gap(AsterSpacing.md)

        alias_detail_panel(
            alias = alias,
            detail = state.alias_details[alias.id] ?: AliasDetailState(),
            vm = vm,
            rule_delivery = alias_rule_delivery_note(alias, state.mail_rules, state.labels),
            rule_label = alias_rule_label_note(alias, state.mail_rules, state.tags),
            on_view_sent = if (alias.alias_address_hash.isNotBlank()) {
                { on_open_alias_mail(alias.id, alias.address, alias.alias_address_hash) }
            } else {
                null
            },
            avatars_locked = avatars_locked,
        )

        v_gap(AsterSpacing.md)

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.delete_alias),
                icon = TablerIcons.Trash,
                icon_tint = colors.danger,
                on_click = { confirm_delete = true },
            )
        }

        v_gap(AsterSpacing.lg)
    }

    if (confirm_delete) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { confirm_delete = false },
            title = stringResource(R.string.delete_alias),
            message = stringResource(R.string.alias_delete_confirm_message, alias.address),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirm_delete = false },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.delete),
                    onClick = {
                        confirm_delete = false
                        vm.delete_alias(alias.id)
                        on_back()
                    },
                )
            },
        )
    }
}
