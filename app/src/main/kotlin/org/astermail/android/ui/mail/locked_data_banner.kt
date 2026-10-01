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

package org.astermail.android.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.mail.LockedDataRecoveryOutcome
import org.astermail.android.ui.common.pill_toggle

@Composable
fun locked_data_banner(
    on_recover: () -> Unit,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(SquircleShape(12.dp))
            .background(colors.bg_card)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = TablerIcons.Lock,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.locked_data_banner_message),
                color = colors.text_primary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.locked_data_banner_dismiss),
                color = colors.text_muted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(SquircleShape(8.dp))
                    .clickable(role = Role.Button, onClick = on_dismiss)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.locked_data_banner_action),
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(SquircleShape(8.dp))
                    .clickable(role = Role.Button, onClick = on_recover)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun recover_data_dialog(
    is_recovering: Boolean,
    outcome: LockedDataRecoveryOutcome?,
    on_recover: (String) -> Unit,
    on_recover_with_code: (String) -> Unit,
    on_clear_outcome: () -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var use_password by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(outcome) {
        if (outcome == LockedDataRecoveryOutcome.PARTIAL) use_password = true
    }

    val notice_res = when (outcome) {
        LockedDataRecoveryOutcome.PARTIAL -> R.string.recover_data_partial
        LockedDataRecoveryOutcome.NO_MATCH -> R.string.recover_data_no_match
        LockedDataRecoveryOutcome.CODE_NO_MATCH -> R.string.recover_data_code_no_match
        LockedDataRecoveryOutcome.RATE_LIMITED -> R.string.recover_data_rate_limited
        LockedDataRecoveryOutcome.FAILED -> R.string.recover_data_failed
        LockedDataRecoveryOutcome.SUCCESS, null -> null
    }
    val can_submit = if (use_password) password.isNotEmpty() else code.isNotBlank()
    val submit = {
        if (use_password) on_recover(password) else on_recover_with_code(code.trim())
    }

    AsterDialog(
        on_dismiss = { if (!is_recovering) on_dismiss() },
        title = stringResource(R.string.recover_data_title),
        body = {
            Column(modifier = Modifier.fillMaxWidth()) {
                pill_toggle(
                    labels = listOf(
                        stringResource(R.string.recovery_code),
                        stringResource(R.string.recover_data_previous_password),
                    ),
                    selected_index = if (use_password) 1 else 0,
                    on_select = { index ->
                        use_password = index == 1
                        on_clear_outcome()
                    },
                    enabled = !is_recovering,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(
                        if (use_password) R.string.recover_data_description else R.string.recover_data_code_description,
                    ),
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(AsterSpacing.lg))
                if (use_password) {
                    AsterTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (outcome != null && outcome != LockedDataRecoveryOutcome.PARTIAL) on_clear_outcome()
                        },
                        label = stringResource(R.string.recover_data_previous_password),
                        visual_transformation = PasswordVisualTransformation(),
                        keyboard_options = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboard_actions = KeyboardActions(onDone = { if (can_submit && !is_recovering) submit() }),
                        singleLine = true,
                        enabled = !is_recovering,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    AsterTextField(
                        value = code,
                        onValueChange = {
                            code = it.uppercase()
                            if (outcome != null) on_clear_outcome()
                        },
                        label = stringResource(R.string.recovery_code),
                        placeholder = stringResource(R.string.recovery_code_placeholder),
                        keyboard_options = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboard_actions = KeyboardActions(onDone = { if (can_submit && !is_recovering) submit() }),
                        singleLine = true,
                        enabled = !is_recovering,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (notice_res != null) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = stringResource(notice_res),
                        color = if (outcome == LockedDataRecoveryOutcome.PARTIAL) colors.text_secondary else colors.danger,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                enabled = !is_recovering,
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.recover_data_button),
                enabled = can_submit && !is_recovering,
                is_loading = is_recovering,
                onClick = submit,
            )
        },
    )
}
