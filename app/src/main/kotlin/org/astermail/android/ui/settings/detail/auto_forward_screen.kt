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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.settings.SaveStatus
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.settings.join_launched

@Composable
fun AutoForwardScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val rules = state.forwarding_rules

    LaunchedEffect(Unit) { vm.load_forwarding_rules() }

    val active_rule = rules.firstOrNull()
    val active_rule_id = active_rule?.id
    var enabled by remember(active_rule_id) { mutableStateOf(active_rule?.enabled ?: true) }
    var target by remember(active_rule_id) { mutableStateOf(active_rule?.target_address ?: "") }
    var keep_copy by remember(active_rule_id) { mutableStateOf(active_rule?.keep_copy ?: true) }

    LaunchedEffect(state.save_status) {
        if (state.save_status == SaveStatus.SAVED) {
            if (state.forwarding_notice == null) {
                org.astermail.android.ui.common.app_toast.show(context.getString(R.string.auto_forward_saved).toString())
            }
            vm.reset_save_status()
        }
    }

    LaunchedEffect(state.save_status) {
        if (state.save_status == SaveStatus.ERROR) {
            val message = state.error ?: context.getString(R.string.settings_save_failed_banner)
            active_rule?.let { enabled = it.enabled }
            org.astermail.android.ui.common.app_toast.show(org.astermail.android.ui.common.TopToastState(message = message.toString(), duration_ms = org.astermail.android.ui.common.toast_action_duration_ms))
            vm.reset_transient_state()
        }
    }

    LaunchedEffect(state.forwarding_notice) {
        val notice = state.forwarding_notice ?: return@LaunchedEffect
        org.astermail.android.ui.common.app_toast.show(org.astermail.android.ui.common.TopToastState(message = notice.toString(), duration_ms = org.astermail.android.ui.common.toast_action_duration_ms))
        vm.clear_forwarding_notice()
    }

    val forward_dirty = enabled != (active_rule?.enabled ?: true) ||
        target != (active_rule?.target_address ?: "") ||
        keep_copy != (active_rule?.keep_copy ?: true)
    detail_scaffold(
        title = stringResource(R.string.auto_forward_title),
        on_back = on_back,
        on_refresh = { vm.join_launched { vm.load_forwarding_rules() } },
        refresh_enabled = !forward_dirty && state.save_status != SaveStatus.SAVING,
    ) {
        if (plan_vm.is_feature_locked("has_auto_forwarding") && !plan_state.is_loading) {
            UpgradeGate(
                title = stringResource(R.string.auto_forward_title),
                description = stringResource(R.string.auto_forward_description),
                plan_name = "Star",
                on_upgrade = { on_open("billing") },
                requires_label = stringResource(R.string.requires_plan, "Star"),
                button_label = stringResource(R.string.upgrade),
            )
            return@detail_scaffold
        }
        state.forwarding_held?.let {
            org.astermail.android.guard.GuardHeldBanner(it)
            v_gap(AsterSpacing.md)
        }
        if (state.forwarding_rules_load_failed && rules.isEmpty()) {
            load_failed_card(state.error) { vm.load_forwarding_rules() }
            return@detail_scaffold
        }
        if (!state.forwarding_rules_loaded && rules.isEmpty()) {
            auto_forward_skeleton()
        } else {
            if (active_rule != null) {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AsterSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.enable_auto_forward),
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.all_mail_forwarded),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                            )
                        }
                        AsterSwitch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                enabled = checked
                                vm.toggle_forwarding_rule(active_rule.id, checked)
                            },
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.auto_forward_setup_hint),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
            v_gap(AsterSpacing.lg)
            AsterTextField(
                value = target,
                onValueChange = { target = it },
                label = stringResource(R.string.forward_to),
                placeholder = stringResource(R.string.forward_to_placeholder),
            )
            v_gap(AsterSpacing.md)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                settings_toggle_row(
                    title = stringResource(R.string.keep_copy_in_inbox),
                    checked = keep_copy,
                    test_tag = "auto_forward_keep_copy",
                    on_change = { keep_copy = it },
                )
            }
            v_gap(AsterSpacing.lg)
            AsterButton(
                label = if (active_rule != null) stringResource(R.string.save) else stringResource(R.string.create_rule),
                onClick = {
                    val rule = active_rule
                    if (rule != null) {
                        vm.update_forwarding_rule(rule.id, target, keep_copy)
                    } else {
                        vm.create_forwarding_rule(target, keep_copy, true)
                    }
                },
                enabled = target.contains("@") && state.save_status != SaveStatus.SAVING,
            )
            val failing_rule = active_rule?.takeIf { it.is_failing }
            if (failing_rule != null) {
                v_gap(AsterSpacing.lg)
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg)) {
                        Text(
                            text = stringResource(R.string.forwarding_failed_badge),
                            color = colors.danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        v_gap(AsterSpacing.sm)
                        Text(
                            text = if (failing_rule.encryption_blocked) {
                                stringResource(R.string.forwarding_failed_encryption, failing_rule.failing_address)
                            } else {
                                stringResource(
                                    R.string.forwarding_failed_generic,
                                    failing_rule.failing_address,
                                    failing_rule.last_error.orEmpty(),
                                )
                            },
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                        if (failing_rule.failed_count > 0) {
                            v_gap(AsterSpacing.sm)
                            Text(
                                text = stringResource(R.string.forwarding_failed_count, failing_rule.failed_count.toInt()),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            val pending = active_rule?.pending_destinations.orEmpty()
            if (pending.isNotEmpty()) {
                v_gap(AsterSpacing.lg)
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg)) {
                        Text(
                            text = stringResource(R.string.forwarding_pending_verification),
                            color = colors.warning,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        pending.forEach { destination ->
                            v_gap(AsterSpacing.sm)
                            Text(
                                text = stringResource(
                                    R.string.forwarding_awaiting_verification,
                                    destination.address,
                                ),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                            )
                            v_gap(AsterSpacing.sm)
                            AsterButton(
                                label = stringResource(R.string.resend_verification_email),
                                onClick = {
                                    active_rule?.let {
                                        vm.resend_forwarding_confirmation(it.id, destination.address)
                                    }
                                },
                                enabled = state.forwarding_resending_address == null,
                                is_loading = state.forwarding_resending_address == destination.address,
                            )
                        }
                    }
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun auto_forward_skeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        skeleton_hero_card(lines = 1)
        v_gap(AsterSpacing.lg)
        skeleton_hero_card(lines = 0)
        v_gap(AsterSpacing.md)
        skeleton_card_list(rows = 1)
    }
}
