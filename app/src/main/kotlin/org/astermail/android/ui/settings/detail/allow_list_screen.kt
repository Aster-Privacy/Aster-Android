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
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.World

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.mail.SenderAvatar

@Composable
private fun allowed_sender_row(
    address: String,
    is_domain: Boolean,
    on_remove: () -> Unit,
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
                text = if (is_domain) "*.$address" else address,
                color = colors.text_primary,
                fontSize = 15.sp,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (is_domain) {
                Text(
                    text = stringResource(R.string.allowlist_domain_label),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            text = stringResource(R.string.remove_from_allowlist),
            color = colors.accent_blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = on_remove)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
                .testTag("allowlist_remove"),
        )
    }
}

@Composable
private fun allowlist_kind_option(
    label: String,
    selected: Boolean,
    on_select: () -> Unit,
    test_tag: String,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = on_select)
            .padding(vertical = AsterSpacing.xs)
            .testTag(test_tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 6.dp else 2.dp,
                    color = if (selected) colors.accent_blue else colors.text_muted,
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = AsterSpacing.sm),
        )
    }
}

@Composable
fun AllowListScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    var show_add_dialog by remember { mutableStateOf(false) }
    var add_value by remember { mutableStateOf("") }
    var add_is_domain by remember { mutableStateOf(false) }
    val senders = state.allowed_senders

    LaunchedEffect(Unit) { vm.load_allowed_senders() }

    detail_scaffold(title = stringResource(R.string.allowlist), on_back = on_back) {
        AsterButton(
            label = stringResource(R.string.allow_a_sender),
            onClick = { show_add_dialog = true },
            modifier = Modifier.fillMaxWidth().testTag("allowlist_add_button"),
        )
        v_gap(AsterSpacing.md)

        if (state.allowed_senders_loading && senders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (state.allowed_senders_error != null) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.allowed_senders_load_failed),
                    icon = TablerIcons.CircleCheck,
                    trailing = {
                        AsterGhostButton(
                            label = stringResource(R.string.retry),
                            onClick = { vm.load_allowed_senders() },
                        )
                    },
                )
            }
        } else if (senders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.CircleCheck,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = stringResource(R.string.no_allowlisted_senders),
                color = colors.text_primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            v_gap(AsterSpacing.xs)
            Text(
                text = stringResource(R.string.no_allowlisted_senders_subtitle),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.md),
            )
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                senders.forEachIndexed { idx, sender ->
                    allowed_sender_row(
                        address = sender.address,
                        is_domain = sender.is_domain,
                        on_remove = { vm.remove_allowed_sender(sender.sender_token) },
                    )
                    if (idx < senders.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_add_dialog) {
        val can_add = if (add_is_domain) {
            add_value.contains(".") && !add_value.contains("@")
        } else {
            add_value.contains("@") && add_value.substringAfter('@').contains(".")
        }
        AsterDialog(
            on_dismiss = { show_add_dialog = false; add_value = ""; add_is_domain = false },
            title = stringResource(R.string.add_to_allowlist),
            message = stringResource(R.string.add_to_allowlist_hint),
            body = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.lg)) {
                        allowlist_kind_option(
                            label = stringResource(R.string.email_address),
                            selected = !add_is_domain,
                            on_select = { add_is_domain = false },
                            test_tag = "allowlist_kind_email",
                        )
                        allowlist_kind_option(
                            label = stringResource(R.string.allowlist_entire_domain),
                            selected = add_is_domain,
                            on_select = { add_is_domain = true },
                            test_tag = "allowlist_kind_domain",
                        )
                    }
                    v_gap(AsterSpacing.sm)
                    AsterTextField(
                        value = add_value,
                        onValueChange = { add_value = it.trim().lowercase() },
                        placeholder = if (add_is_domain) {
                            stringResource(R.string.allowlist_domain_placeholder)
                        } else {
                            stringResource(R.string.allowlist_email_placeholder)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("allowlist_add_input"),
                    )
                }
            },
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_add_dialog = false; add_value = ""; add_is_domain = false },
                )
                AsterDialogPrimaryButton(
                    label = stringResource(R.string.alias_action_add),
                    enabled = can_add,
                    onClick = {
                        vm.allow_sender(add_value, add_is_domain)
                        show_add_dialog = false
                        add_value = ""
                        add_is_domain = false
                    },
                )
            },
        )
    }
}
