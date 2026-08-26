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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
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
import org.astermail.android.ui.common.picker_theme_res
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

    var confirm_delete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    if (confirm_delete) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { confirm_delete = false },
            title = stringResource(R.string.delete_vacation_reply),
            message = stringResource(R.string.delete_vacation_reply_confirm),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                confirm_delete = false
                vm.delete()
            },
        )
    }

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

        if (state.load_failed) {
            load_failed_card(state.error) { vm.load() }
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
                    enabled = !state.is_busy,
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

        section_label(stringResource(R.string.vacation_start_date))
        vacation_date_row(
            value = state.start_date,
            max_date = state.end_date,
            on_pick = { vm.set_start_date(it) },
            on_clear = { vm.set_start_date(null) },
        )
        v_gap(AsterSpacing.md)

        section_label(stringResource(R.string.vacation_end_date))
        vacation_date_row(
            value = state.end_date,
            min_date = state.start_date,
            on_pick = { vm.set_end_date(it) },
            on_clear = { vm.set_end_date(null) },
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
                    text = pluralStringResource(R.plurals.replies_sent_count, state.reply_count, state.reply_count),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
                v_gap(AsterSpacing.md)
            }
        }

        AsterButton(
            label = if (state.is_busy) stringResource(R.string.saving) else stringResource(R.string.save),
            onClick = { vm.save() },
            enabled = !state.is_busy,
        )
        AnimatedVisibility(
            visible = state.exists,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                v_gap(AsterSpacing.sm)
                AsterDestructiveButton(
                    label = stringResource(R.string.delete_vacation_reply),
                    onClick = { confirm_delete = true },
                    enabled = !state.is_busy,
                )
            }
        }
        Spacer(Modifier.size(AsterSpacing.xxl))
    }
}


@Composable
private fun vacation_date_row(
    value: String?,
    on_pick: (String) -> Unit,
    on_clear: () -> Unit,
    min_date: String? = null,
    max_date: String? = null,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val theme_res = picker_theme_res()
    val label = value?.let { format_vacation_date(it) } ?: stringResource(R.string.vacation_date_not_set)

    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val cal = org.astermail.android.ui.mail.AsterTimePreferences.account_calendar()
                    parse_vacation_date(value)?.let { parsed ->
                        cal.set(java.util.Calendar.YEAR, parsed[0])
                        cal.set(java.util.Calendar.MONTH, parsed[1] - 1)
                        cal.set(java.util.Calendar.DAY_OF_MONTH, parsed[2])
                    }
                    val dialog = android.app.DatePickerDialog(
                        context,
                        theme_res,
                        { _, year, month, day ->
                            val m = if (month + 1 < 10) "0${month + 1}" else "${month + 1}"
                            val d = if (day < 10) "0$day" else "$day"
                            on_pick("$year-$m-$d")
                        },
                        cal.get(java.util.Calendar.YEAR),
                        cal.get(java.util.Calendar.MONTH),
                        cal.get(java.util.Calendar.DAY_OF_MONTH),
                    )

                    vacation_bound_millis(min_date, false)?.let {
                        dialog.datePicker.minDate = it
                    }
                    vacation_bound_millis(max_date, true)?.let {
                        dialog.datePicker.maxDate = it
                    }
                    dialog.show()
                }
                .padding(AsterSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (value == null) colors.text_tertiary else colors.text_primary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = stringResource(R.string.clear),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { on_clear() },
                )
            }
        }
    }
}

private fun vacation_bound_millis(value: String?, end_of_day: Boolean): Long? {
    val parsed = parse_vacation_date(value) ?: return null
    val cal = java.util.Calendar.getInstance()

    cal.set(java.util.Calendar.YEAR, parsed[0])
    cal.set(java.util.Calendar.MONTH, parsed[1] - 1)
    cal.set(java.util.Calendar.DAY_OF_MONTH, parsed[2])
    cal.set(java.util.Calendar.HOUR_OF_DAY, if (end_of_day) 23 else 0)
    cal.set(java.util.Calendar.MINUTE, if (end_of_day) 59 else 0)
    cal.set(java.util.Calendar.SECOND, if (end_of_day) 59 else 0)
    cal.set(java.util.Calendar.MILLISECOND, if (end_of_day) 999 else 0)

    return cal.timeInMillis
}

private fun parse_vacation_date(value: String?): IntArray? {
    val parts = value?.split("-") ?: return null
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null

    return intArrayOf(year, month, day)
}

private fun format_vacation_date(value: String): String {
    val parts = parse_vacation_date(value) ?: return value

    return runCatching {
        java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withZone(org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id())
            .format(
                java.time.LocalDate.of(parts[0], parts[1], parts[2])
                    .atStartOfDay(org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id())
                    .toInstant(),
            )
    }.getOrDefault(value)
}
