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

package org.astermail.android.ui.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterTextField
import androidx.compose.material3.Text

@Composable
fun folder_unlock_dialog(
    folder_name: String,
    verifying: Boolean,
    error_text: String?,
    on_dismiss: () -> Unit,
    on_submit: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }

    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.folder_locked_title),
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
                enabled = !verifying,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.folder_unlock_action),
                onClick = {
                    if (password.isNotBlank() && !verifying) {
                        submitted = password
                        on_submit(password)
                    }
                },
                enabled = password.isNotBlank() && !verifying,
                is_loading = verifying,
            )
        },
        body = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.folder_locked_description, folder_name),
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                AsterTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = stringResource(R.string.folder_unlock_password_placeholder),
                    singleLine = true,
                    visual_transformation = PasswordVisualTransformation(),
                    keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
                    error_text = error_text.takeIf { password == submitted },
                    enabled = !verifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_unlock_password"),
                )
            }
        },
    )
}
