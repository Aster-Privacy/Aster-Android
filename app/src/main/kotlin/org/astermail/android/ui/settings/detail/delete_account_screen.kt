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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.DeleteAccountViewModel

@Composable
fun DeleteAccountScreen(
    on_back: () -> Unit,
    on_deleted: () -> Unit,
    view_model: DeleteAccountViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by view_model.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) on_deleted()
    }

    detail_scaffold(title = stringResource(R.string.delete_account), on_back = on_back) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.delete_account_warning_title),
                subtitle = stringResource(R.string.delete_account_warning_body),
            )
        }
        v_gap(AsterSpacing.lg)

        AsterTextField(
            label = stringResource(R.string.confirm_password),
            value = state.password,
            onValueChange = view_model::set_password,
            enabled = !state.is_submitting,
            singleLine = true,
            visual_transformation = if (state.show_password) VisualTransformation.None else PasswordVisualTransformation(),
            keyboard_options = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            trailing_icon = {
                Icon(
                    imageVector = if (state.show_password) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (state.show_password) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                    tint = colors.text_tertiary,
                    modifier = Modifier.clickable(onClick = view_model::toggle_show_password),
                )
            },
        )
        v_gap(AsterSpacing.md)
        AsterTextField(
            label = stringResource(R.string.totp_code_optional),
            value = state.totp_code,
            onValueChange = view_model::set_totp_code,
            enabled = !state.is_submitting,
            singleLine = true,
            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        v_gap(AsterSpacing.md)
        AsterTextField(
            label = stringResource(R.string.delete_account_confirm_phrase),
            value = state.confirm_phrase,
            onValueChange = view_model::set_confirm_phrase,
            enabled = !state.is_submitting,
            singleLine = true,
            keyboard_options = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
            ),
        )

        state.error?.let { err ->
            v_gap(AsterSpacing.md)
            Text(text = err, color = colors.danger, fontSize = 13.sp)
        }

        v_gap(AsterSpacing.lg)
        val required_phrase = stringResource(R.string.delete_account_confirm_word)
        val can_submit = !state.is_submitting &&
            state.password.isNotBlank() &&
            state.confirm_phrase.trim() == required_phrase
        AsterButton(
            label = if (state.is_submitting) stringResource(R.string.deleting_account) else stringResource(R.string.delete_account_button),
            onClick = { view_model.submit() },
            enabled = can_submit,
        )
        v_gap(AsterSpacing.lg)
        Text(
            text = stringResource(R.string.delete_account_grace_note),
            color = colors.text_tertiary,
            fontSize = 12.sp,
        )
        v_gap(AsterSpacing.xxl)
    }
}
