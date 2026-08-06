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

import compose.icons.TablerIcons
import compose.icons.tablericons.Ban
import compose.icons.tablericons.World

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.mail.SenderAvatar
import org.astermail.android.settings.shared_settings_view_model

@Composable
private fun blocked_sender_row(
    address: String,
    is_domain: Boolean,
    on_unblock: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (is_domain) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.World,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            SenderAvatar(email = address, size = 36.dp)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = !expanded }
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = address,
                color = colors.text_primary,
                fontSize = 15.sp,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (is_domain) {
                Text(
                    text = stringResource(R.string.blocked_domain),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            text = stringResource(R.string.unblock),
            color = colors.accent_blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = on_unblock)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        )
    }
}

@Composable
fun BlockedSendersScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    var show_add_dialog by remember { mutableStateOf(false) }
    var add_email by remember { mutableStateOf("") }
    val senders = state.blocked_senders

    LaunchedEffect(Unit) { vm.load_blocked_senders() }

    detail_scaffold(title = stringResource(R.string.blocked_senders), on_back = on_back) {
        AsterButton(
            label = stringResource(R.string.block_a_sender),
            onClick = { show_add_dialog = true },
            modifier = Modifier.fillMaxWidth(),
        )
        v_gap(AsterSpacing.md)

        if (state.blocked_senders_loading && senders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (state.blocked_senders_error != null) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.blocked_senders_load_failed),
                    icon = TablerIcons.Ban,
                    trailing = {
                        AsterGhostButton(
                            label = stringResource(R.string.retry),
                            onClick = { vm.load_blocked_senders() },
                        )
                    },
                )
            }
        } else if (senders.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_blocked_senders),
                    subtitle = stringResource(R.string.no_blocked_senders_subtitle),
                    icon = TablerIcons.Ban,
                )
            }
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                senders.forEachIndexed { idx, sender ->
                    blocked_sender_row(
                        address = sender.address,
                        is_domain = sender.is_domain,
                        on_unblock = { vm.unblock_sender(sender.sender_token) },
                    )
                    if (idx < senders.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_add_dialog) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_add_dialog = false; add_email = "" },
            title = stringResource(R.string.block_sender),
            message = stringResource(R.string.block_sender_hint),
            body = {
                AsterTextField(
                    value = add_email,
                    onValueChange = { add_email = it.trim().lowercase() },
                    placeholder = stringResource(R.string.block_sender_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_add_dialog = false; add_email = "" },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.block),
                    enabled = add_email.contains("."),
                    onClick = {
                        vm.block_sender(add_email)
                        show_add_dialog = false
                        add_email = ""
                    },
                )
            },
        )
    }
}
