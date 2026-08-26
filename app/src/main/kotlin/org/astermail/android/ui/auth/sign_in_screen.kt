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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.painterResource
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
import org.astermail.android.util.ascii_digits

@Composable
fun SignInScreen(
    on_back: () -> Unit,
    on_forgot_password: () -> Unit,
    on_signed_in: () -> Unit,
    on_register: () -> Unit,
    prefill_email: String = "",
    view_model: AuthViewModel = hiltViewModel(),
) {
    org.astermail.android.ui.common.secure_screen()
    val colors = AsterMaterial.colors
    val state by view_model.ui_state.collectAsStateWithLifecycle()

    var email by rememberSaveable(prefill_email) { mutableStateOf(prefill_email) }
    var email_domain by rememberSaveable { mutableStateOf("astermail.org") }
    var password by remember { mutableStateOf("") }
    var password_visible by remember { mutableStateOf(false) }
    val email_focus = remember { FocusRequester() }
    val password_focus = remember { FocusRequester() }
    val keyboard_controller = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var cached_totp_challenge by remember {
        mutableStateOf<org.astermail.android.auth.TotpChallenge?>(null)
    }
    var signed_in_fired by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is AuthUiState.Success -> if (!signed_in_fired) { signed_in_fired = true; on_signed_in() }
            is AuthUiState.TotpChallenge -> cached_totp_challenge = (state as AuthUiState.TotpChallenge).challenge
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
    BackHandler {
        if (active_totp_challenge != null) {
            if (view_model.cancel_totp(active_totp_challenge)) cached_totp_challenge = null
        } else {
            on_back()
        }
    }
    if (active_totp_challenge != null) {
        TotpVerifyScreen(
            challenge = active_totp_challenge,
            view_model = view_model,
            on_back = {
                if (view_model.cancel_totp(active_totp_challenge)) cached_totp_challenge = null
            },
        )
        return
    }

    val fields = SignInFieldState(
        email = email,
        password = password,
        password_visible = password_visible,
        email_domain = email_domain,
        error_message = error_message,
        is_loading = is_loading,
        can_submit = can_submit,
    )
    val callbacks = SignInCallbacks(
        on_email_change = { raw ->
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
        on_password_change = {
            password = it
            if (state is AuthUiState.Error) view_model.reset_state()
        },
        on_toggle_password_visible = { password_visible = !password_visible },
        on_domain_select = { email_domain = it },
        on_submit = submit,
        on_forgot_password = on_forgot_password,
        on_register = on_register,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        auth_centered_column(horizontal_padding = 0.dp) {
            Image(
                painter = painterResource(R.drawable.aster_wordmark),
                contentDescription = null,
                modifier = Modifier.height(40.dp),
            )

            Spacer(Modifier.height(AsterSpacing.xl))

            aster_variant_body(fields, callbacks, email_focus, password_focus)
        }

        AsterTopBar(
            title = "",
            on_back = on_back,
        )

        debug_build_banner()
    }
}

private data class SignInFieldState(
    val email: String,
    val password: String,
    val password_visible: Boolean,
    val email_domain: String,
    val error_message: String?,
    val is_loading: Boolean,
    val can_submit: Boolean,
)

private class SignInCallbacks(
    val on_email_change: (String) -> Unit,
    val on_password_change: (String) -> Unit,
    val on_toggle_password_visible: () -> Unit,
    val on_domain_select: (String) -> Unit,
    val on_submit: () -> Unit,
    val on_forgot_password: () -> Unit,
    val on_register: () -> Unit,
)

@Composable
private fun aster_variant_body(
    fields: SignInFieldState,
    cb: SignInCallbacks,
    email_focus: FocusRequester,
    password_focus: FocusRequester,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.sign_in_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        AnimatedVisibility(
            visible = fields.error_message != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                error_banner(message = fields.error_message ?: "")
                Spacer(Modifier.height(AsterSpacing.lg))
            }
        }

        AsterTextField(
            value = fields.email,
            onValueChange = cb.on_email_change,
            label = stringResource(R.string.username),
            placeholder = stringResource(R.string.username_placeholder),
            enabled = !fields.is_loading,
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboard_actions = KeyboardActions(
                onNext = { password_focus.requestFocus() },
            ),
            leading_icon = {
                Icon(
                    imageVector = TablerIcons.User,
                    contentDescription = null,
                    tint = colors.text_muted,
                )
            },
            content_type = ContentType.Username,
            modifier = Modifier.focusRequester(email_focus),
        )

        Spacer(Modifier.height(AsterSpacing.md))

        domain_toggle(
            selected = fields.email_domain,
            on_select = cb.on_domain_select,
        )

        Spacer(Modifier.height(AsterSpacing.lg))

        AsterTextField(
            value = fields.password,
            onValueChange = cb.on_password_change,
            label = stringResource(R.string.password),
            placeholder = stringResource(R.string.password_placeholder),
            enabled = !fields.is_loading,
            visual_transformation = if (fields.password_visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboard_actions = KeyboardActions(
                onDone = { cb.on_submit() },
            ),
            leading_icon = {
                Icon(
                    imageVector = TablerIcons.Lock,
                    contentDescription = null,
                    tint = colors.text_muted,
                )
            },
            trailing_icon = {
                AsterIconButton(
                    icon = if (fields.password_visible) TablerIcons.EyeOff else TablerIcons.Eye,
                    content_description = stringResource(if (fields.password_visible) R.string.hide_password else R.string.show_password),
                    onClick = cb.on_toggle_password_visible,
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
                onClick = cb.on_forgot_password,
                enabled = !fields.is_loading,
            )
        }

        Spacer(Modifier.height(AsterSpacing.md))

        AsterButton(
            label = stringResource(R.string.sign_in),
            onClick = cb.on_submit,
            enabled = fields.can_submit,
            is_loading = fields.is_loading,
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
                modifier = Modifier.clickable(enabled = !fields.is_loading, onClick = cb.on_register),
            )
        }

        Spacer(Modifier.height(AsterSpacing.xxl))
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
        Column(modifier = Modifier.fillMaxSize()) {
            AsterTopBar(title = "", on_back = on_back)

            auth_centered_column(horizontal_alignment = Alignment.Start) {
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
                            ascii_digits(v).take(6)
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
                            imageVector = TablerIcons.Lock,
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

