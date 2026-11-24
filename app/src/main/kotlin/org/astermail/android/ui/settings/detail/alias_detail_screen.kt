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
import androidx.compose.foundation.layout.width
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
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.mail.SenderAvatar
import org.astermail.android.ui.icons.pin_icon
import org.astermail.android.ui.icons.pin_icon_filled
import org.astermail.android.ui.settings.join_launched

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
    val instant_delete_locked = plan_vm.is_feature_locked("has_instant_alias_delete") && !plan_state.is_loading
    val contacts_locked = plan_vm.is_feature_locked("max_reverse_contacts_per_alias") && !plan_state.is_loading
    var menu_open by remember { mutableStateOf(false) }
    var confirm_delete by remember { mutableStateOf(false) }
    var alias_too_new_date by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.load_aliases()
        vm.load_labels()
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

    val page_title = stringResource(R.string.alias)
    val feature_locked_text = stringResource(R.string.alias_feature_locked)
    val alias = state.aliases.firstOrNull { it.id == alias_id }
    val request_delete: () -> Unit = {
        val eligible_at = alias?.let { alias_delete_eligible_at(it.created_at) }
        if (instant_delete_locked && eligible_at != null && System.currentTimeMillis() < eligible_at) {
            alias_too_new_date = format_alias_date(eligible_at)
        } else {
            confirm_delete = true
        }
    }
    val refresh_alias: suspend () -> Unit = {
        vm.join_launched {
            vm.load_aliases(force = true)
            vm.load_labels(force = true)
            vm.load_mail_rules(force = true)
            vm.load_alias_detail(alias_id, force = true)
        }
    }

    if (alias == null) {
        detail_scaffold(title = page_title, on_back = on_back, on_refresh = refresh_alias) {
            if (state.aliases_loading || (!state.aliases_loaded && state.error == null)) {
                skeleton_hero_card(lines = 2)
                v_gap(AsterSpacing.md)
                skeleton_card_list(rows = 2, trailing_width = 44.dp)
                v_gap(AsterSpacing.md)
                skeleton_card_list(rows = 3)
            } else if (state.error != null) {
                load_failed_card(message = state.error, on_retry = { vm.load_aliases(force = true) })
            } else {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    detail_row(title = stringResource(R.string.alias_detail_unavailable))
                }
            }
        }
        return
    }

    val receive_subtitle = when {
        alias.downgrade_grace_expires_at != null -> stringResource(R.string.alias_grace_upgrade_hint)
        alias.is_enabled -> stringResource(R.string.alias_receive_mail_on)
        else -> stringResource(R.string.alias_receive_mail_off)
    }

    detail_scaffold(
        title = page_title,
        on_back = on_back,
        on_refresh = refresh_alias,
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
                    aster_menu_item(
                        label = stringResource(R.string.delete_alias),
                        icon = TablerIcons.Trash,
                        destructive = true,
                        test_tag = "alias_detail_delete_menu",
                        on_click = {
                            menu_open = false
                            request_delete()
                        },
                    )
                }
            }
        },
    ) {
        alias_detail_header(
            address = alias.address,
            display_name = alias.encrypted_display_name.orEmpty(),
            profile_picture = alias.profile_picture,
            is_enabled = alias.is_enabled,
            on_copy = { copy_address(context, alias.address) },
            on_compose = { on_compose_from(alias.address) },
        )

        settings_group(title = stringResource(R.string.alias_status_section)) {
            settings_toggle_row(
                title = stringResource(R.string.alias_receive_mail),
                subtitle = receive_subtitle,
                checked = alias.is_enabled,
                enabled = alias.downgrade_grace_expires_at == null,
                test_tag = "alias_detail_enabled_toggle",
                on_change = { vm.toggle_alias(alias.id) },
            )
            settings_row_gap()
            settings_toggle_row(
                title = stringResource(R.string.alias_pin),
                subtitle = if (pin_locked) {
                    feature_locked_text
                } else {
                    stringResource(R.string.alias_pin_subtitle)
                },
                checked = alias.is_pinned && !pin_locked,
                test_tag = "alias_detail_pin_toggle",
                on_change = { if (pin_locked) on_open("billing") else vm.toggle_alias_pin(alias.id) },
            )
        }

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
            contacts_locked = contacts_locked,
        )

        settings_group(title = stringResource(R.string.alias_danger_zone)) {
            detail_row(
                title = stringResource(R.string.delete_alias),
                subtitle = stringResource(R.string.alias_delete_row_subtitle),
                icon = TablerIcons.Trash,
                icon_tint = colors.danger,
                on_click = request_delete,
            )
        }

        v_gap(AsterSpacing.xl)
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
                    onClick = { alias_too_new_date = null; on_open("billing") },
                )
            },
        )
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

@Composable
private fun alias_detail_header(
    address: String,
    display_name: String,
    profile_picture: String?,
    is_enabled: Boolean,
    on_copy: () -> Unit,
    on_compose: () -> Unit,
) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.lg, end = AsterSpacing.xs, top = AsterSpacing.lg, bottom = AsterSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(
                    email = address,
                    name = display_name,
                    size = 48.dp,
                    profile_picture_url = profile_picture,
                    use_contact_photo = false,
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    alias_address_text(
                        address = address,
                        font_size = 17.sp,
                        font_weight = FontWeight.SemiBold,
                        test_tag = "alias_detail_address",
                    )
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                    ) {
                        tone_badge(
                            text = if (is_enabled) {
                                stringResource(R.string.alias_status_active)
                            } else {
                                stringResource(R.string.alias_status_disabled_badge)
                            },
                            tone = if (is_enabled) colors.success else colors.text_tertiary,
                            modifier = Modifier.testTag("alias_detail_status"),
                        )
                        if (display_name.isNotBlank()) {
                            Text(
                                text = display_name,
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
                AsterIconButton(
                    icon = TablerIcons.Copy,
                    content_description = stringResource(R.string.copy_address),
                    onClick = on_copy,
                    modifier = Modifier.testTag("alias_detail_copy_button"),
                )
            }
            AsterButton(
                label = stringResource(R.string.new_message),
                onClick = on_compose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = AsterSpacing.md)
                    .testTag("alias_detail_compose"),
            )
        }
    }
}
