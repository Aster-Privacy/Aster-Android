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
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Lock
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.auth.hash_recovery_code
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
import org.astermail.android.design.components.AsterActionRow
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.common.print_recovery_codes
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard

@Composable
fun RecoveryCodesScreen(on_back: () -> Unit) {
    org.astermail.android.ui.common.secure_screen()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var totp_code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var is_verifying by remember { mutableStateOf(false) }
    var step_up_token by remember { mutableStateOf<String?>(null) }
    var used_hashes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var total_codes by remember { mutableStateOf(0) }
    var created_at by remember { mutableStateOf<String?>(null) }
    var codes by remember { mutableStateOf<List<String>>(emptyList()) }
    var is_rotating by remember { mutableStateOf(false) }
    var show_rotate_confirm by remember { mutableStateOf(false) }
    var rotated by remember { mutableStateOf(false) }
    var codes_saved by remember { mutableStateOf(false) }

    val totp_required = state.security_status?.totp_enabled == true
    val account_email = state.user?.email.orEmpty()

    LaunchedEffect(Unit) { vm.load_security_status() }

    detail_scaffold(title = stringResource(R.string.recovery_codes), on_back = on_back) {
        if (step_up_token == null) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AsterSpacing.lg),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = TablerIcons.Lock,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.confirm_its_you),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(AsterSpacing.xs))
                        Text(
                            text = stringResource(R.string.recovery_step_up_description),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            v_gap(AsterSpacing.lg)
            AsterTextField(
                value = password,
                onValueChange = { password = it; error = null },
                placeholder = stringResource(R.string.enter_your_password),
                label = stringResource(R.string.password),
                visual_transformation = PasswordVisualTransformation(),
                content_type = ContentType.Password,
                keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            if (totp_required) {
                v_gap(AsterSpacing.md)
                AsterTextField(
                    value = totp_code,
                    onValueChange = { totp_code = it.filter { ch -> ch.isLetterOrDigit() }; error = null },
                    placeholder = stringResource(R.string.authenticator_code),
                    label = stringResource(R.string.authenticator_code),
                    keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            AnimatedVisibility(
                visible = error != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    v_gap(AsterSpacing.sm)
                    Text(text = error.orEmpty(), color = colors.danger, fontSize = 13.sp)
                }
            }
            v_gap(AsterSpacing.lg)
            AsterButton(
                label = stringResource(R.string.show_codes),
                onClick = {
                    is_verifying = true
                    error = null
                    scope.launch {
                        val result = vm.verify_recovery_step_up(
                            password,
                            totp_code.trim().ifBlank { null },
                        )
                        is_verifying = false
                        result
                            .onSuccess { response ->
                                password = ""
                                totp_code = ""
                                step_up_token = response.step_up_token
                                used_hashes = response.codes
                                    .filter { it.status == "used" }
                                    .map { it.code_hash }
                                    .toSet()
                                total_codes = response.codes.size
                                codes = vm.get_recovery_codes().orEmpty()
                                created_at = format_codes_date(vm.recovery_codes_status_now()?.created_at)
                            }
                            .onFailure { error = vm.error_text(it) }
                    }
                },
                enabled = !is_verifying &&
                    password.isNotBlank() &&
                    (!totp_required || totp_code.trim().length >= 6),
                is_loading = is_verifying,
            )
        } else {
            val remaining = if (total_codes > 0) total_codes - used_hashes.size else codes.size
            val is_low = remaining in 1..3
            Text(
                text = stringResource(R.string.recovery_codes_view_desc),
                color = colors.text_secondary,
                fontSize = 13.sp,
            )
            created_at?.let { stamp ->
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.recovery_codes_print_created, stamp),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
            if (total_codes > 0) {
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.recovery_codes_remaining, remaining, total_codes),
                    color = if (is_low) colors.danger else colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
            if (is_low) {
                v_gap(AsterSpacing.sm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = TablerIcons.AlertTriangle,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.recovery_codes_low_warning),
                        color = colors.danger,
                        fontSize = 12.sp,
                    )
                }
            }

            v_gap(AsterSpacing.lg)

            if (codes.isEmpty()) {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AsterSpacing.lg),
                    ) {
                        Text(
                            text = stringResource(R.string.recovery_codes_unavailable),
                            color = colors.text_tertiary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            } else {
                recovery_codes_status_grid(codes = codes, used_hashes = used_hashes)
                v_gap(AsterSpacing.lg)
                AsterActionRow(
                    modifier = Modifier.fillMaxWidth(),
                    spacing = AsterSpacing.sm,
                ) {
                    AsterSecondaryButton(
                        label = stringResource(R.string.copy_to_clipboard),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val text = codes.joinToString("\n")
                            val clip = ClipData.newPlainText(
                                context.getString(R.string.clipboard_label_recovery_key),
                                text,
                            )
                            clip.description.extras = android.os.PersistableBundle().apply {
                                putBoolean("android.content.extra.IS_SENSITIVE", true)
                            }
                            if (write_to_clipboard(context, clip)) {
                                org.astermail.android.util.schedule_sensitive_clipboard_clear(context, text)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.codes_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                show_copy_failed_toast(context)
                            }
                        },
                    )
                    AsterSecondaryButton(
                        label = stringResource(R.string.download),
                        modifier = Modifier.weight(1f),
                        onClick = { save_recovery_codes(context, codes) },
                    )
                    AsterSecondaryButton(
                        label = stringResource(R.string.print_codes),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            print_recovery_codes(
                                context = context,
                                account_email = account_email,
                                codes = codes,
                                on_failure = {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.print_not_available),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        },
                    )
                }
            }

            if (rotated) {
                v_gap(AsterSpacing.lg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleShape(AsterRadius.md))
                        .clickable { codes_saved = !codes_saved }
                        .padding(vertical = AsterSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = codes_saved, onCheckedChange = { codes_saved = it })
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.i_saved_these_codes),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                    )
                }
            }

            v_gap(AsterSpacing.lg)

            AsterButton(
                label = stringResource(R.string.get_new_codes),
                onClick = { show_rotate_confirm = true },
                enabled = !is_rotating && (!rotated || codes_saved),
                is_loading = is_rotating,
            )

            AnimatedVisibility(
                visible = error != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    v_gap(AsterSpacing.sm)
                    Text(text = error.orEmpty(), color = colors.danger, fontSize = 13.sp)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_rotate_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_rotate_confirm = false },
            title = stringResource(R.string.get_new_codes_title),
            message = stringResource(R.string.get_new_codes_message),
            confirm_label = stringResource(R.string.get_new_codes),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                show_rotate_confirm = false
                val token = step_up_token ?: return@AsterAlertDialog
                is_rotating = true
                error = null
                scope.launch {
                    val result = vm.rotate_recovery_codes_now(token)
                    is_rotating = false
                    result
                        .onSuccess { new_codes ->
                            codes = new_codes
                            used_hashes = emptySet()
                            total_codes = new_codes.size
                            created_at = format_codes_date(null)
                            rotated = true
                            codes_saved = false
                        }
                        .onFailure { error = vm.error_text(it) }
                }
            },
        )
    }
}

private fun format_codes_date(iso: String?): String {
    val formatter = DateFormat.getDateInstance(DateFormat.LONG)
    if (iso.isNullOrBlank()) return formatter.format(Date())
    return runCatching {
        formatter.format(Date(java.time.Instant.parse(iso).toEpochMilli()))
    }.getOrDefault(iso)
}

@Composable
private fun recovery_codes_status_grid(codes: List<String>, used_hashes: Set<String>) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(AsterRadius.lg))
            .acrylic(colors, SquircleShape(AsterRadius.lg), colors.bg_secondary)
            .border(1.dp, colors.border_primary, SquircleShape(AsterRadius.lg))
            .padding(AsterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        codes.chunked(2).forEachIndexed { row_index, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                pair.forEachIndexed { column_index, code ->
                    val is_used = used_hashes.contains(runCatching { hash_recovery_code(code) }.getOrNull())
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${row_index * 2 + column_index + 1}",
                            color = colors.text_muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            text = code,
                            color = if (is_used) colors.text_muted else colors.text_primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp,
                            textDecoration = if (is_used) TextDecoration.LineThrough else null,
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
