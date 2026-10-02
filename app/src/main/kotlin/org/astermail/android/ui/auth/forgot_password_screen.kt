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
import org.astermail.android.design.acrylic
import org.astermail.android.ui.common.open_external_url
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import compose.icons.tablericons.*

import android.content.ClipData
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.auth.RecoveryStep
import org.astermail.android.auth.RecoveryViewModel
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.ui.common.page_surface

private const val SUPPORT_MAIL_URL = "mailto:support@astermail.org"
private const val HELP_CENTER_URL = "https://astermail.org/help"

@Composable
fun ForgotPasswordScreen(
    on_back: () -> Unit,
    on_submit: (email: String) -> Unit,
    start_with_code: Boolean = false,
    view_model: RecoveryViewModel = hiltViewModel(),
) {
    org.astermail.android.ui.common.secure_screen()
    val colors = AsterMaterial.colors
    val state by view_model.state.collectAsStateWithLifecycle()
    var new_password by remember { mutableStateOf("") }
    var new_password_confirm by remember { mutableStateOf("") }

    LaunchedEffect(start_with_code) {
        if (start_with_code) {
            view_model.go_to_code_step()
        }
    }

    LaunchedEffect(state.step) {
        if (state.step == RecoveryStep.new_codes || state.step == RecoveryStep.review_security) {
            new_password = ""
            new_password_confirm = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val top_bar_back: (() -> Unit)? = when (state.step) {
                RecoveryStep.processing, RecoveryStep.new_codes -> null
                RecoveryStep.email, RecoveryStep.review_security -> on_back
                else -> ({ view_model.go_back() })
            }
            AsterTopBar(title = "", on_back = top_bar_back)

            AnimatedContent(
                targetState = state.step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState.ordinal >= initialState.ordinal) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "recovery_step",
            ) { step ->
                auth_centered_column(
                    horizontal_alignment = Alignment.Start,
                    vertical_arrangement = Arrangement.Top,
                ) {
                    val change_account = { view_model.go_to_email_step() }
                    when (step) {
                        RecoveryStep.email -> email_step(
                            initial_email = state.email,
                            is_loading = state.is_loading,
                            error = state.error,
                            on_submit = { view_model.submit_email(it) },
                            on_clear_error = { view_model.clear_error() },
                            on_back = on_back,
                        )
                        RecoveryStep.email_sent -> email_sent_step(
                            email = state.email,
                            error = state.error,
                            is_loading = state.is_loading,
                            resend_seconds = state.resend_seconds,
                            on_sign_in = on_back,
                            on_resend = { view_model.resend_reset_link() },
                            on_use_code = { view_model.go_to_code_step() },
                            on_change_account = change_account,
                        )
                        RecoveryStep.code -> code_step(
                            email = state.email,
                            is_loading = state.is_loading,
                            error = state.error,
                            on_verify = { view_model.verify_code(it) },
                            on_other_ways = { view_model.go_to_other_ways() },
                            on_change_account = change_account,
                        )
                        RecoveryStep.other_ways -> other_ways_step(
                            email = state.email,
                            is_loading = state.is_loading,
                            on_select_code = { view_model.go_to_code_step() },
                            on_select_email = { view_model.go_to_reset_email_confirm() },
                            on_contact_support = { view_model.go_to_support() },
                            on_change_account = change_account,
                        )
                        RecoveryStep.support -> support_step(
                            email = state.email,
                            on_change_account = change_account,
                        )
                        RecoveryStep.reset_email_confirm -> reset_email_confirm_step(
                            email = state.email,
                            is_loading = state.is_loading,
                            error = state.error,
                            on_send = { view_model.send_reset_link() },
                            on_back = { view_model.go_back() },
                            on_change_account = change_account,
                        )
                        RecoveryStep.password -> password_step(
                            email = state.email,
                            password = new_password,
                            confirm = new_password_confirm,
                            on_password_change = { new_password = it },
                            on_confirm_change = { new_password_confirm = it },
                            is_loading = state.is_loading,
                            error = state.error,
                            on_submit = { pw, confirm -> view_model.submit_new_password(pw, confirm) },
                        )
                        RecoveryStep.processing -> processing_step(
                            status = state.processing_status,
                        )
                        RecoveryStep.new_codes -> new_codes_step(
                            codes = state.new_codes,
                            account_email = state.email,
                            on_continue = { view_model.go_to_review_security() },
                        )
                        RecoveryStep.review_security -> review_security_step(
                            review = state.review,
                            on_sign_in = on_back,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun recovery_step_header(
    title: String,
    description: String,
    email: String = "",
    on_change_account: (() -> Unit)? = null,
    show_logo: Boolean = true,
) {
    val colors = AsterMaterial.colors

    if (show_logo) {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(28.dp),
        )
        Spacer(Modifier.height(AsterSpacing.xxl))
    }
    Text(
        text = title,
        color = colors.text_primary,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 32.sp,
    )
    Spacer(Modifier.height(AsterSpacing.sm))
    Text(
        text = description,
        color = colors.text_secondary,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    if (email.isNotBlank()) {
        Spacer(Modifier.height(AsterSpacing.lg))
        account_chip(email = email, on_click = on_change_account)
    }
}

@Composable
private fun account_chip(
    email: String,
    on_click: (() -> Unit)?,
) {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier
            .clip(AsterShapes.pill)
            .border(1.dp, colors.border_primary, AsterShapes.pill)
            .then(if (on_click != null) Modifier.clickable(onClick = on_click) else Modifier)
            .padding(start = AsterSpacing.xs, end = AsterSpacing.md, top = AsterSpacing.xs, bottom = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.accent_blue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = email.take(1).uppercase(),
                color = colors.on_accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = email,
            color = colors.text_primary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        if (on_click != null) {
            Spacer(Modifier.width(AsterSpacing.xs))
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun recovery_actions(
    primary_label: String,
    on_primary: () -> Unit,
    primary_enabled: Boolean = true,
    primary_loading: Boolean = false,
    secondary_label: String? = null,
    on_secondary: (() -> Unit)? = null,
    secondary_enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AsterButton(
            label = primary_label,
            onClick = on_primary,
            enabled = primary_enabled,
            is_loading = primary_loading,
        )
        if (secondary_label != null && on_secondary != null) {
            Spacer(Modifier.height(AsterSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                text_link(
                    label = secondary_label,
                    enabled = secondary_enabled,
                    on_click = on_secondary,
                )
            }
        }
    }
}

@Composable
private fun text_link(
    label: String,
    enabled: Boolean = true,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = if (enabled) colors.accent_blue else colors.text_muted,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(AsterShapes.item)
            .clickable(enabled = enabled, onClick = on_click)
            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm),
    )
}

private val recovery_account_domains = listOf("astermail.org", "aster.cx")

private fun recovery_account_domain(domain_part: String): String? {
    val domain = domain_part.lowercase()
    return recovery_account_domains.firstOrNull { domain == it || domain.endsWith(".$it") }
}

@Composable
private fun email_step(
    initial_email: String,
    is_loading: Boolean,
    error: String?,
    on_submit: (String) -> Unit,
    on_clear_error: () -> Unit,
    on_back: () -> Unit,
) {
    var username by remember { mutableStateOf(initial_email.substringBefore('@')) }
    var email_domain by remember {
        mutableStateOf(recovery_account_domain(initial_email.substringAfter('@', "")) ?: recovery_account_domains.first())
    }
    val submit = {
        val trimmed = username.trim()
        if (trimmed.isNotEmpty() && !is_loading) {
            on_submit(if (trimmed.contains("@")) trimmed else "$trimmed@$email_domain")
        }
    }

    recovery_step_header(
        title = stringResource(R.string.recover_your_account),
        description = stringResource(R.string.enter_email_for_recovery),
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    if (error != null) {
        error_banner(message = error)
        Spacer(Modifier.height(AsterSpacing.lg))
    }

    AsterTextField(
        value = username,
        onValueChange = { raw ->
            val at_index = raw.indexOf('@')
            val matched = if (at_index != -1) recovery_account_domain(raw.substring(at_index + 1)) else null
            if (matched != null) {
                email_domain = matched
                username = raw.substring(0, at_index)
            } else {
                username = raw
            }
            if (error != null) on_clear_error()
        },
        label = stringResource(R.string.username),
        placeholder = stringResource(R.string.username_placeholder),
        enabled = !is_loading,
        keyboard_options = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboard_actions = KeyboardActions(onDone = { submit() }),
        content_type = ContentType.Username,
    )

    Spacer(Modifier.height(AsterSpacing.md))

    domain_toggle(
        selected = email_domain,
        on_select = {
            email_domain = it
            if (error != null) on_clear_error()
        },
    )

    Spacer(Modifier.height(AsterSpacing.xxxl))

    recovery_actions(
        primary_label = stringResource(R.string.continue_action),
        on_primary = submit,
        primary_enabled = username.isNotBlank() && !is_loading,
        primary_loading = is_loading,
        secondary_label = stringResource(R.string.back_to_sign_in),
        on_secondary = on_back,
        secondary_enabled = !is_loading,
    )
}

@Composable
private fun email_sent_step(
    email: String,
    error: String?,
    is_loading: Boolean,
    resend_seconds: Int,
    on_sign_in: () -> Unit,
    on_resend: () -> Unit,
    on_use_code: () -> Unit,
    on_change_account: () -> Unit,
) {
    val colors = AsterMaterial.colors

    recovery_step_header(
        title = stringResource(R.string.recovery_email_sent),
        description = stringResource(R.string.recovery_email_sent_description),
        email = email,
        on_change_account = on_change_account,
    )
    Spacer(Modifier.height(AsterSpacing.lg))
    Text(
        text = stringResource(R.string.reset_link_sent_next),
        color = colors.text_secondary,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    if (error != null) {
        error_banner(message = error)
        Spacer(Modifier.height(AsterSpacing.lg))
    }

    text_link(
        label = stringResource(R.string.use_recovery_code_instead),
        enabled = !is_loading,
        on_click = on_use_code,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    recovery_actions(
        primary_label = stringResource(R.string.back_to_sign_in),
        on_primary = on_sign_in,
        primary_enabled = !is_loading,
        secondary_label = if (resend_seconds > 0) {
            stringResource(R.string.resend_reset_link_in, resend_seconds)
        } else {
            stringResource(R.string.resend_reset_link)
        },
        on_secondary = on_resend,
        secondary_enabled = !is_loading && resend_seconds == 0,
    )
}

@Composable
private fun code_step(
    email: String,
    is_loading: Boolean,
    error: String?,
    on_verify: (String) -> Unit,
    on_other_ways: () -> Unit,
    on_change_account: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    recovery_step_header(
        title = stringResource(R.string.enter_recovery_code),
        description = stringResource(R.string.enter_recovery_code_description),
        email = email,
        on_change_account = on_change_account,
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
    )

    Spacer(Modifier.height(AsterSpacing.xxxl))

    recovery_actions(
        primary_label = stringResource(R.string.continue_action),
        on_primary = { on_verify(code) },
        primary_enabled = code.isNotBlank() && !is_loading,
        primary_loading = is_loading,
        secondary_label = stringResource(R.string.try_another_way),
        on_secondary = on_other_ways,
        secondary_enabled = !is_loading,
    )
}

@Composable
private fun recovery_option_row(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AsterShapes.item)
            .clickable(enabled = enabled, onClick = on_click)
            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(AsterSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun other_ways_step(
    email: String,
    is_loading: Boolean,
    on_select_code: () -> Unit,
    on_select_email: () -> Unit,
    on_contact_support: () -> Unit,
    on_change_account: () -> Unit,
) {
    recovery_step_header(
        title = stringResource(R.string.recover_choose_method),
        description = stringResource(R.string.other_ways_desc),
        email = email,
        on_change_account = on_change_account,
    )

    Spacer(Modifier.height(AsterSpacing.xl))

    recovery_option_row(
        icon = TablerIcons.Key,
        title = stringResource(R.string.other_way_code_title),
        description = stringResource(R.string.other_way_code_desc),
        enabled = !is_loading,
        on_click = on_select_code,
    )

    AsterDivider()

    recovery_option_row(
        icon = TablerIcons.Mail,
        title = stringResource(R.string.other_way_email_title),
        description = stringResource(R.string.other_way_email_desc),
        enabled = !is_loading,
        on_click = on_select_email,
    )

    AsterDivider()

    Spacer(Modifier.height(AsterSpacing.lg))

    text_link(
        label = stringResource(R.string.other_way_none_title),
        enabled = !is_loading,
        on_click = on_contact_support,
    )
}

@Composable
private fun support_step(
    email: String,
    on_change_account: () -> Unit,
) {
    val context = LocalContext.current

    recovery_step_header(
        title = stringResource(R.string.support_step_title),
        description = stringResource(R.string.support_step_desc),
        email = email,
        on_change_account = on_change_account,
    )

    Spacer(Modifier.height(AsterSpacing.xxxl))

    recovery_actions(
        primary_label = stringResource(R.string.support_email_action),
        on_primary = { open_external_url(context, SUPPORT_MAIL_URL) },
        secondary_label = stringResource(R.string.support_help_center),
        on_secondary = { open_external_url(context, HELP_CENTER_URL) },
    )
}

@Composable
private fun reset_email_confirm_step(
    email: String,
    is_loading: Boolean,
    error: String?,
    on_send: () -> Unit,
    on_back: () -> Unit,
    on_change_account: () -> Unit,
) {
    recovery_step_header(
        title = stringResource(R.string.other_way_email_title),
        description = stringResource(R.string.reset_account_desc),
        email = email,
        on_change_account = on_change_account,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    if (error != null) {
        error_banner(message = error)
        Spacer(Modifier.height(AsterSpacing.lg))
    }

    recovery_actions(
        primary_label = stringResource(R.string.send_reset_link),
        on_primary = on_send,
        primary_enabled = !is_loading,
        primary_loading = is_loading,
        secondary_label = stringResource(R.string.back),
        on_secondary = on_back,
        secondary_enabled = !is_loading,
    )
}

@Composable
private fun password_step(
    email: String,
    password: String,
    confirm: String,
    on_password_change: (String) -> Unit,
    on_confirm_change: (String) -> Unit,
    is_loading: Boolean,
    error: String?,
    on_submit: (String, String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var password_visible by remember { mutableStateOf(false) }
    var confirm_visible by remember { mutableStateOf(false) }
    val confirm_focus = remember { androidx.compose.ui.focus.FocusRequester() }

    recovery_step_header(
        title = stringResource(R.string.create_new_password),
        description = stringResource(R.string.choose_strong_password),
        email = email,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    if (error != null) {
        error_banner(message = error)
        Spacer(Modifier.height(AsterSpacing.lg))
    }

    AsterTextField(
        value = password,
        onValueChange = on_password_change,
        label = stringResource(R.string.new_password),
        placeholder = stringResource(R.string.enter_new_password),
        enabled = !is_loading,
        visual_transformation = if (password_visible) VisualTransformation.None else PasswordVisualTransformation(),
        content_type = ContentType.NewPassword,
        keyboard_options = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
        ),
        keyboard_actions = KeyboardActions(
            onNext = { confirm_focus.requestFocus() },
        ),
        trailing_icon = {
            AsterIconButton(
                icon = if (password_visible) TablerIcons.EyeOff else TablerIcons.Eye,
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
        onValueChange = on_confirm_change,
        label = stringResource(R.string.confirm_password_label),
        placeholder = stringResource(R.string.confirm_new_password),
        enabled = !is_loading,
        visual_transformation = if (confirm_visible) VisualTransformation.None else PasswordVisualTransformation(),
        content_type = ContentType.NewPassword,
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
        trailing_icon = {
            AsterIconButton(
                icon = if (confirm_visible) TablerIcons.EyeOff else TablerIcons.Eye,
                content_description = stringResource(if (confirm_visible) R.string.hide_password else R.string.show_password),
                onClick = { confirm_visible = !confirm_visible },
                tint = colors.text_muted,
            )
        },
        modifier = Modifier.focusRequester(confirm_focus),
    )

    Spacer(Modifier.height(AsterSpacing.xxxl))

    recovery_actions(
        primary_label = stringResource(R.string.reset_password),
        on_primary = { on_submit(password, confirm) },
        primary_enabled = password.isNotBlank() && confirm.isNotBlank() && !is_loading,
        primary_loading = is_loading,
    )
}

@Composable
private fun password_strength_bar(password: String) {
    val colors = AsterMaterial.colors
    val strength = compute_strength(password)
    val (label, color, segments) = when {
        strength < 2 -> Triple(stringResource(R.string.strength_weak), colors.danger, 1)
        strength < 3 -> Triple(stringResource(R.string.strength_fair), colors.warning, 2)
        strength < 4 -> Triple(stringResource(R.string.strength_good), colors.success, 3)
        else -> Triple(stringResource(R.string.strength_strong), colors.success, 4)
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
    account_email: String,
    on_continue: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val request_storage_access = org.astermail.android.util.remember_downloads_permission_gate()
    var codes_visible by remember { mutableStateOf(false) }
    var codes_saved by remember { mutableStateOf(false) }
    val copied_message = stringResource(R.string.codes_copied)
    val saved_message = stringResource(R.string.saved_file, FORGOT_PASSWORD_CODES_FILE_NAME)
    val failed_message = stringResource(R.string.failed_to_save)
    val print_failed_message = stringResource(R.string.print_not_available)

    recovery_step_header(
        title = stringResource(R.string.save_new_recovery_codes),
        description = stringResource(R.string.old_codes_invalidated),
        email = account_email,
    )

    Spacer(Modifier.height(AsterSpacing.xxl))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.recovery_codes_count, codes.size, codes.size),
            color = colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        AsterIconButton(
            icon = if (codes_visible) TablerIcons.EyeOff else TablerIcons.Eye,
            content_description = stringResource(if (codes_visible) R.string.hide_codes else R.string.show_codes),
            onClick = { codes_visible = !codes_visible },
            tint = colors.text_muted,
        )
    }

    Spacer(Modifier.height(AsterSpacing.md))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border_primary, SquircleShape(18.dp))
            .acrylic(colors, SquircleShape(18.dp), colors.bg_secondary)
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

    Spacer(Modifier.height(AsterSpacing.xl))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AsterSecondaryButton(
                label = stringResource(R.string.copy_to_clipboard),
                onClick = {
                    val text = codes.joinToString("\n")
                    val clip = ClipData.newPlainText(context.getString(R.string.recovery_codes), text)
                    clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                    if (write_to_clipboard(context, clip)) {
                        org.astermail.android.util.schedule_sensitive_clipboard_clear(context)
                        org.astermail.android.ui.common.app_toast.show(copied_message)
                    } else {
                        show_copy_failed_toast(context)
                    }
                },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AsterSecondaryButton(
                label = stringResource(R.string.download),
                onClick = {
                    request_storage_access {
                        val saved = download_forgot_password_codes(context, codes)
                        org.astermail.android.ui.common.app_toast.show(if (saved) saved_message else failed_message)
                    }
                },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AsterSecondaryButton(
                label = stringResource(R.string.print_codes),
                onClick = {
                    org.astermail.android.ui.common.print_recovery_codes(
                        context = context,
                        account_email = account_email,
                        codes = codes,
                        on_failure = {
                            org.astermail.android.ui.common.app_toast.show(print_failed_message)
                        },
                    )
                },
            )
        }
    }

    Spacer(Modifier.height(AsterSpacing.xl))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .clickable { codes_saved = !codes_saved }
            .padding(vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = codes_saved,
            onCheckedChange = { codes_saved = it },
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.i_saved_these_codes),
            color = colors.text_primary,
            fontSize = 14.sp,
        )
    }

    Spacer(Modifier.height(AsterSpacing.lg))

    recovery_actions(
        primary_label = stringResource(R.string.continue_action),
        on_primary = on_continue,
        primary_enabled = codes_saved,
    )
}

private const val FORGOT_PASSWORD_CODES_FILE_NAME = "aster-recovery-codes.txt"

private fun download_forgot_password_codes(context: Context, codes: List<String>): Boolean =
    org.astermail.android.util.save_recovery_codes_file(context, FORGOT_PASSWORD_CODES_FILE_NAME, codes)

@Composable
private fun review_row(text: String) {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Check,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.lg))
        Text(
            text = text,
            color = colors.text_primary,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun review_security_step(
    review: org.astermail.android.auth.RecoveryReview?,
    on_sign_in: () -> Unit,
) {
    recovery_step_header(
        title = stringResource(R.string.review_security_title),
        description = stringResource(R.string.review_security_desc),
    )

    Spacer(Modifier.height(AsterSpacing.xl))

    Column(modifier = Modifier.fillMaxWidth()) {
        review_row(stringResource(R.string.review_devices_signed_out))
        AsterDivider()
        if (review?.second_factors_removed != false) {
            review_row(stringResource(R.string.review_two_step_off))
            AsterDivider()
        }
        review_row(
            if (review?.recovery_email_set == true) {
                stringResource(R.string.review_recovery_email_kept)
            } else {
                stringResource(R.string.review_no_recovery_email)
            },
        )
        AsterDivider()
        val codes_left = review?.codes_remaining ?: 0
        review_row(
            pluralStringResource(R.plurals.review_codes_left, codes_left, codes_left),
        )
    }

    Spacer(Modifier.height(AsterSpacing.xxxl))

    recovery_actions(
        primary_label = stringResource(R.string.sign_in),
        on_primary = on_sign_in,
    )
}
