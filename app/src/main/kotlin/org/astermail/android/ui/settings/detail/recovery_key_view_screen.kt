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
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.SettingsViewModel

@Composable
fun RecoveryKeyViewScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val vm: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var is_verifying by remember { mutableStateOf(false) }
    val codes = remember(unlocked) {
        if (unlocked) vm.get_recovery_codes() else null
    }

    detail_scaffold(title = stringResource(R.string.recovery_key), on_back = on_back) {
        if (!unlocked) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AsterSpacing.lg),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enter_your_password),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(AsterSpacing.xs))
                        Text(
                            text = stringResource(R.string.password_required_for_recovery),
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
                placeholder = stringResource(R.string.password),
                visual_transformation = PasswordVisualTransformation(),
                keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            v_gap(AsterSpacing.lg)
            AsterButton(
                label = if (is_verifying) stringResource(R.string.verifying) else stringResource(R.string.view_recovery_key),
                onClick = {
                    if (password.isBlank()) {
                        error = context.getString(R.string.please_enter_password)
                        return@AsterButton
                    }
                    is_verifying = true
                    scope.launch {
                        val valid = vm.verify_password(password)
                        is_verifying = false
                        if (valid) {
                            unlocked = true
                        } else {
                            error = context.getString(R.string.incorrect_password)
                        }
                    }
                },
                enabled = !is_verifying && password.isNotBlank(),
                is_loading = is_verifying,
            )
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AsterSpacing.lg),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.store_somewhere_safe),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(AsterSpacing.xs))
                        Text(
                            text = stringResource(R.string.recovery_key_warning),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            v_gap(AsterSpacing.lg)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .background(colors.bg_secondary, SquircleShape(18.dp))
                        .padding(AsterSpacing.lg)
                        .fillMaxWidth(),
                ) {
                    if (codes.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.no_recovery_key_found),
                            color = colors.text_tertiary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    } else {
                        codes.forEachIndexed { index, code ->
                            if (index > 0) Spacer(Modifier.size(AsterSpacing.sm))
                            Text(
                                text = code,
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = !codes.isNullOrEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    v_gap(AsterSpacing.lg)
                    AsterSecondaryButton(
                        label = stringResource(R.string.copy_to_clipboard),
                        onClick = {
                            val text = codes?.joinToString("\n").orEmpty()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(context.getString(R.string.clipboard_label_recovery_key), text)
                            clip.description.extras = android.os.PersistableBundle().apply {
                                putBoolean("android.content.extra.IS_SENSITIVE", true)
                            }
                            cm.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
