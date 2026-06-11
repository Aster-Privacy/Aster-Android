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
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.api.settings.SessionInfo
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterButton
import org.astermail.android.settings.SettingsViewModel

@Composable
fun SessionsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) { vm.load_sessions() }

    var pending_revoke_id by remember { mutableStateOf<String?>(null) }
    var show_logout_others_confirm by remember { mutableStateOf(false) }

    detail_scaffold(title = stringResource(R.string.sessions), on_back = on_back) {
        if (state.is_loading && state.sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (state.error != null && state.sessions.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.could_not_load_sessions),
                    subtitle = state.error,
                )
            }
            v_gap(AsterSpacing.md)
            AsterButton(
                label = stringResource(R.string.retry),
                onClick = { vm.load_sessions() },
            )
        } else if (state.sessions.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_active_sessions),
                    subtitle = null,
                )
            }
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                state.sessions.forEachIndexed { idx, s ->
                    session_row(
                        session = s,
                        on_revoke = { pending_revoke_id = s.id },
                    )
                    if (idx < state.sessions.lastIndex) AsterDivider()
                }
            }
            AnimatedVisibility(
                visible = state.sessions.size > 1,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    v_gap(AsterSpacing.lg)
                    AsterDestructiveButton(
                        label = stringResource(R.string.sign_out_all_other),
                        onClick = { show_logout_others_confirm = true },
                    )
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    if (pending_revoke_id != null) {
        val rid = pending_revoke_id
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { pending_revoke_id = null },
            title = stringResource(R.string.sign_out),
            message = stringResource(R.string.revoke_session_confirm_message),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { pending_revoke_id = null },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.sign_out),
                    onClick = {
                        rid?.let { vm.revoke_session(it) }
                        pending_revoke_id = null
                    },
                )
            },
        )
    }

    if (show_logout_others_confirm) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_logout_others_confirm = false },
            title = stringResource(R.string.sign_out_all_other),
            message = stringResource(R.string.sign_out_all_other_confirm_message),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_logout_others_confirm = false },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.sign_out_all_other),
                    onClick = {
                        vm.logout_others()
                        show_logout_others_confirm = false
                    },
                )
            },
        )
    }
}

@Composable
private fun parse_device_label(session: SessionInfo): String {
    val device = session.device_type.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
    val browser = session.browser.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
    val parts = listOfNotNull(device, browser)
    if (parts.isNotEmpty()) return parts.joinToString(" - ")
    return if (session.os.isNotBlank()) stringResource(R.string.os_device, session.os)
    else stringResource(R.string.unknown_device)
}

private fun is_mobile_session(session: SessionInfo): Boolean {
    val dt = session.device_type.lowercase()
    return dt.contains("android") || dt.contains("iphone") || dt.contains("ipad") || dt.contains("mobile")
}

@Composable
private fun format_last_active(last_active: String?): String {
    if (last_active.isNullOrBlank()) return stringResource(R.string.unknown)
    val minutes = try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val ts = fmt.parse(last_active.take(19))?.time ?: return last_active
        val diff = System.currentTimeMillis() - ts
        (diff / 60_000).toInt().coerceAtLeast(0)
    } catch (_: Throwable) {
        return last_active.take(10)
    }
    return when {
        minutes < 2 -> stringResource(R.string.active_now)
        minutes < 60 -> stringResource(R.string.minutes_ago, minutes)
        minutes < 1440 -> stringResource(R.string.hours_ago, minutes / 60)
        else -> stringResource(R.string.days_ago, minutes / 1440)
    }
}

@Composable
private fun session_row(
    session: SessionInfo,
    on_revoke: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val device_label = parse_device_label(session)
    val is_mobile = is_mobile_session(session)
    val icon = if (is_mobile) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device_label,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (session.is_current) {
                    Spacer(Modifier.size(AsterSpacing.sm))
                    Box(
                        modifier = Modifier
                            .background(colors.accent_blue.copy(alpha = 0.15f), SquircleShape(8.dp))
                            .padding(horizontal = AsterSpacing.sm, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.this_device),
                            color = colors.accent_blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            val os = session.os
            if (os.isNotBlank()) {
                Text(text = os, color = colors.text_tertiary, fontSize = 13.sp)
            }
            Text(
                text = format_last_active(session.last_active),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
        if (!session.is_current) {
            AsterGhostButton(label = stringResource(R.string.sign_out), onClick = on_revoke)
        }
    }
}
