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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.twofactor.TwoFactorMode
import org.astermail.android.twofactor.TwoFactorViewModel
import org.astermail.android.twofactor.render_qr_code

@Composable
fun TwoFactorScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val vm: TwoFactorViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load_status() }

    detail_scaffold(title = stringResource(R.string.two_factor_authentication), on_back = on_back) {
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

        when (state.mode) {
            TwoFactorMode.Idle -> idle_panel(state, vm)
            TwoFactorMode.Setup -> setup_panel(state, vm)
            TwoFactorMode.SetupComplete -> backup_codes_panel(
                title = stringResource(R.string.two_factor_enabled_title),
                subtitle = stringResource(R.string.two_factor_enabled_subtitle),
                codes = state.backup_codes,
                on_done = { vm.acknowledge_backup_codes() },
            )
            TwoFactorMode.Disable -> disable_panel(state, vm)
            TwoFactorMode.Regenerate -> regenerate_panel(state, vm)
            TwoFactorMode.RegenerateComplete -> backup_codes_panel(
                title = stringResource(R.string.new_backup_codes_title),
                subtitle = stringResource(R.string.new_backup_codes_subtitle),
                codes = state.backup_codes,
                on_done = { vm.acknowledge_backup_codes() },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun idle_panel(state: org.astermail.android.twofactor.TwoFactorUiState, vm: TwoFactorViewModel) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = if (state.enabled) colors.success else colors.text_tertiary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.enabled) {
                        stringResource(R.string.backup_codes_remaining, state.backup_codes_remaining)
                    } else {
                        stringResource(R.string.add_second_factor)
                    },
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }
    }
    v_gap(AsterSpacing.lg)
    if (state.enabled) {
        AsterDestructiveButton(label = stringResource(R.string.turn_off_two_factor), onClick = { vm.start_disable() })
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(label = stringResource(R.string.generate_new_backup_codes), onClick = { vm.start_regenerate() })
    } else {
        AsterButton(label = stringResource(R.string.setup_two_factor), onClick = { vm.start_setup() })
    }
}

@Composable
private fun setup_panel(state: org.astermail.android.twofactor.TwoFactorUiState, vm: TwoFactorViewModel) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    section_label(stringResource(R.string.scan_or_import))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            val uri = state.setup_otpauth_uri
            if (uri != null) {
                val bitmap = remember(uri) { render_qr_code(uri, 600) }
                if (bitmap != null) {
                    androidx.compose.runtime.DisposableEffect(bitmap) {
                        onDispose { runCatching { bitmap.recycle() } }
                    }
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(SquircleShape(18.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(8.dp),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.two_factor_qr_code),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.size(AsterSpacing.md))
                }
            }
            Text(
                text = stringResource(R.string.secret),
                color = colors.text_secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(AsterSpacing.xs))
            Text(
                text = state.setup_secret ?: "",
                color = colors.text_primary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.size(AsterSpacing.md))
            Row {
                AsterGhostButton(
                    label = stringResource(R.string.copy_secret),
                    onClick = {
                        val text = state.setup_secret.orEmpty()
                        if (text.isNotEmpty()) copy_to_clipboard(context, "TOTP secret", text)
                    },
                )
                Spacer(Modifier.size(AsterSpacing.sm))
                if (state.setup_otpauth_uri != null) {
                    AsterGhostButton(
                        label = stringResource(R.string.open_in_app),
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(state.setup_otpauth_uri)),
                                )
                            } catch (_: Throwable) {
                                copy_to_clipboard(context, "TOTP setup link", state.setup_otpauth_uri)
                            }
                        },
                    )
                }
            }
        }
    }
    v_gap(AsterSpacing.lg)
    section_label(stringResource(R.string.enter_6_digit_code))
    AsterTextField(
        value = state.code_input,
        onValueChange = { vm.update_code(it) },
        placeholder = stringResource(R.string.code_placeholder),
        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    v_gap(AsterSpacing.lg)
    AsterButton(
        label = if (state.is_busy) stringResource(R.string.verifying) else stringResource(R.string.verify_and_enable),
        onClick = { vm.verify_setup() },
    )
    v_gap(AsterSpacing.sm)
    AsterSecondaryButton(label = stringResource(R.string.cancel), onClick = { vm.cancel_action() })
}

@Composable
private fun disable_panel(state: org.astermail.android.twofactor.TwoFactorUiState, vm: TwoFactorViewModel) {
    section_label(stringResource(R.string.confirm_disable))
    Text(
        text = stringResource(R.string.confirm_disable_description),
        color = AsterMaterial.colors.text_tertiary,
        fontSize = 13.sp,
    )
    v_gap(AsterSpacing.md)
    AsterTextField(
        value = state.password_input,
        onValueChange = { vm.update_password(it) },
        label = stringResource(R.string.password),
        placeholder = stringResource(R.string.password_dots),
        visual_transformation = PasswordVisualTransformation(),
        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    v_gap(AsterSpacing.md)
    AsterTextField(
        value = state.code_input,
        onValueChange = { vm.update_code(it) },
        label = stringResource(R.string.authenticator_code),
        placeholder = stringResource(R.string.code_placeholder),
        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    v_gap(AsterSpacing.lg)
    AsterDestructiveButton(
        label = if (state.is_busy) stringResource(R.string.disabling) else stringResource(R.string.disable_two_factor),
        onClick = { vm.confirm_disable() },
    )
    v_gap(AsterSpacing.sm)
    AsterSecondaryButton(label = stringResource(R.string.cancel), onClick = { vm.cancel_action() })
}

@Composable
private fun regenerate_panel(state: org.astermail.android.twofactor.TwoFactorUiState, vm: TwoFactorViewModel) {
    section_label(stringResource(R.string.generate_new_backup_codes_section))
    Text(
        text = stringResource(R.string.generate_backup_codes_description),
        color = AsterMaterial.colors.text_tertiary,
        fontSize = 13.sp,
    )
    v_gap(AsterSpacing.md)
    AsterTextField(
        value = state.code_input,
        onValueChange = { vm.update_code(it) },
        label = stringResource(R.string.authenticator_code),
        placeholder = stringResource(R.string.code_placeholder),
        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    v_gap(AsterSpacing.lg)
    AsterButton(
        label = if (state.is_busy) stringResource(R.string.generating) else stringResource(R.string.generate_codes),
        onClick = { vm.confirm_regenerate() },
    )
    v_gap(AsterSpacing.sm)
    AsterSecondaryButton(label = stringResource(R.string.cancel), onClick = { vm.cancel_action() })
}

@Composable
private fun backup_codes_panel(
    title: String,
    subtitle: String,
    codes: List<String>,
    on_done: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    section_label(title)
    Text(text = subtitle, color = colors.text_tertiary, fontSize = 13.sp)
    v_gap(AsterSpacing.md)
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            codes.forEach { code ->
                Text(
                    text = code,
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.size(AsterSpacing.xs))
            }
        }
    }
    v_gap(AsterSpacing.md)
    AsterGhostButton(
        label = stringResource(R.string.copy_all_codes),
        onClick = {
            val joined = codes.joinToString("\n")
            if (joined.isNotEmpty()) copy_to_clipboard(context, context.getString(R.string.backup_codes_clipboard_label), joined)
        },
    )
    v_gap(AsterSpacing.sm)
    AsterButton(label = stringResource(R.string.done), onClick = on_done)
}

private fun copy_to_clipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
}
