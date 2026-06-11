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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.ChangePasswordViewModel

@Composable
fun ChangePasswordScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    view_model: ChangePasswordViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by view_model.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) {
            on_back()
        }
    }

    detail_scaffold(title = stringResource(R.string.change_password), on_back = on_back) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.update_your_password),
                subtitle = stringResource(R.string.change_password_description),
            )
        }
        v_gap(AsterSpacing.lg)

        password_field(
            label = stringResource(R.string.current_password),
            value = state.current_password,
            on_change = view_model::set_current,
            visible = state.show_current,
            on_toggle = view_model::toggle_show_current,
            enabled = !state.is_submitting,
        )
        v_gap(AsterSpacing.md)
        password_field(
            label = stringResource(R.string.new_password),
            value = state.new_password,
            on_change = view_model::set_new,
            visible = state.show_new,
            on_toggle = view_model::toggle_show_new,
            enabled = !state.is_submitting,
        )
        v_gap(AsterSpacing.md)
        password_field(
            label = stringResource(R.string.confirm_new_password),
            value = state.confirm_password,
            on_change = view_model::set_confirm,
            visible = state.show_confirm,
            on_toggle = view_model::toggle_show_confirm,
            enabled = !state.is_submitting,
        )

        state.error?.let { err ->
            v_gap(AsterSpacing.md)
            Text(
                text = err,
                color = colors.danger,
                fontSize = 13.sp,
            )
        }

        v_gap(AsterSpacing.lg)
        AsterButton(
            label = if (state.is_submitting) stringResource(R.string.updating) else stringResource(R.string.update_password),
            onClick = { view_model.submit() },
            enabled = !state.is_submitting &&
                state.current_password.isNotBlank() &&
                state.new_password.length >= 8 &&
                state.new_password == state.confirm_password,
        )
        v_gap(AsterSpacing.lg)
        Text(
            text = stringResource(R.string.password_protection_note),
            color = colors.text_tertiary,
            fontSize = 12.sp,
        )
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun password_field(
    label: String,
    value: String,
    on_change: (String) -> Unit,
    visible: Boolean,
    on_toggle: () -> Unit,
    enabled: Boolean,
) {
    val colors = AsterMaterial.colors
    AsterTextField(
        label = label,
        value = value,
        onValueChange = on_change,
        enabled = enabled,
        singleLine = true,
        visual_transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboard_options = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        trailing_icon = {
            Icon(
                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (visible) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                tint = colors.text_tertiary,
                modifier = Modifier.clickable(onClick = on_toggle),
            )
        },
    )
}
