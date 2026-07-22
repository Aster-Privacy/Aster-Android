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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
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
            .padding(horizontal = AsterSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1.4f))

        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(40.dp),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.register_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.register_subtitle),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        androidx.compose.animation.AnimatedVisibility(
            visible = error_message != null,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            Column {
                error_banner(message = error_message ?: "")
                Spacer(Modifier.height(AsterSpacing.lg))
            }
        }

        AsterTextField(
            value = state.username.value,
            onValueChange = { input ->
                state.username.value = input.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
            },
            label = stringResource(R.string.username),
            placeholder = stringResource(R.string.username_placeholder),
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            leading_icon = {
                Icon(
                    imageVector = TablerIcons.At,
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
            label = null,
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
            enabled = state.username.value.length in 3..40,
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

        Spacer(Modifier.weight(1f))
    }
}

private val domain_toggle_height = 40.dp
private val domain_toggle_shape = SquircleShape(12.dp)

@Composable
internal fun domain_toggle(
    selected: String,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val options = listOf("astermail.org", "aster.cx")
    val selected_index = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(domain_toggle_height)
            .background(colors.bg_secondary, domain_toggle_shape)
            .padding(3.dp),
    ) {
        val pill_width = (maxWidth - 6.dp) / options.size
        val pill_offset by animateDpAsState(
            targetValue = pill_width * selected_index,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "domain_pill_offset",
        )

        Box(
            modifier = Modifier
                .offset(x = pill_offset)
                .width(pill_width)
                .fillMaxHeight()
                .background(colors.accent_blue, SquircleShape(9.dp)),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { opt ->
                val active = selected == opt
                val label_color by animateColorAsState(
                    targetValue = if (active) Color.White else colors.text_muted,
                    animationSpec = tween(durationMillis = AsterDuration.short_4, easing = AsterEasing.standard_enter),
                    label = "domain_label_color",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { on_select(opt) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "@$opt",
                        color = label_color,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
