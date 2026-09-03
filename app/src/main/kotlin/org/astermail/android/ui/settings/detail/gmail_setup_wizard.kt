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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.ExternalLink
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Key
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Mail
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.common.start_external_intent

internal const val gmail_wizard_two_step_url =
    "https://myaccount.google.com/signinoptions/two-step-verification"

internal const val gmail_wizard_app_password_url = "https://myaccount.google.com/apppasswords"

private const val gmail_wizard_total_steps = 4

private val gmail_email_pattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

private val gmail_wizard_step_icons = listOf(
    TablerIcons.Lock,
    TablerIcons.Key,
    TablerIcons.Mail,
    TablerIcons.CircleCheck,
)

@Composable
internal fun gmail_setup_wizard(
    email: String,
    password: String,
    on_email_change: (String) -> Unit,
    on_password_change: (String) -> Unit,
    on_dismiss: () -> Unit,
    on_connect: () -> Unit,
    is_submitting: Boolean,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(1) }
    var password_visible by remember { mutableStateOf(false) }
    val email_is_valid = remember(email) { gmail_email_pattern.matches(email.trim()) }
    val password_is_filled = password.isNotBlank()
    val can_advance = (step != 3 || email_is_valid) && (step != 4 || password_is_filled)

    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.ext_gmail_wizard_title),
        body = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(SquircleShape(10.dp))
                            .background(colors.bg_tertiary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_gmail),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(AsterSpacing.md))
                    Text(
                        text = stringResource(
                            R.string.ext_gmail_wizard_progress,
                            step,
                            gmail_wizard_total_steps,
                        ),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (index in 0 until gmail_wizard_total_steps) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(SquircleShape(2.dp))
                                .background(
                                    if (index < step) colors.accent_blue else colors.bg_tertiary,
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(AsterSpacing.lg))
                gmail_wizard_step_body(
                    icon = gmail_wizard_step_icons[step - 1],
                    title = stringResource(
                        when (step) {
                            1 -> R.string.ext_gmail_wizard_step_1_title
                            2 -> R.string.ext_gmail_wizard_step_2_title
                            3 -> R.string.ext_gmail_wizard_step_3_title
                            else -> R.string.ext_gmail_wizard_step_4_title
                        },
                    ),
                    body = stringResource(
                        when (step) {
                            1 -> R.string.ext_gmail_wizard_step_1_body
                            2 -> R.string.ext_gmail_wizard_step_2_body
                            3 -> R.string.ext_gmail_wizard_step_3_body
                            else -> R.string.ext_gmail_wizard_step_4_body
                        },
                    ),
                )
                when (step) {
                    1 -> {
                        Spacer(Modifier.height(AsterSpacing.md))
                        gmail_wizard_link(
                            label = stringResource(R.string.ext_gmail_wizard_step_1_action),
                            on_click = { open_gmail_wizard_url(context, gmail_wizard_two_step_url) },
                        )
                    }

                    2 -> {
                        Spacer(Modifier.height(AsterSpacing.md))
                        gmail_wizard_link(
                            label = stringResource(R.string.ext_app_password_create),
                            on_click = {
                                open_gmail_wizard_url(context, gmail_wizard_app_password_url)
                            },
                        )
                        Spacer(Modifier.height(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.ext_gmail_wizard_note),
                            color = colors.text_muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }

                    3 -> {
                        Spacer(Modifier.height(AsterSpacing.md))
                        AsterTextField(
                            value = email,
                            onValueChange = on_email_change,
                            label = stringResource(R.string.ext_field_email),
                            placeholder = "you@gmail.com",
                            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Email),
                            content_type = ContentType.EmailAddress,
                        )
                    }

                    else -> {
                        Spacer(Modifier.height(AsterSpacing.md))
                        AsterTextField(
                            value = password,
                            onValueChange = on_password_change,
                            label = stringResource(R.string.ext_gmail_wizard_password_label),
                            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visual_transformation = if (password_visible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailing_icon = {
                                AsterIconButton(
                                    icon = if (password_visible) TablerIcons.EyeOff else TablerIcons.Eye,
                                    content_description = stringResource(
                                        if (password_visible) {
                                            R.string.hide_password
                                        } else {
                                            R.string.show_password
                                        },
                                    ),
                                    onClick = { password_visible = !password_visible },
                                    tint = colors.text_muted,
                                )
                            },
                            content_type = ContentType.Password,
                        )
                    }
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = if (step == 1) {
                    stringResource(R.string.cancel)
                } else {
                    stringResource(R.string.back)
                },
                onClick = { if (step == 1) on_dismiss() else step -= 1 },
                enabled = !is_submitting,
            )
            AsterDialogPrimaryButton(
                label = if (step == gmail_wizard_total_steps) {
                    stringResource(R.string.ext_gmail_wizard_connect)
                } else {
                    stringResource(R.string.next)
                },
                onClick = {
                    if (step == gmail_wizard_total_steps) on_connect() else step += 1
                },
                enabled = can_advance && !is_submitting,
                is_loading = is_submitting,
            )
        },
    )
}

@Composable
private fun gmail_wizard_step_body(icon: ImageVector, title: String, body: String) {
    val colors = AsterMaterial.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = body,
                color = colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun gmail_wizard_link(label: String, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .clip(SquircleShape(8.dp))
            .clickable(onClick = on_click)
            .padding(vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = TablerIcons.ExternalLink,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(15.dp),
        )
    }
}

private fun open_gmail_wizard_url(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    start_external_intent(context, intent)
}
