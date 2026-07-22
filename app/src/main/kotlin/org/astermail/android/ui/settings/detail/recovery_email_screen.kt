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

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.SaveStatus
import org.astermail.android.settings.SettingsViewModel

@Composable
fun RecoveryEmailScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load_recovery_email()
        vm.load_security_status()
    }

    var email by rememberSaveable { mutableStateOf("") }
    var show_step_up by rememberSaveable { mutableStateOf(false) }
    var step_up_is_remove by rememberSaveable { mutableStateOf(false) }
    var step_up_password by rememberSaveable { mutableStateOf("") }
    var step_up_code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.recovery_email_address) {
        if (email.isBlank() && !state.recovery_email_address.isNullOrBlank()) {
            email = state.recovery_email_address!!
        }
    }

    LaunchedEffect(state.save_status) {
        if (state.save_status == SaveStatus.SAVED) {
            Toast.makeText(
                context,
                context.getString(R.string.recovery_email_saved),
                Toast.LENGTH_SHORT,
            ).show()
            vm.reset_save_status()
        }
    }

    LaunchedEffect(state.action_result) {
        state.action_result?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clear_action_result()
        }
    }

    detail_scaffold(title = stringResource(R.string.recovery_email), on_back = on_back) {
        Text(
            text = stringResource(R.string.backup_email_description),
            color = colors.text_tertiary,
            fontSize = 14.sp,
        )
        v_gap(AsterSpacing.lg)

        if (state.recovery_email_set) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AsterSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.recovery_email_address
                                ?: stringResource(R.string.recovery_email),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (state.recovery_email_verified) {
                                stringResource(R.string.recovery_email_status_verified)
                            } else {
                                stringResource(R.string.recovery_email_status_unverified)
                            },
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                    }
                    if (state.recovery_email_verified) {
                        verified_badge(stringResource(R.string.verified))
                    }
                }
            }
            v_gap(AsterSpacing.lg)
        }

        state.error?.let {
            error_banner(it)
            v_gap(AsterSpacing.lg)
        }

        AsterTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.recovery_email),
            placeholder = stringResource(R.string.recovery_email_placeholder),
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            leading_icon = {
                Icon(TablerIcons.Mail, null, tint = colors.text_muted)
            },
        )
        v_gap(AsterSpacing.lg)

        AsterButton(
            label = if (state.recovery_email_set) {
                stringResource(R.string.update_recovery_email)
            } else {
                stringResource(R.string.save_recovery_email)
            },
            onClick = {
                step_up_is_remove = false
                step_up_password = ""
                step_up_code = ""
                show_step_up = true
            },
            enabled = email.trim().contains("@") && state.save_status != SaveStatus.SAVING,
            is_loading = state.save_status == SaveStatus.SAVING,
        )

        if (state.recovery_email_set && !state.recovery_email_verified) {
            v_gap(AsterSpacing.sm)
            AsterGhostButton(
                label = stringResource(R.string.recovery_email_resend),
                onClick = { vm.resend_recovery_verification() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.recovery_email_set) {
            v_gap(AsterSpacing.sm)
            AsterGhostButton(
                label = stringResource(R.string.recovery_email_remove),
                onClick = {
                    step_up_is_remove = true
                    step_up_password = ""
                    step_up_code = ""
                    show_step_up = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.save_status != SaveStatus.SAVING,
            )
        }

        v_gap(AsterSpacing.xxl)
    }

    if (show_step_up) {
        val totp_on = state.security_status?.totp_enabled == true
        val can_confirm = step_up_password.isNotBlank() &&
            (!totp_on || step_up_code.length == 6) &&
            state.save_status != SaveStatus.SAVING
        AsterAlertDialog(
            on_dismiss = { show_step_up = false },
            title = if (step_up_is_remove) {
                stringResource(R.string.recovery_email_remove)
            } else {
                stringResource(R.string.recovery_email)
            },
            message = stringResource(R.string.recovery_step_up_description),
            confirm_label = if (step_up_is_remove) {
                stringResource(R.string.recovery_email_remove)
            } else {
                stringResource(R.string.save_recovery_email)
            },
            cancel_label = stringResource(R.string.cancel),
            confirm_style = if (step_up_is_remove) {
                DialogConfirmStyle.destructive
            } else {
                DialogConfirmStyle.primary
            },
            confirm_enabled = can_confirm,
            is_busy = state.save_status == SaveStatus.SAVING,
            on_confirm = {
                val code = if (totp_on) step_up_code else null
                if (step_up_is_remove) {
                    vm.remove_recovery_email(step_up_password, code)
                } else {
                    vm.save_recovery_email(email, step_up_password, code)
                }
                show_step_up = false
            },
            extra_content = {
                androidx.compose.foundation.layout.Column {
                    AsterTextField(
                        value = step_up_password,
                        onValueChange = { step_up_password = it },
                        label = stringResource(R.string.password),
                        placeholder = stringResource(R.string.enter_your_password),
                        visual_transformation = PasswordVisualTransformation(),
                        keyboard_options = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    if (totp_on) {
                        v_gap(AsterSpacing.md)
                        AsterTextField(
                            value = step_up_code,
                            onValueChange = { input ->
                                step_up_code = input.filter { it.isDigit() }.take(6)
                            },
                            label = stringResource(R.string.authenticator_code),
                            placeholder = "000000",
                            keyboard_options = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }
                }
            },
        )
    }
}
