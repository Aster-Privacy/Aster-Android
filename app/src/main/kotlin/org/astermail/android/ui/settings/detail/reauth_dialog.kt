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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.ReauthPrompt

@Composable
fun reauth_dialog(
    title: String,
    message: String,
    confirm_label: String,
    prompt: ReauthPrompt,
    on_dismiss: () -> Unit,
    on_confirm: (password: String, code: String?) -> Unit,
    confirm_style: DialogConfirmStyle = DialogConfirmStyle.primary,
) {
    val colors = AsterMaterial.colors
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = title,
        message = message,
        confirm_label = confirm_label,
        cancel_label = stringResource(R.string.cancel),
        confirm_style = confirm_style,
        confirm_enabled = password.isNotBlank() &&
            (!prompt.needs_code || code.isNotBlank()) &&
            !prompt.busy,
        is_busy = prompt.busy,
        dismiss_on_confirm = false,
        on_confirm = { on_confirm(password, code.trim().takeIf { prompt.needs_code && it.isNotEmpty() }) },
        extra_content = {
            Column {
                prompt.error?.let {
                    Text(
                        text = it,
                        color = colors.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("reauth_error"),
                    )
                    Spacer(Modifier.height(AsterSpacing.md))
                }
                AsterTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    placeholder = stringResource(R.string.enter_your_password),
                    visual_transformation = PasswordVisualTransformation(),
                    content_type = ContentType.Password,
                    keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("reauth_password"),
                )
                if (prompt.needs_code) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    AsterTextField(
                        value = code,
                        onValueChange = { value -> code = value.filter { it.isLetterOrDigit() || it == '-' }.take(14) },
                        singleLine = true,
                        label = stringResource(R.string.authenticator_code),
                        placeholder = stringResource(R.string.code_placeholder),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().testTag("reauth_code"),
                    )
                }
            }
        },
    )
}
