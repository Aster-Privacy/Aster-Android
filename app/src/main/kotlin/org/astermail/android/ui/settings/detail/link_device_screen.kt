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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Key
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.devices.LinkDeviceError
import org.astermail.android.devices.LinkDeviceStep
import org.astermail.android.devices.LinkDeviceViewModel
import org.astermail.android.ui.settings.device_badge
import org.astermail.android.ui.settings.link_device_icon
import org.astermail.android.ui.settings.link_device_step_icon

private const val MAX_MACHINE_NAME_CHARS = 64

private val link_device_step_titles = listOf(
    R.string.link_device_step_code,
    R.string.link_device_step_confirm,
    R.string.link_device_step_done,
)

@Composable
fun LinkDeviceScreen(
    on_back: () -> Unit,
    vm: LinkDeviceViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val step_index = when (state.step) {
        LinkDeviceStep.INPUT -> 0
        LinkDeviceStep.CONFIRM -> 1
        LinkDeviceStep.SUCCESS -> 2
    }

    detail_scaffold(title = stringResource(R.string.link_device_title), on_back = on_back) {
        link_device_progress(step_index)
        v_gap(AsterSpacing.xl)

        when (state.step) {
            LinkDeviceStep.INPUT -> link_device_hero(
                icon = TablerIcons.Key,
                tint = colors.accent_blue,
                title = stringResource(R.string.link_device_hero_title),
                description = stringResource(R.string.link_device_description),
            )

            LinkDeviceStep.CONFIRM -> link_device_hero(
                icon = link_device_icon(state.pending_device?.device_type.orEmpty()),
                tint = colors.accent_blue,
                title = stringResource(R.string.link_device_confirm_title),
                description = stringResource(R.string.link_device_confirm_description),
            )

            LinkDeviceStep.SUCCESS -> link_device_hero(
                icon = TablerIcons.Check,
                tint = colors.success,
                title = stringResource(R.string.link_device_success_title),
                description = stringResource(
                    R.string.link_device_success_description,
                    state.linked_device_name.take(MAX_MACHINE_NAME_CHARS).ifBlank {
                        stringResource(R.string.link_device_unnamed)
                    },
                ),
            )
        }
        v_gap(AsterSpacing.xl)

        state.error?.let {
            error_banner(stringResource(link_device_error_res(it)))
            v_gap(AsterSpacing.lg)
        }

        when (state.step) {
            LinkDeviceStep.INPUT -> {
                AsterTextField(
                    value = state.code_input,
                    onValueChange = { vm.on_code_change(it) },
                    label = stringResource(R.string.link_device_code_label),
                    placeholder = stringResource(R.string.link_device_code_placeholder),
                    enabled = !state.is_verifying,
                    keyboard_options = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    keyboard_actions = KeyboardActions(
                        onDone = {
                            if (state.is_code_complete && !state.is_verifying) vm.verify_code()
                        },
                    ),
                    leading_icon = { Icon(TablerIcons.Key, null, tint = colors.text_muted) },
                )
                v_gap(AsterSpacing.lg)

                AsterButton(
                    label = stringResource(R.string.link_device_continue),
                    onClick = { vm.verify_code() },
                    enabled = state.is_code_complete && !state.is_verifying,
                    is_loading = state.is_verifying,
                )
            }

            LinkDeviceStep.CONFIRM -> {
                val device = state.pending_device
                val device_type = device?.device_type.orEmpty()

                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AsterSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = link_device_icon(device_type),
                            contentDescription = null,
                            tint = colors.text_tertiary,
                            modifier = Modifier
                                .size(44.dp)
                                .padding(11.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = AsterSpacing.md),
                        ) {
                            Text(
                                text = device?.machine_name
                                    ?.take(MAX_MACHINE_NAME_CHARS)
                                    ?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.link_device_unnamed),
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(link_device_type_res(device_type)),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                            )
                        }
                        device_badge(
                            label = stringResource(R.string.link_device_pending_badge),
                            color = colors.accent_blue,
                        )
                    }
                }
                v_gap(AsterSpacing.lg)

                Text(
                    text = stringResource(R.string.link_device_confirm_warning),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
                v_gap(AsterSpacing.lg)

                AsterButton(
                    label = stringResource(R.string.link_device_confirm_action),
                    onClick = { vm.confirm_link() },
                    enabled = !state.is_confirming,
                    is_loading = state.is_confirming,
                )
                v_gap(AsterSpacing.sm)
                AsterGhostButton(
                    label = stringResource(R.string.cancel),
                    onClick = { vm.cancel_confirm() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.is_confirming,
                )
            }

            LinkDeviceStep.SUCCESS -> {
                AsterButton(
                    label = stringResource(R.string.link_device_link_another),
                    onClick = { vm.start_over() },
                )
                v_gap(AsterSpacing.sm)
                AsterGhostButton(
                    label = stringResource(R.string.done),
                    onClick = on_back,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun link_device_hero(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(44.dp),
        )
        v_gap(AsterSpacing.md)
        Text(
            text = title,
            color = colors.text_primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        v_gap(AsterSpacing.xs)
        Text(
            text = description,
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun link_device_progress(current: Int) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            link_device_step_titles.indices.forEach { index ->
                val reached = index <= current
                val done = index < current
                val tint = when {
                    done -> colors.success
                    reached -> colors.accent_blue
                    else -> colors.text_muted
                }
                Icon(
                    imageVector = if (done) TablerIcons.Check else link_device_step_icon(index),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                if (index < link_device_step_titles.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AsterSpacing.sm)
                            .height(2.dp)
                            .background(
                                if (index < current) colors.success else colors.border_secondary,
                            ),
                    )
                }
            }
        }
        v_gap(AsterSpacing.sm)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            link_device_step_titles.forEachIndexed { index, label_res ->
                Text(
                    text = stringResource(label_res),
                    color = if (index <= current) colors.text_secondary else colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun link_device_error_res(error: LinkDeviceError): Int = when (error) {
    LinkDeviceError.INVALID_CODE -> R.string.link_device_error_invalid
    LinkDeviceError.EXPIRED_CODE -> R.string.link_device_error_expired
    LinkDeviceError.ALREADY_LINKED -> R.string.link_device_error_already_linked
    LinkDeviceError.UPGRADE_REQUIRED -> R.string.link_device_error_upgrade_required
    LinkDeviceError.RATE_LIMITED -> R.string.link_device_error_rate_limited
    LinkDeviceError.SESSION_EXPIRED -> R.string.link_device_error_session
    LinkDeviceError.UNAVAILABLE -> R.string.link_device_error_unavailable
    LinkDeviceError.FAILED -> R.string.link_device_error_failed
}

private fun link_device_type_res(device_type: String): Int = when (device_type.lowercase()) {
    "bridge" -> R.string.link_device_type_bridge
    "desktop" -> R.string.link_device_type_desktop
    else -> R.string.link_device_type_other
}
