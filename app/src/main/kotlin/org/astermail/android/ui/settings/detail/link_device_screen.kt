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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.DeviceDesktop
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

private const val MAX_MACHINE_NAME_CHARS = 64

@Composable
fun LinkDeviceScreen(
    on_back: () -> Unit,
    vm: LinkDeviceViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    detail_scaffold(title = stringResource(R.string.link_device_title), on_back = on_back) {
        state.error?.let {
            error_banner(stringResource(link_device_error_res(it)))
            v_gap(AsterSpacing.lg)
        }

        when (state.step) {
            LinkDeviceStep.INPUT -> {
                Text(
                    text = stringResource(R.string.link_device_description),
                    color = colors.text_tertiary,
                    fontSize = 14.sp,
                )
                v_gap(AsterSpacing.lg)

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
                Text(
                    text = stringResource(R.string.link_device_confirm_description),
                    color = colors.text_tertiary,
                    fontSize = 14.sp,
                )
                v_gap(AsterSpacing.lg)

                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AsterSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(TablerIcons.DeviceDesktop, null, tint = colors.text_muted)
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
                                text = stringResource(
                                    link_device_type_res(device?.device_type.orEmpty()),
                                ),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                            )
                        }
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
                Text(
                    text = stringResource(R.string.link_device_success_title),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                v_gap(AsterSpacing.sm)
                Text(
                    text = stringResource(
                        R.string.link_device_success_description,
                        state.linked_device_name.take(MAX_MACHINE_NAME_CHARS).ifBlank {
                            stringResource(R.string.link_device_unnamed)
                        },
                    ),
                    color = colors.text_tertiary,
                    fontSize = 14.sp,
                )
                v_gap(AsterSpacing.lg)

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
