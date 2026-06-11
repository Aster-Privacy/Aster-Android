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

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField

@Composable
fun RegisterPasswordStep(
    state: RegisterFlowState,
    error_message: String?,
    is_loading: Boolean,
    on_next: () -> Unit,
    on_sign_in: () -> Unit,
    on_terms_click: () -> Unit = {},
    on_privacy_click: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    var password_visible by remember { mutableStateOf(false) }
    var confirm_visible by remember { mutableStateOf(false) }
    var captcha_reset_trigger by remember { mutableStateOf(0) }
    val confirm_focus = remember { androidx.compose.ui.focus.FocusRequester() }

    val password = state.password.value
    val length_ok = password.length >= 8
    val upper_ok = password.any { it.isUpperCase() }
    val lower_ok = password.any { it.isLowerCase() }
    val number_ok = password.any { it.isDigit() }
    val match_ok = password.isNotEmpty() && password == state.confirm_password.value
    val captcha_ok = !state.captcha_token.value.isNullOrBlank()
    val can_submit = length_ok && upper_ok && lower_ok && number_ok && match_ok && captcha_ok && !is_loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AsterSpacing.xxl),
    ) {
        Spacer(Modifier.height(AsterSpacing.sm))

        Text(
            text = stringResource(R.string.secure_your_account),
            color = colors.text_primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.create_strong_password),
            color = colors.text_tertiary,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        if (error_message != null) {
            error_banner(message = error_message)
            Spacer(Modifier.height(AsterSpacing.lg))
        }

        AsterTextField(
            value = password,
            onValueChange = { state.password.value = it },
            label = stringResource(R.string.password),
            placeholder = stringResource(R.string.new_password),
            enabled = !is_loading,
            visual_transformation = if (password_visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboard_actions = KeyboardActions(onNext = { confirm_focus.requestFocus() }),
            leading_icon = {
                Icon(Icons.Filled.Lock, null, tint = colors.text_muted)
            },
            trailing_icon = {
                AsterIconButton(
                    icon = if (password_visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    content_description = if (password_visible) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                    onClick = { password_visible = !password_visible },
                    tint = colors.text_muted,
                )
            },
            content_type = ContentType.NewPassword,
        )

        Spacer(Modifier.height(AsterSpacing.lg))

        AsterTextField(
            value = state.confirm_password.value,
            onValueChange = { state.confirm_password.value = it },
            label = stringResource(R.string.confirm_password),
            placeholder = stringResource(R.string.confirm_password_label),
            enabled = !is_loading,
            visual_transformation = if (confirm_visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboard_actions = KeyboardActions(onDone = { if (can_submit) on_next() }),
            leading_icon = {
                Icon(Icons.Filled.Lock, null, tint = colors.text_muted)
            },
            trailing_icon = {
                AsterIconButton(
                    icon = if (confirm_visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    content_description = if (confirm_visible) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                    onClick = { confirm_visible = !confirm_visible },
                    tint = colors.text_muted,
                )
            },
            content_type = ContentType.NewPassword,
            modifier = Modifier.focusRequester(confirm_focus),
        )

        Spacer(Modifier.height(AsterSpacing.lg))

        keep_signed_in_row(
            checked = state.remember_me.value,
            on_toggle = { state.remember_me.value = !state.remember_me.value },
        )

        Spacer(Modifier.height(AsterSpacing.md))

        password_requirement(met = length_ok, label = stringResource(R.string.requirement_8_chars))
        password_requirement(met = upper_ok, label = stringResource(R.string.requirement_uppercase))
        password_requirement(met = lower_ok, label = stringResource(R.string.requirement_lowercase))
        password_requirement(met = number_ok, label = stringResource(R.string.requirement_number))

        Spacer(Modifier.height(AsterSpacing.md))

        terms_agreement_text(
            on_terms_click = on_terms_click,
            on_privacy_click = on_privacy_click,
        )

        Spacer(Modifier.height(AsterSpacing.md))

        TurnstileWidget(
            on_token = { state.captcha_token.value = it },
            on_error = {
                state.captcha_token.value = null
                captcha_reset_trigger += 1
            },
            on_expired = {
                state.captcha_token.value = null
                captcha_reset_trigger += 1
            },
            reset_trigger = captcha_reset_trigger,
        )

        Spacer(Modifier.height(AsterSpacing.md))

        AsterButton(
            label = stringResource(R.string.next),
            onClick = on_next,
            enabled = can_submit,
            is_loading = is_loading,
        )

        Spacer(Modifier.height(AsterSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.have_account_prompt),
                color = colors.text_tertiary,
                fontSize = 14.sp,
            )
            Text(
                text = stringResource(R.string.sign_in),
                color = colors.accent_blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = !is_loading, onClick = on_sign_in),
            )
        }

        Spacer(Modifier.height(AsterSpacing.xxl))
    }
}

@Composable
private fun keep_signed_in_row(
    checked: Boolean,
    on_toggle: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_toggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val box_bg = if (checked) colors.accent_blue else Color.Transparent
        val box_border = if (checked) colors.accent_blue else colors.border_primary
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(box_bg, shape = RoundedCornerShape(4.dp))
                .border(1.5.dp, box_border, shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.size(AsterSpacing.md))
        Text(
            text = stringResource(R.string.keep_me_signed_in),
            color = colors.text_secondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun password_requirement(met: Boolean, label: String) {
    val colors = AsterMaterial.colors
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (met) colors.success else colors.text_muted,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "password_req_tint",
    )
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = met,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                ) + androidx.compose.animation.scaleIn(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
                    initialScale = 0.5f,
                )) togetherWith (androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
                ) + androidx.compose.animation.scaleOut(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
                    targetScale = 0.5f,
                ))
            },
            label = "password_req_icon",
        ) { is_met ->
            if (is_met) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(tint, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun terms_agreement_text(
    on_terms_click: () -> Unit,
    on_privacy_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val prefix = stringResource(R.string.register_agree_prefix)
    val terms = stringResource(R.string.terms_of_service)
    val and_word = stringResource(R.string.register_agree_and)
    val privacy = stringResource(R.string.privacy_policy)

    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.text_tertiary)) {
            append(prefix)
        }
        pushStringAnnotation(tag = "terms", annotation = "terms")
        withStyle(SpanStyle(color = colors.accent_blue, fontWeight = FontWeight.Medium)) {
            append(terms)
        }
        pop()
        withStyle(SpanStyle(color = colors.text_tertiary)) {
            append(and_word)
        }
        pushStringAnnotation(tag = "privacy", annotation = "privacy")
        withStyle(SpanStyle(color = colors.accent_blue, fontWeight = FontWeight.Medium)) {
            append(privacy)
        }
        pop()
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            annotated.getStringAnnotations("terms", offset, offset).firstOrNull()?.let {
                on_terms_click()
            }
            annotated.getStringAnnotations("privacy", offset, offset).firstOrNull()?.let {
                on_privacy_click()
            }
        },
    )
}
