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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@Composable
fun AutoForwardScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val rules = state.forwarding_rules

    LaunchedEffect(Unit) { vm.load_forwarding_rules() }

    val active_rule = rules.firstOrNull()
    var enabled by remember(active_rule) { mutableStateOf(active_rule?.enabled ?: false) }
    var target by remember(active_rule) { mutableStateOf(active_rule?.target_address ?: "") }
    var keep_copy by remember(active_rule) { mutableStateOf(active_rule?.keep_copy ?: true) }

    LaunchedEffect(state.save_status) {
        if (state.save_status == SaveStatus.SAVED) {
            if (state.forwarding_notice == null) {
                Toast.makeText(context, context.getString(R.string.auto_forward_saved), Toast.LENGTH_SHORT).show()
            }
            vm.reset_save_status()
        }
    }

    LaunchedEffect(state.forwarding_notice) {
        val notice = state.forwarding_notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
        vm.clear_forwarding_notice()
    }

    detail_scaffold(title = stringResource(R.string.auto_forward_title), on_back = on_back) {
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
        if (state.is_loading && rules.isEmpty() && active_rule == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
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
                            active_rule?.let { vm.toggle_forwarding_rule(it.id, checked) }
                        },
                    )
                }
            }
            v_gap(AsterSpacing.lg)
            AsterTextField(
                value = target,
                onValueChange = { target = it },
                label = stringResource(R.string.forward_to),
                placeholder = stringResource(R.string.forward_to_placeholder),
            )
            v_gap(AsterSpacing.md)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { keep_copy = !keep_copy }
                    .padding(vertical = AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            if (keep_copy && enabled) colors.accent_blue else Color.Transparent,
                            RoundedCornerShape(4.dp),
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (enabled) colors.border_primary else colors.text_muted,
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (keep_copy && enabled) {
                        Text("\u2713", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.size(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.keep_copy_in_inbox),
                    color = if (enabled) colors.text_primary else colors.text_muted,
                    fontSize = 14.sp,
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
                        vm.create_forwarding_rule(target, keep_copy)
                    }
                },
                enabled = target.contains("@"),
            )
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
