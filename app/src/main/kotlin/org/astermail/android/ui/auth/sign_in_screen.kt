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

package org.astermail.android.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.auth.AuthUiState
import org.astermail.android.debugtools.debug_build_banner
import org.astermail.android.auth.AuthViewModel
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.AsterTopBar

@Composable
fun SignInScreen(
    on_back: () -> Unit,
    on_forgot_password: () -> Unit,
    on_signed_in: () -> Unit,
    on_register: () -> Unit,
    prefill_email: String = "",
    view_model: AuthViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by view_model.ui_state.collectAsStateWithLifecycle()

    var email by remember(prefill_email) { mutableStateOf(prefill_email) }
    var email_domain by remember { mutableStateOf("astermail.org") }
    var password by remember { mutableStateOf("") }
    var password_visible by remember { mutableStateOf(false) }
    val email_focus = remember { FocusRequester() }
    val password_focus = remember { FocusRequester() }
    val keyboard_controller = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (prefill_email.isBlank()) email_focus.requestFocus()
    }

    var cached_totp_challenge by remember {
        mutableStateOf<org.astermail.android.auth.TotpChallenge?>(null)
    }
    var signed_in_fired by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is AuthUiState.Success -> if (!signed_in_fired) { signed_in_fired = true; on_signed_in() }
            is AuthUiState.TotpChallenge -> cached_totp_challenge = (state as AuthUiState.TotpChallenge).challenge
            is AuthUiState.Idle -> cached_totp_challenge = null
            else -> Unit
        }
    }

    val is_loading = state is AuthUiState.Loading
    val error_message = (state as? AuthUiState.Error)?.message
    val can_submit = email.isNotBlank() && password.isNotBlank() && !is_loading

    val submit: () -> Unit = {
        if (can_submit) {
            keyboard_controller?.hide()
            val trimmed = email.trim()
            val full_email = if (trimmed.contains("@")) trimmed else "$trimmed@$email_domain"
            view_model.submit_login(full_email, password)
        }
    }

    val active_totp_challenge = cached_totp_challenge
    if (active_totp_challenge != null) {
        TotpVerifyScreen(
            challenge = active_totp_challenge,
            view_model = view_model,
            on_back = {
                cached_totp_challenge = null
                view_model.reset_state()
            },
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AsterTopBar(title = "", on_back = on_back)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.xxl),
            ) {
                Spacer(Modifier.height(AsterSpacing.sm))

                Text(
                    text = stringResource(R.string.sign_in_title),
                    color = colors.text_primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.sign_in_subtitle),
                    color = colors.text_tertiary,
                    fontSize = 14.sp,
                )

                Spacer(Modifier.height(AsterSpacing.xxl))

                AnimatedVisibility(
                    visible = error_message != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        error_banner(message = error_message ?: "")
                        Spacer(Modifier.height(AsterSpacing.lg))
                    }
                }

                AsterTextField(
                    value = email,
                    onValueChange = { raw ->
                        val at_index = raw.indexOf('@')
                        val matched = if (at_index != -1) {
                            val domain_part = raw.substring(at_index + 1).lowercase()
                            when {
                                domain_part == "astermail.org" || domain_part.endsWith(".astermail.org") -> "astermail.org"
                                domain_part == "aster.cx" || domain_part.endsWith(".aster.cx") -> "aster.cx"
                                else -> null
                            }
                        } else {
                            null
                        }
                        if (matched != null) {
                            email_domain = matched
                            email = raw.substring(0, at_index)
                        } else {
                            email = raw
                        }
                        if (state is AuthUiState.Error) view_model.reset_state()
                    },
                    label = stringResource(R.string.username),
                    placeholder = stringResource(R.string.username_placeholder),
                    enabled = !is_loading,
                    keyboard_options = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    keyboard_actions = KeyboardActions(
                        onNext = { password_focus.requestFocus() },
                    ),
                    leading_icon = {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = colors.text_muted,
                        )
                    },
                    content_type = ContentType.Username,
                    modifier = Modifier.focusRequester(email_focus),
                )

                Spacer(Modifier.height(AsterSpacing.md))

                domain_toggle(
                    selected = email_domain,
                    on_select = { email_domain = it },
                )

                Spacer(Modifier.height(AsterSpacing.lg))

                AsterTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (state is AuthUiState.Error) view_model.reset_state()
                    },
                    label = stringResource(R.string.password),
                    placeholder = stringResource(R.string.password_placeholder),
                    enabled = !is_loading,
                    visual_transformation = if (password_visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboard_options = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboard_actions = KeyboardActions(
                        onDone = { submit() },
                    ),
                    leading_icon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = colors.text_muted,
                        )
                    },
                    trailing_icon = {
                        AsterIconButton(
                            icon = if (password_visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            content_description = stringResource(if (password_visible) R.string.hide_password else R.string.show_password),
                            onClick = { password_visible = !password_visible },
                            tint = colors.text_muted,
                        )
                    },
                    content_type = ContentType.Password,
                    modifier = Modifier.focusRequester(password_focus),
                )

                Spacer(Modifier.height(AsterSpacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AsterGhostButton(
                        label = stringResource(R.string.forgot_password),
                        onClick = on_forgot_password,
                        enabled = !is_loading,
                    )
                }

                Spacer(Modifier.height(AsterSpacing.md))

                AsterButton(
                    label = stringResource(R.string.sign_in),
                    onClick = submit,
                    enabled = can_submit,
                    is_loading = is_loading,
                )

                Spacer(Modifier.height(AsterSpacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(R.string.no_account_prompt),
                        color = colors.text_tertiary,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = stringResource(R.string.sign_up),
                        color = colors.accent_blue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = !is_loading, onClick = on_register),
                    )
                }

                Spacer(Modifier.height(AsterSpacing.xxl))
            }
        }

        debug_build_banner()
    }
}

@Composable
private fun TotpVerifyScreen(
    challenge: org.astermail.android.auth.TotpChallenge,
    view_model: AuthViewModel,
    on_back: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val state by view_model.ui_state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var use_backup by remember { mutableStateOf(false) }
    var trust_device by remember { mutableStateOf(false) }
    val is_loading = state is AuthUiState.Loading
    val error_message = (state as? AuthUiState.Error)?.message
    val code_focus = remember { FocusRequester() }
    val code_ready = if (use_backup) {
        code.count { it.isLetterOrDigit() } >= 12
    } else {
        code.length >= 6
    }

    LaunchedEffect(Unit) {
        code_focus.requestFocus()
    }
    LaunchedEffect(use_backup) { code = "" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AsterTopBar(title = "", on_back = on_back)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.xxl),
            ) {
                Spacer(Modifier.height(AsterSpacing.sm))

                Text(
                    text = stringResource(R.string.totp_verify_title),
                    color = colors.text_primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (use_backup) {
                        stringResource(R.string.totp_backup_code_subtitle)
                    } else {
                        stringResource(R.string.totp_verify_subtitle)
                    },
                    color = colors.text_tertiary,
                    fontSize = 14.sp,
                )

                Spacer(Modifier.height(AsterSpacing.xl))

                if (error_message != null) {
                    error_banner(error_message)
                    Spacer(Modifier.height(AsterSpacing.md))
                }

                AsterTextField(
                    value = code,
                    onValueChange = { v ->
                        code = if (use_backup) {
                            v.filter { it.isLetterOrDigit() || it == '-' }.take(20)
                        } else {
                            v.filter { it.isDigit() }.take(6)
                        }
                        if (state is AuthUiState.Error) view_model.reset_state()
                    },
                    label = if (use_backup) {
                        stringResource(R.string.totp_backup_code_label)
                    } else {
                        stringResource(R.string.totp_code_label)
                    },
                    keyboard_options = KeyboardOptions(
                        keyboardType = if (use_backup) KeyboardType.Text else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboard_actions = KeyboardActions(
                        onDone = {
                            if (code_ready && !is_loading) {
                                view_model.submit_totp(code, challenge, trust_device)
                            }
                        },
                    ),
                    leading_icon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = colors.text_muted,
                        )
                    },
                    modifier = Modifier.focusRequester(code_focus),
                )

                Spacer(Modifier.height(AsterSpacing.md))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !is_loading) { trust_device = !trust_device },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = trust_device,
                        onCheckedChange = { trust_device = it },
                        enabled = !is_loading,
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.accent_blue,
                            uncheckedColor = colors.text_muted,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.totp_trust_device),
                        color = colors.text_secondary,
                        fontSize = 14.sp,
                    )
                }

                Spacer(Modifier.height(AsterSpacing.md))

                AsterButton(
                    label = stringResource(R.string.totp_verify_button),
                    onClick = { view_model.submit_totp(code, challenge, trust_device) },
                    enabled = code_ready && !is_loading,
                    is_loading = is_loading,
                )

                Spacer(Modifier.height(AsterSpacing.md))

                Text(
                    text = if (use_backup) {
                        stringResource(R.string.totp_use_authenticator)
                    } else {
                        stringResource(R.string.totp_use_backup_code)
                    },
                    color = colors.accent_blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(enabled = !is_loading) { use_backup = !use_backup },
                )

                Spacer(Modifier.height(AsterSpacing.xxl))
            }
        }
    }
}

@Composable
internal fun error_banner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFDC2626),
                shape = SquircleShape(18.dp),
            )
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

