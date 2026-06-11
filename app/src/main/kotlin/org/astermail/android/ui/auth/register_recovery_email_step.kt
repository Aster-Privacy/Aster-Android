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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField

@Composable
fun RegisterRecoveryEmailStep(
    state: RegisterFlowState,
    on_continue: () -> Unit,
    on_skip: () -> Unit,
) {
    val colors = AsterMaterial.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AsterSpacing.xxl),
    ) {
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.add_backup_email),
            color = colors.text_primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.backup_email_description),
            color = colors.text_tertiary,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        AsterTextField(
            value = state.recovery_email.value,
            onValueChange = { state.recovery_email.value = it },
            label = stringResource(R.string.recovery_email),
            placeholder = stringResource(R.string.recovery_email_placeholder),
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            leading_icon = {
                Icon(Icons.Filled.Email, null, tint = colors.text_muted)
            },
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        AsterButton(
            label = stringResource(R.string.continue_action),
            onClick = on_continue,
            enabled = state.recovery_email.value.isNotBlank(),
        )

        Spacer(Modifier.height(AsterSpacing.sm))

        AsterGhostButton(
            label = stringResource(R.string.skip_for_now),
            onClick = on_skip,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AsterSpacing.xxl))
    }
}
