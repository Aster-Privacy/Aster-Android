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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.auth.RecoveryStep
import org.astermail.android.auth.RecoveryViewModel
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.AsterTopBar

@Composable
fun ForgotPasswordScreen(
    on_back: () -> Unit,
    on_submit: (email: String) -> Unit,
    start_with_code: Boolean = false,
    view_model: RecoveryViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by view_model.state.collectAsStateWithLifecycle()

    LaunchedEffect(start_with_code) {
        if (start_with_code) {
            view_model.go_to_code_step()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsterTopBar(
                title = "",
                on_back = {
                    when (state.step) {
                        RecoveryStep.email -> on_back()
                        RecoveryStep.success -> on_back()
                        RecoveryStep.processing -> {}
                        RecoveryStep.new_codes -> {}
                        else -> view_model.go_back()
                    }
                },
            )

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "recovery_step",
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AsterSpacing.xxl),
                ) {
                    when (step) {
                        RecoveryStep.email -> email_step(
                            is_loading = state.is_loading,
                            error = state.error,
                            on_submit = { view_model.send_recovery_email(it) },
                            on_back = on_back,
                            on_use_code = { view_model.go_to_code_step() },
                        )
                        RecoveryStep.email_sent -> email_sent_step(
                            on_use_code = { view_model.go_to_code_step() },
                        )
                        RecoveryStep.code -> code_step(
                            is_loading = state.is_loading,
                            error = state.error,
                            on_verify = { view_model.verify_code(it) },
                            on_back = { view_model.go_back() },
                        )
                        RecoveryStep.password -> password_step(
                            is_loading = state.is_loading,
                            error = state.error,
                            on_submit = { pw, confirm -> view_model.submit_new_password(pw, confirm) },
                        )
                        RecoveryStep.processing -> processing_step(
                            status = state.processing_status,
                        )
                        RecoveryStep.new_codes -> new_codes_step(
                            codes = state.new_codes,
                            on_continue = { view_model.go_to_success() },
                        )
                        RecoveryStep.success -> success_step(
                            on_sign_in = on_back,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun email_step(
    is_loading: Boolean,
    error: String?,
    on_submit: (String) -> Unit,
    on_back: () -> Unit,
    on_use_code: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var email by remember { mutableStateOf("") }

    Spacer(Modifier.height(AsterSpacing.sm))
    Text(
        text = stringResource(R.string.recover_your_account),
        color = colors.text_primary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.enter_email_for_recovery),
        color = colors.text_tertiary,
        fontSize = 14.sp,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    if (error != null) {
        error_banner(message = error)
        Spacer(Modifier.height(AsterSpacing.lg))
    }

    AsterTextField(
        value = email,
        onValueChange = { email = it },
        label = stringResource(R.string.email),
        placeholder = stringResource(R.string.email_address),
        enabled = !is_loading,
        keyboard_options = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboard_actions = KeyboardActions(
            onDone = { if (email.isNotBlank() && !is_loading) on_submit(email) },
        ),
        leading_icon = {
            Icon(
                imageVector = Icons.Filled.AlternateEmail,
                contentDescription = null,
                tint = colors.text_muted,
            )
        },
    )

    Spacer(Modifier.height(AsterSpacing.xl))

    AsterButton(
        label = stringResource(R.string.continue_action),
        onClick = { on_submit(email) },
        enabled = email.isNotBlank() && !is_loading,
        is_loading = is_loading,
    )

    Spacer(Modifier.height(AsterSpacing.md))

    AsterSecondaryButton(
        label = stringResource(R.string.back_to_sign_in),
        onClick = on_back,
        enabled = !is_loading,
    )

    Spacer(Modifier.height(AsterSpacing.md))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.use_recovery_code),
            color = colors.accent_blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(enabled = !is_loading, onClick = on_use_code),
        )
    }

    Spacer(Modifier.height(AsterSpacing.xxl))
}

@Composable
private fun email_sent_step(
    on_use_code: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Spacer(Modifier.height(AsterSpacing.xxxl))
    Text(
        text = stringResource(R.string.recovery_email_sent),
        color = colors.text_primary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.recovery_email_sent_description),
        color = colors.text_tertiary,
        fontSize = 14.sp,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    AsterButton(
        label = stringResource(R.string.use_recovery_code_instead),
        onClick = on_use_code,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))
}

@Composable
private fun code_step(
    is_loading: Boolean,
    error: String?,
    on_verify: (String) -> Unit,
    on_back: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var code by remember { mutableStateOf("") }

    Spacer(Modifier.height(AsterSpacing.sm))
    Text(
        text = stringResource(R.string.enter_recovery_code),
        color = colors.text_primary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.enter_recovery_code_description),
        color = colors.text_tertiary,
        fontSize = 14.sp,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    if (error != null) {
        error_banner(message = error)
        Spacer(Modifier.height(AsterSpacing.lg))
    }

    AsterTextField(
        value = code,
        onValueChange = { code = it.uppercase() },
        label = stringResource(R.string.recovery_code),
        placeholder = stringResource(R.string.recovery_code_placeholder),
        enabled = !is_loading,
        keyboard_options = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
        keyboard_actions = KeyboardActions(
            onDone = { if (code.isNotBlank() && !is_loading) on_verify(code) },
        ),
        leading_icon = {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = colors.text_muted,
            )
        },
    )

    Spacer(Modifier.height(AsterSpacing.xl))

    AsterButton(
        label = stringResource(R.string.verify_code),
        onClick = { on_verify(code) },
        enabled = code.isNotBlank() && !is_loading,
        is_loading = is_loading,
    )

    Spacer(Modifier.height(AsterSpacing.md))

    AsterSecondaryButton(
        label = stringResource(R.string.back),
        onClick = on_back,
        enabled = !is_loading,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))
}

@Composable
private fun password_step(
    is_loading: Boolean,
    error: String?,
    on_submit: (String, String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var password_visible by remember { mutableStateOf(false) }
    var confirm_visible by remember { mutableStateOf(false) }
    val confirm_focus = remember { androidx.compose.ui.focus.FocusRequester() }

    Spacer(Modifier.height(AsterSpacing.sm))
    Text(
        text = stringResource(R.string.create_new_password),
        color = colors.text_primary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.choose_strong_password),
        color = colors.text_tertiary,
        fontSize = 14.sp,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    if (error != null) {
        error_banner(message = error)
        Spacer(Modifier.height(AsterSpacing.lg))
    }

    AsterTextField(
        value = password,
        onValueChange = { password = it },
        label = stringResource(R.string.new_password),
        placeholder = stringResource(R.string.enter_new_password),
        enabled = !is_loading,
        visual_transformation = if (password_visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboard_options = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
        ),
        keyboard_actions = KeyboardActions(
            onNext = { confirm_focus.requestFocus() },
        ),
        leading_icon = {
            Icon(Icons.Filled.Lock, null, tint = colors.text_muted)
        },
        trailing_icon = {
            AsterIconButton(
                icon = if (password_visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                content_description = stringResource(if (password_visible) R.string.hide_password else R.string.show_password),
                onClick = { password_visible = !password_visible },
                tint = colors.text_muted,
            )
        },
    )

    if (password.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        password_strength_bar(password)
    }

    Spacer(Modifier.height(AsterSpacing.lg))

    AsterTextField(
        value = confirm,
        onValueChange = { confirm = it },
        label = stringResource(R.string.confirm_password_label),
        placeholder = stringResource(R.string.confirm_new_password),
        enabled = !is_loading,
        visual_transformation = if (confirm_visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboard_options = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboard_actions = KeyboardActions(
            onDone = {
                if (password.isNotBlank() && confirm.isNotBlank() && !is_loading) {
                    on_submit(password, confirm)
                }
            },
        ),
        leading_icon = {
            Icon(Icons.Filled.Lock, null, tint = colors.text_muted)
        },
        trailing_icon = {
            AsterIconButton(
                icon = if (confirm_visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                content_description = stringResource(if (confirm_visible) R.string.hide_password else R.string.show_password),
                onClick = { confirm_visible = !confirm_visible },
                tint = colors.text_muted,
            )
        },
        modifier = Modifier.focusRequester(confirm_focus),
    )

    Spacer(Modifier.height(AsterSpacing.xl))

    AsterButton(
        label = stringResource(R.string.reset_password),
        onClick = { on_submit(password, confirm) },
        enabled = password.isNotBlank() && confirm.isNotBlank() && !is_loading,
        is_loading = is_loading,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))
}

@Composable
private fun password_strength_bar(password: String) {
    val colors = AsterMaterial.colors
    val strength = compute_strength(password)
    val (label, color, segments) = when {
        strength < 2 -> Triple(stringResource(R.string.strength_weak), Color(0xFFEF4444), 1)
        strength < 3 -> Triple(stringResource(R.string.strength_fair), Color(0xFFF59E0B), 2)
        strength < 4 -> Triple(stringResource(R.string.strength_good), Color(0xFF22C55E), 3)
        else -> Triple(stringResource(R.string.strength_strong), Color(0xFF10B981), 4)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(4) { idx ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (idx < segments) color else colors.border_primary),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun compute_strength(password: String): Int {
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score.coerceAtMost(5)
}

@Composable
private fun processing_step(status: String) {
    val colors = AsterMaterial.colors
    Spacer(Modifier.height(100.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = colors.accent_blue,
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(AsterSpacing.xl))
        Text(
            text = stringResource(R.string.recovering_your_account),
            color = colors.text_primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = status,
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.xl))
        Text(
            text = stringResource(R.string.please_dont_close_app),
            color = colors.text_muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun new_codes_step(
    codes: List<String>,
    on_continue: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var codes_visible by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Spacer(Modifier.height(AsterSpacing.sm))
    Text(
        text = stringResource(R.string.save_new_recovery_codes),
        color = colors.text_primary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.old_codes_invalidated),
        color = colors.text_tertiary,
        fontSize = 14.sp,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.recovery_codes_count, codes.size),
            color = colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
            AsterIconButton(
                icon = if (codes_visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                content_description = stringResource(if (codes_visible) R.string.hide_codes else R.string.show_codes),
                onClick = { codes_visible = !codes_visible },
                tint = colors.text_muted,
            )
            AsterIconButton(
                icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                content_description = stringResource(R.string.copy_codes),
                onClick = {
                    val text = codes.joinToString("\n")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("recovery codes", text)
                    clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                    clipboard?.setPrimaryClip(clip)
                    copied = true
                },
                tint = if (copied) Color(0xFF22C55E) else colors.text_muted,
            )
        }
    }

    Spacer(Modifier.height(AsterSpacing.md))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border_primary, SquircleShape(18.dp))
            .background(colors.bg_secondary, SquircleShape(18.dp))
            .padding(AsterSpacing.lg),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!codes_visible) Modifier.blur(8.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            codes.forEach { code ->
                Text(
                    text = code,
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    Spacer(Modifier.height(AsterSpacing.xxl))

    AsterButton(
        label = stringResource(R.string.continue_action),
        onClick = on_continue,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))
}

@Composable
private fun success_step(on_sign_in: () -> Unit) {
    val colors = AsterMaterial.colors

    Spacer(Modifier.height(80.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFF22C55E).copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.password_reset_successful),
            color = colors.text_primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.password_reset_success_description),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        AsterButton(
            label = stringResource(R.string.sign_in),
            onClick = on_sign_in,
        )
    }

    Spacer(Modifier.height(AsterSpacing.xxl))
}
