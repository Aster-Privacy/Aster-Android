//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import android.content.ClipData
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Download
import compose.icons.tablericons.Printer
import org.astermail.android.R
import org.astermail.android.auth.request_passkey_json
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.crypto.guard.GuardCode
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.aster_shared_axis_slide_px
import org.astermail.android.design.aster_shared_axis_x
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.guard.guard_passkey_failure
import org.astermail.android.guard.guard_passkey_sheet
import org.astermail.android.guard.guard_prompt_text
import org.astermail.android.guard.resolve
import org.astermail.android.ui.common.page_surface
import org.astermail.android.ui.common.print_recovery_codes
import org.astermail.android.ui.common.secure_screen
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import org.astermail.android.ui.settings.detail.detail_row
import org.astermail.android.ui.settings.detail.error_banner
import org.astermail.android.ui.settings.detail.settings_row_gap

private const val RESCUE_FILE_NAME = "aster-guard-rescue-code.txt"

@Composable
fun guard_setup_host(view_model: GuardSetupViewModel, on_back: () -> Unit) {
    val state by view_model.state.collectAsState()
    val context = LocalContext.current
    val activity = find_fragment_activity(context)
    val setup_prompt = guard_prompt_text(
        title = stringResource(R.string.guard_prompt_title),
        subtitle = stringResource(R.string.guard_setup_prompt_subtitle),
    )
    val join_prompt = guard_prompt_text(
        title = stringResource(R.string.guard_prompt_title),
        subtitle = stringResource(R.string.guard_join_prompt_subtitle),
    )
    guard_setup_screen(
        state = state,
        on_start = view_model::to_choose,
        on_toggle = view_model::toggle,
        on_toggle_scope = view_model::toggle_scope,
        on_to_rescue = view_model::to_rescue,
        on_save_file = { guard_save_rescue(context, state.rescue_code) },
        on_print = { guard_print_rescue(context, state.rescue_code) },
        on_copy = { guard_copy_rescue(context, state.rescue_code) },
        on_to_confirm = view_model::to_confirm_rescue,
        on_confirm_input = view_model::on_confirm_input,
        on_confirm = { view_model.confirm_rescue() },
        on_password = view_model::on_password,
        on_totp = view_model::on_totp,
        on_submit = { view_model.submit(guard_prompt_signer(activity, setup_prompt)) },
        on_passkey = { view_model.submit_passkey(guard_prompt_signer(activity, setup_prompt), guard_passkey_sheet_for(context)) },
        on_join = { view_model.start_join(guard_prompt_signer(activity, join_prompt)) },
        on_restart_join = view_model::restart_join,
        on_retry = view_model::retry,
        on_step_back = {
            when (state.step) {
                guard_setup_step.choose, guard_setup_step.rescue, guard_setup_step.confirm_rescue, guard_setup_step.password ->
                    view_model.back()
                else -> on_back()
            }
        },
        on_close = on_back,
    )
}

internal fun guard_passkey_sheet_for(context: Context): guard_passkey_sheet = guard_passkey_sheet { request_json ->
    request_passkey_json(find_fragment_activity(context) ?: context, request_json)
}

internal fun guard_save_rescue(context: Context, code: String) {
    if (code.isEmpty()) return
    org.astermail.android.util.save_recovery_codes_file(context, RESCUE_FILE_NAME, listOf(GuardCode.format_rescue(code)))
}

internal fun guard_print_rescue(context: Context, code: String) {
    if (code.isEmpty()) return
    print_recovery_codes(context, "", listOf(GuardCode.format_rescue(code)), on_failure = { show_copy_failed_toast(context) })
}

internal fun guard_copy_rescue(context: Context, code: String) {
    if (code.isEmpty()) return
    val clip = ClipData.newPlainText(context.getString(R.string.guard_rescue_label), GuardCode.format_rescue(code))
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    if (write_to_clipboard(context, clip)) {
        org.astermail.android.util.schedule_sensitive_clipboard_clear(context)
    } else {
        show_copy_failed_toast(context)
    }
}

@Composable
fun guard_setup_screen(
    state: guard_setup_ui,
    on_start: () -> Unit,
    on_toggle: (Int) -> Unit,
    on_toggle_scope: (Int) -> Unit,
    on_to_rescue: () -> Unit,
    on_save_file: () -> Unit,
    on_print: () -> Unit,
    on_copy: () -> Unit,
    on_to_confirm: () -> Unit,
    on_confirm_input: (String) -> Unit,
    on_confirm: () -> Unit,
    on_password: (String) -> Unit,
    on_totp: (String) -> Unit,
    on_submit: () -> Unit,
    on_passkey: () -> Unit = {},
    on_join: () -> Unit,
    on_restart_join: () -> Unit,
    on_retry: () -> Unit,
    on_step_back: () -> Unit,
    on_close: () -> Unit,
) {
    secure_screen()
    val colors = AsterMaterial.colors
    BackHandler { on_step_back() }
    val close: (() -> Unit)? = if (guard_setup_can_close(state.step)) on_close else null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors),
    ) {
        val reduce_motion = aster_reduce_motion()
        val slide_px = aster_shared_axis_slide_px()
        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                aster_shared_axis_x(
                    forward = targetState.ordinal > initialState.ordinal,
                    slide_px = slide_px,
                    reduce_motion = reduce_motion,
                )
            },
            contentKey = { if (it == guard_setup_step.working) guard_setup_step.loading else it },
            label = "guard_setup_step",
        ) { step ->
        when (step) {
            guard_setup_step.loading, guard_setup_step.working -> guard_wizard_result {
                guard_wordmark()
                Spacer(Modifier.height(AsterSpacing.xxl + AsterSpacing.sm))
                guard_wizard_body(stringResource(R.string.guard_setup_working), Modifier.fillMaxWidth(), TextAlign.Center)
                state.phase?.let { current ->
                    Spacer(Modifier.height(AsterSpacing.xl))
                    guard_setup_phase_list(current)
                }
            }
            guard_setup_step.hidden -> guard_wizard_result {
                guard_result_block(
                    title = stringResource(R.string.guard_setup_title),
                    body = stringResource(R.string.guard_setup_unavailable),
                    primary_label = stringResource(R.string.done),
                    on_primary = on_close,
                )
            }
            guard_setup_step.already_on -> guard_wizard_result {
                guard_result_block(
                    title = stringResource(R.string.guard_setup_already_on_title),
                    body = stringResource(R.string.guard_setup_already_on_body),
                    primary_label = stringResource(R.string.done),
                    on_primary = on_close,
                )
            }
            guard_setup_step.failed -> guard_wizard_result {
                guard_result_block(
                    title = stringResource(R.string.guard_setup_failed_title),
                    body = state.error?.resolve() ?: stringResource(R.string.guard_setup_failed),
                    primary_label = stringResource(R.string.guard_setup_retry),
                    on_primary = on_retry,
                    secondary_label = stringResource(R.string.close),
                    on_secondary = on_close,
                )
            }
            guard_setup_step.intro -> guard_wizard_scaffold(
                step = guard_setup_step_number(step),
                title = stringResource(R.string.guard_setup_intro_title),
                body = stringResource(R.string.guard_intro_lead),
                on_back = on_close,
                back_closes = true,
                on_close = close,
                bottom = {
                    Text(
                        text = stringResource(R.string.guard_intro_note),
                        color = colors.text_tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AsterButton(
                        label = stringResource(R.string.guard_setup_continue),
                        onClick = on_start,
                        modifier = Modifier.fillMaxWidth().testTag("guard_setup_start"),
                    )
                    AsterGhostButton(
                        label = stringResource(R.string.guard_intro_not_now),
                        onClick = on_close,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            ) {
                guard_intro_features()
            }
            guard_setup_step.choose -> guard_wizard_scaffold(
                step = guard_setup_step_number(step),
                title = stringResource(R.string.guard_setup_choose_title),
                body = stringResource(R.string.guard_setup_choose_body),
                on_back = on_step_back,
                on_close = close,
                bottom = {
                    AsterButton(
                        label = stringResource(R.string.guard_setup_continue),
                        onClick = on_to_rescue,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.can_continue_choose,
                    )
                },
            ) {
                val hold_on = state.switches and GuardSwitch.HOLD_CHANGES != 0
                guard_card {
                    guard_protections_list(
                        switches = state.switches,
                        scopes = state.scopes,
                        on_toggle = on_toggle,
                        on_toggle_scope = on_toggle_scope,
                        scope_enabled = { hold_on },
                    )
                }
            }
            guard_setup_step.rescue -> guard_wizard_scaffold(
                step = guard_setup_step_number(step),
                title = stringResource(R.string.guard_setup_rescue_title),
                body = stringResource(R.string.guard_setup_rescue_body),
                on_back = on_step_back,
                on_close = close,
                bottom = {
                    AsterButton(
                        label = stringResource(R.string.guard_setup_continue),
                        onClick = on_to_confirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            ) {
                guard_setup_rescue(state.rescue_code, on_save_file, on_print, on_copy)
            }
            guard_setup_step.confirm_rescue -> guard_wizard_scaffold(
                step = guard_setup_step_number(step),
                title = stringResource(R.string.guard_setup_confirm_title),
                body = stringResource(R.string.guard_setup_confirm_body),
                on_back = on_step_back,
                on_close = close,
                bottom = {
                    AsterButton(
                        label = stringResource(R.string.guard_setup_continue),
                        onClick = on_confirm,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.confirm_input.isNotBlank(),
                    )
                },
            ) {
                AsterTextField(
                    value = state.confirm_input,
                    onValueChange = on_confirm_input,
                    modifier = Modifier.fillMaxWidth().testTag("guard_confirm_field"),
                    label = stringResource(R.string.guard_setup_confirm_label),
                    error_text = if (state.notice == guard_setup_notice.wrong_group) {
                        stringResource(R.string.guard_setup_confirm_wrong)
                    } else {
                        null
                    },
                    keyboard_options = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    text_style = guard_code_text_style,
                )
            }
            guard_setup_step.password -> guard_wizard_scaffold(
                step = guard_setup_step_number(step),
                title = stringResource(R.string.guard_setup_password_title),
                body = stringResource(R.string.guard_setup_password_body),
                on_back = on_step_back,
                on_close = close,
                bottom = {
                    AsterButton(
                        label = stringResource(R.string.guard_setup_turn_on),
                        onClick = on_submit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.can_submit,
                    )
                    AsterGhostButton(
                        label = stringResource(R.string.guard_passkey_use),
                        onClick = on_passkey,
                        modifier = Modifier.fillMaxWidth().testTag("guard_setup_passkey"),
                        enabled = state.can_use_passkey,
                    )
                },
            ) {
                AsterTextField(
                    value = state.password,
                    onValueChange = on_password,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.guard_setup_password_label),
                    error_text = guard_setup_notice_text(state) ?: guard_passkey_failure_text(state.passkey_failure),
                    keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visual_transformation = PasswordVisualTransformation(),
                )
                if (state.show_totp) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    AsterTextField(
                        value = state.totp,
                        onValueChange = on_totp,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.guard_setup_totp_label),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            }
            guard_setup_step.done -> guard_wizard_result {
                guard_result_block(
                    title = stringResource(R.string.guard_setup_done_title),
                    body = stringResource(R.string.guard_setup_done_body),
                    primary_label = stringResource(R.string.done),
                    on_primary = on_close,
                )
            }
            guard_setup_step.join_intro -> guard_wizard_scaffold(
                step = 0,
                title = stringResource(R.string.guard_join_title),
                body = stringResource(R.string.guard_join_body),
                on_back = on_close,
                back_closes = true,
                on_close = close,
                bottom = {
                    AsterButton(
                        label = stringResource(R.string.guard_join_action),
                        onClick = on_join,
                        modifier = Modifier.fillMaxWidth().testTag("guard_join_action"),
                    )
                },
            ) {
                guard_setup_notice_text(state)?.let { error_banner(it) }
            }
            guard_setup_step.join_waiting -> guard_wizard_scaffold(
                step = 0,
                title = stringResource(R.string.guard_join_waiting_title),
                body = stringResource(R.string.guard_join_waiting_body),
                on_back = on_close,
                back_closes = true,
                on_close = close,
                centered = true,
            ) {
                state.join_number?.let { number ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        guard_number_tiles(number, stringResource(R.string.guard_join_number_description, number.toString()))
                    }
                }
            }
            guard_setup_step.join_done -> guard_wizard_result {
                guard_result_block(
                    title = stringResource(R.string.guard_join_done_title),
                    body = stringResource(R.string.guard_join_done_body),
                    primary_label = stringResource(R.string.done),
                    on_primary = on_close,
                )
            }
            guard_setup_step.join_denied, guard_setup_step.join_expired -> guard_wizard_result {
                val denied = step == guard_setup_step.join_denied
                guard_result_block(
                    title = stringResource(if (denied) R.string.guard_join_denied_title else R.string.guard_join_expired_title),
                    body = stringResource(if (denied) R.string.guard_join_denied else R.string.guard_join_expired),
                    primary_label = stringResource(R.string.guard_join_try_again),
                    on_primary = on_restart_join,
                    secondary_label = stringResource(R.string.close),
                    on_secondary = on_close,
                )
            }
        }
        }
    }
}

internal val guard_code_text_style = TextStyle(
    fontFamily = FontFamily.Monospace,
    letterSpacing = 2.sp,
    fontWeight = FontWeight.Medium,
)

@Composable
internal fun guard_setup_rescue(
    rescue_code: String,
    on_save_file: () -> Unit,
    on_print: () -> Unit,
    on_copy: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val groups = rescue_code.chunked(5)
    val description = stringResource(R.string.guard_setup_rescue_description, rescue_code.toCharArray().joinToString(" "))
    guard_card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl)
                .clearAndSetSemantics { contentDescription = description }
                .testTag("guard_rescue_code"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            groups.chunked(3).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.lg)) {
                    line.forEach { group ->
                        Text(
                            text = group,
                            color = colors.text_primary,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(AsterSpacing.lg))
    guard_card {
        guard_rescue_action(TablerIcons.Download, R.string.guard_setup_save_file, on_save_file)
        settings_row_gap()
        guard_rescue_action(TablerIcons.Printer, R.string.guard_setup_print, on_print)
        settings_row_gap()
        guard_rescue_action(TablerIcons.Copy, R.string.guard_setup_copy, on_copy)
    }
}

@Composable
private fun guard_rescue_action(icon: ImageVector, label: Int, on_click: () -> Unit) {
    detail_row(
        title = stringResource(label),
        icon = icon,
        on_click = on_click,
    )
}

@Composable
internal fun guard_passkey_failure_text(failure: guard_passkey_failure?): String? = when (failure) {
    null, guard_passkey_failure.canceled -> null
    guard_passkey_failure.interrupted -> stringResource(R.string.guard_passkey_interrupted)
    guard_passkey_failure.no_passkey -> stringResource(R.string.guard_passkey_none)
    guard_passkey_failure.unavailable -> stringResource(R.string.guard_passkey_unavailable)
    guard_passkey_failure.wrong_account -> stringResource(R.string.guard_passkey_wrong_account)
    guard_passkey_failure.account_changed -> stringResource(R.string.guard_passkey_account_changed)
    guard_passkey_failure.expired -> stringResource(R.string.guard_passkey_expired)
    guard_passkey_failure.failed -> stringResource(R.string.guard_passkey_failed)
}

@Composable
private fun guard_setup_phase_list(current: guard_setup_phase) {
    val colors = AsterMaterial.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        guard_setup_phase.entries.forEach { phase ->
            val done = phase.ordinal < current.ordinal
            val active = phase == current
            Text(
                text = stringResource(
                    when {
                        done -> R.string.guard_setup_phase_done
                        active -> R.string.guard_setup_phase_active
                        else -> R.string.guard_setup_phase_pending
                    },
                    stringResource(guard_setup_phase_label(phase)),
                ),
                color = if (active) colors.text_primary else colors.text_tertiary,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun guard_setup_phase_label(phase: guard_setup_phase): Int = when (phase) {
    guard_setup_phase.keys -> R.string.guard_setup_phase_keys
    guard_setup_phase.confirm -> R.string.guard_setup_phase_confirm
    guard_setup_phase.register -> R.string.guard_setup_phase_register
}

internal fun guard_setup_phase_failure(phase: guard_setup_phase?): Int = when (phase) {
    guard_setup_phase.keys -> R.string.guard_setup_failed_keys
    guard_setup_phase.confirm -> R.string.guard_setup_failed_confirm
    guard_setup_phase.register -> R.string.guard_setup_failed_register
    null -> R.string.guard_setup_failed
}

@Composable
private fun guard_setup_notice_text(state: guard_setup_ui): String? {
    state.error?.takeIf { it.step_up_field != null && state.notice != null }?.let { return it.resolve() }
    if (state.notice != guard_setup_notice.failed) return guard_setup_notice_text(state.notice)
    val lead = stringResource(guard_setup_phase_failure(state.failed_phase))
    val detail = state.error?.resolve()
    return if (state.failed_phase != null && detail != null) "$lead $detail" else detail ?: lead
}

@Composable
private fun guard_setup_notice_text(notice: guard_setup_notice?): String? = when (notice) {
    guard_setup_notice.wrong_password -> stringResource(R.string.guard_setup_wrong_password)
    guard_setup_notice.totp_required -> stringResource(R.string.guard_setup_totp_required)
    guard_setup_notice.canceled -> stringResource(R.string.guard_setup_canceled)
    guard_setup_notice.no_lock_screen -> stringResource(R.string.guard_setup_no_lock_screen)
    guard_setup_notice.failed -> stringResource(R.string.guard_setup_failed)
    guard_setup_notice.wrong_group, null -> null
}
