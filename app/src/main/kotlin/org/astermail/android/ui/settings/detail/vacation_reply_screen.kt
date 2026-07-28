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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.vacation.VacationReplyViewModel

@Composable
fun VacationReplyScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val vm: VacationReplyViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    detail_scaffold(title = stringResource(R.string.vacation_reply), on_back = on_back) {
        if (plan_vm.is_feature_locked("has_vacation_reply") && !plan_state.is_loading) {
            UpgradeGate(
                title = stringResource(R.string.vacation_reply),
                description = stringResource(R.string.vacation_reply_description),
                plan_name = "Star",
                on_upgrade = { on_open("billing") },
                requires_label = stringResource(R.string.requires_plan, "Star"),
                button_label = stringResource(R.string.upgrade),
            )
            return@detail_scaffold
        }
        if (state.is_loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
            return@detail_scaffold
        }

        state.error?.let { err ->
            error_banner(err)
            v_gap(AsterSpacing.md)
        }

        state.saved_message?.let { msg ->
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = msg,
                    color = colors.success,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(AsterSpacing.md),
                )
            }
            v_gap(AsterSpacing.md)
        }

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.is_enabled) stringResource(R.string.vacation_active) else stringResource(R.string.vacation_off),
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (state.is_enabled) {
                            stringResource(R.string.vacation_active_subtitle)
                        } else {
                            stringResource(R.string.vacation_off_subtitle)
                        },
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                }
                AsterSwitch(
                    checked = state.is_enabled,
                    onCheckedChange = { vm.set_enabled(it) },
                    modifier = Modifier.testTag("vacation_toggle"),
                )
            }
        }
        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.vacation_subject))
        AsterTextField(
            value = state.subject,
            onValueChange = { vm.update_subject(it) },
            placeholder = stringResource(R.string.vacation_subject_placeholder),
        )
        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.vacation_reply_body))
        AsterTextField(
            value = state.body,
            onValueChange = { vm.update_body(it) },
            placeholder = stringResource(R.string.vacation_reply_placeholder),
            singleLine = false,
        )
        v_gap(AsterSpacing.lg)

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.external_senders_only),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.external_senders_subtitle),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                }
                AsterSwitch(
                    checked = state.external_only,
                    onCheckedChange = { vm.set_external_only(it) },
                )
            }
        }
        v_gap(AsterSpacing.lg)

        AnimatedVisibility(
            visible = state.exists && state.reply_count > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.replies_sent_count, state.reply_count, if (state.reply_count == 1) stringResource(R.string.reply_singular) else stringResource(R.string.replies_plural)),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
                v_gap(AsterSpacing.md)
            }
        }

        AsterButton(
            label = if (state.is_busy) stringResource(R.string.saving) else stringResource(R.string.save),
            onClick = { vm.save() },
        )
        AnimatedVisibility(
            visible = state.exists,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                v_gap(AsterSpacing.sm)
                AsterDestructiveButton(label = stringResource(R.string.delete_vacation_reply), onClick = { vm.delete() })
            }
        }
        Spacer(Modifier.size(AsterSpacing.xxl))
    }
}
