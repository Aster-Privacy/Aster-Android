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

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.api.settings.SessionInfo
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.settings.device_client_avatar
import org.astermail.android.ui.settings.device_client_kind
import org.astermail.android.ui.settings.device_client_label_res
import org.astermail.android.ui.settings.device_display_name
import org.astermail.android.ui.settings.device_display_platform
import org.astermail.android.ui.settings.this_device_badge

@Composable
fun SessionsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) { vm.load_sessions() }
    val sessions_load_settled = remember_load_settled(state.is_loading)

    var pending_revoke_id by remember { mutableStateOf<String?>(null) }
    var show_logout_others_confirm by remember { mutableStateOf(false) }

    val scroll_state = rememberScrollState()
    val current_session = state.sessions.firstOrNull { it.is_current }
    val other_sessions = state.sessions.filter { !it.is_current }

    detail_scaffold(
        title = stringResource(R.string.sessions),
        on_back = on_back,
        scroll_state = scroll_state,
    ) {
        if (state.sessions.isEmpty() && (state.is_loading || !sessions_load_settled)) {
            section_label(stringResource(R.string.sessions_active_section))
            skeleton_card_list(rows = 3, leading_circle = true, trailing_width = 72.dp)
        } else if (state.error != null && state.sessions.isEmpty()) {
            section_label(stringResource(R.string.sessions_active_section))
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
            section_label(stringResource(R.string.sessions_active_section))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_active_sessions),
                    subtitle = null,
                )
            }
        } else {
            if (current_session != null) {
                section_label(stringResource(R.string.this_device))
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    session_row(session = current_session, on_revoke = null)
                }
                v_gap(AsterSpacing.lg)
            }
            if (other_sessions.isNotEmpty()) {
                section_header_action(
                    title = stringResource(R.string.sessions_other_devices_section),
                    action_label = stringResource(R.string.sign_out_others_action),
                    enabled = true,
                    on_click = { show_logout_others_confirm = true },
                )
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    other_sessions.forEachIndexed { idx, s ->
                        session_row(
                            session = s,
                            on_revoke = { pending_revoke_id = s.id },
                        )
                        if (idx < other_sessions.lastIndex) AsterDivider()
                    }
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
    val name = device_display_name(session.browser, session.device_type)
    if (name.isNotEmpty()) return name
    return if (session.os.isNotBlank()) stringResource(R.string.os_device, session.os)
    else stringResource(R.string.unknown_device)
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
    on_revoke: (() -> Unit)?,
) {
    val colors = AsterMaterial.colors
    val device_label = parse_device_label(session)
    val kind = device_client_kind(session.browser, session.device_type, session.os)
    val platform = device_display_platform(device_label, session.os)
        .ifEmpty { stringResource(device_client_label_res(kind)) }
    val meta = stringResource(
        R.string.session_meta_line,
        platform,
        format_last_active(session.last_active),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        device_client_avatar(kind = kind)
        Spacer(Modifier.size(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device_label,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(AsterSpacing.sm))
        if (on_revoke == null) {
            this_device_badge()
        } else {
            AsterGhostButton(label = stringResource(R.string.sign_out), onClick = on_revoke)
        }
    }
}
