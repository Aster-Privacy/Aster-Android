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

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.astermail.android.R
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.guard.GuardHeldBanner
import org.astermail.android.guard.guard_prompt_text
import org.astermail.android.ui.common.secure_screen
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.astermail.android.ui.settings.detail.error_banner

@Composable
fun guard_rotate_rescue_host(view_model: GuardRotateRescueViewModel, on_back: () -> Unit) {
    val state by view_model.state.collectAsState()
    val context: Context = LocalContext.current
    val activity = find_fragment_activity(context)
    val prompt = guard_prompt_text(
        title = stringResource(R.string.guard_prompt_title),
        subtitle = stringResource(R.string.guard_rotate_prompt_subtitle),
    )
    guard_rotate_rescue_screen(
        state = state,
        on_save_file = { guard_save_rescue(context, state.rescue_code) },
        on_print = { guard_print_rescue(context, state.rescue_code) },
        on_copy = { guard_copy_rescue(context, state.rescue_code) },
        on_confirm_input = view_model::on_confirm_input,
        on_password = view_model::on_password,
        on_totp = view_model::on_totp,
        on_submit = { view_model.submit(guard_prompt_signer(activity, prompt)) },
        on_passkey = { view_model.submit_passkey(guard_prompt_signer(activity, prompt), guard_passkey_sheet_for(context)) },
        on_back = on_back,
    )
}

@Composable
fun guard_rotate_rescue_screen(
    state: guard_rotate_ui,
    on_save_file: () -> Unit,
    on_print: () -> Unit,
    on_copy: () -> Unit,
    on_confirm_input: (String) -> Unit,
    on_password: (String) -> Unit,
    on_totp: (String) -> Unit,
    on_submit: () -> Unit,
    on_back: () -> Unit,
    on_passkey: () -> Unit = {},
) {
    secure_screen()
    detail_scaffold(title = stringResource(R.string.guard_rotate_title), on_back = on_back) {
        when (val result = state.result) {
            guard_rotate_result.applied -> {
                guard_result_block(
                    title = stringResource(R.string.guard_rotate_done_title),
                    body = stringResource(R.string.guard_rotate_done_body),
                    primary_label = stringResource(R.string.done),
                    on_primary = on_back,
                )
            }
            is guard_rotate_result.held -> {
                GuardHeldBanner(result.notice)
                Spacer(Modifier.height(AsterSpacing.lg))
                AsterButton(
                    label = stringResource(R.string.done),
                    onClick = on_back,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            null -> guard_rotate_form(
                state,
                on_save_file,
                on_print,
                on_copy,
                on_confirm_input,
                on_password,
                on_totp,
                on_submit,
                on_passkey,
            )
        }
    }
}

@Composable
private fun guard_rotate_form(
    state: guard_rotate_ui,
    on_save_file: () -> Unit,
    on_print: () -> Unit,
    on_copy: () -> Unit,
    on_confirm_input: (String) -> Unit,
    on_password: (String) -> Unit,
    on_totp: (String) -> Unit,
    on_submit: () -> Unit,
    on_passkey: () -> Unit,
) {
    (guard_rotate_notice_text(state.notice, state.error) ?: guard_passkey_failure_text(state.passkey_failure))?.let {
        error_banner(it)
        Spacer(Modifier.height(AsterSpacing.md))
    }
    guard_setup_rescue(state.rescue_code, on_save_file, on_print, on_copy)
    Spacer(Modifier.height(AsterSpacing.lg))
    AsterTextField(
        value = state.confirm_input,
        onValueChange = on_confirm_input,
        modifier = Modifier.fillMaxWidth().testTag("guard_rotate_confirm"),
        label = stringResource(R.string.guard_setup_confirm_label),
        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
    )
    Spacer(Modifier.height(AsterSpacing.md))
    AsterTextField(
        value = state.password,
        onValueChange = on_password,
        modifier = Modifier.fillMaxWidth().testTag("guard_rotate_password"),
        label = stringResource(R.string.guard_setup_password_label),
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
    Spacer(Modifier.height(AsterSpacing.lg))
    AsterButton(
        label = stringResource(R.string.guard_rotate_action),
        onClick = on_submit,
        modifier = Modifier.fillMaxWidth().testTag("guard_rotate_submit"),
        enabled = state.can_submit,
    )
    Spacer(Modifier.height(AsterSpacing.sm))
    AsterGhostButton(
        label = stringResource(R.string.guard_passkey_use),
        onClick = on_passkey,
        modifier = Modifier.fillMaxWidth().testTag("guard_rotate_passkey"),
        enabled = state.can_use_passkey,
    )
}

@Composable
private fun guard_rotate_notice_text(
    notice: guard_rotate_notice?,
    error: org.astermail.android.guard.guard_error_message? = null,
): String? = when (notice) {
    null -> null
    guard_rotate_notice.wrong_password -> stringResource(R.string.guard_setup_wrong_password)
    guard_rotate_notice.totp_required -> stringResource(R.string.guard_setup_totp_required)
    guard_rotate_notice.wrong_group -> stringResource(R.string.guard_setup_confirm_wrong)
    guard_rotate_notice.canceled -> guard_notice_text(guard_action_notice.canceled)
    guard_rotate_notice.key_lost -> guard_notice_text(guard_action_notice.key_lost)
    guard_rotate_notice.no_lock_screen -> guard_notice_text(guard_action_notice.no_lock_screen)
    guard_rotate_notice.no_device -> guard_notice_text(guard_action_notice.no_device)
    guard_rotate_notice.failed -> guard_notice_text(guard_action_notice.failed, error)
}
