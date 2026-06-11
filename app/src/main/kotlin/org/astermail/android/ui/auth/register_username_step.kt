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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterTextField

@Composable
fun RegisterUsernameStep(
    state: RegisterFlowState,
    error_message: String?,
    on_next: () -> Unit,
    on_sign_in: () -> Unit,
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
            text = stringResource(R.string.register_title),
            color = colors.text_primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.register_subtitle),
            color = colors.text_tertiary,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        if (error_message != null) {
            error_banner(message = error_message)
            Spacer(Modifier.height(AsterSpacing.lg))
        }

        AsterTextField(
            value = state.username.value,
            onValueChange = { state.username.value = it },
            label = stringResource(R.string.username),
            placeholder = stringResource(R.string.username_placeholder),
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            leading_icon = {
                Icon(
                    imageVector = Icons.Filled.AlternateEmail,
                    contentDescription = null,
                    tint = colors.text_muted,
                )
            },
            content_type = ContentType.NewUsername,
        )

        Spacer(Modifier.height(AsterSpacing.md))

        domain_toggle(
            selected = state.email_domain.value,
            on_select = { state.email_domain.value = it },
        )

        Spacer(Modifier.height(AsterSpacing.lg))

        AsterTextField(
            value = state.display_name.value,
            onValueChange = { state.display_name.value = it },
            label = stringResource(R.string.display_name_optional),
            placeholder = stringResource(R.string.display_name_optional),
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        AsterButton(
            label = stringResource(R.string.next),
            onClick = on_next,
            enabled = state.username.value.isNotBlank(),
        )

        Spacer(Modifier.height(AsterSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.have_account_prompt),
                color = colors.text_tertiary,
                fontSize = 14.sp,
            )
            Text(
                text = stringResource(R.string.sign_in),
                color = colors.accent_blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = on_sign_in),
            )
        }

        Spacer(Modifier.height(AsterSpacing.xxl))
    }
}

@Composable
internal fun domain_toggle(
    selected: String,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val options = listOf("astermail.org", "aster.cx")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.input_bg, SquircleShape(18.dp))
            .border(1.dp, colors.input_border, SquircleShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { opt ->
            val active = selected == opt
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        if (active) colors.accent_blue else Color.Transparent,
                        SquircleShape(14.dp),
                    )
                    .clickable { on_select(opt) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "@$opt",
                    color = if (active) Color.White else colors.text_muted,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}
