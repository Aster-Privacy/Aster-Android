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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    on_recover: (String) -> Unit,
    on_dismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AsterDialog(
        on_dismiss = { if (!is_recovering) on_dismiss() },
        title = stringResource(R.string.recover_data_title),
        message = stringResource(R.string.recover_data_description),
        body = {
            AsterTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.recover_data_previous_password),
                visual_transformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !is_recovering,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                enabled = !is_recovering,
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.recover_data_button),
                enabled = password.isNotEmpty() && !is_recovering,
                is_loading = is_recovering,
                onClick = { on_recover(password) },
            )
        },
    )
}
