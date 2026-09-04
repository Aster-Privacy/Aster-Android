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
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Search
import compose.icons.tablericons.World
import compose.icons.tablericons.X

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogDestructiveButton
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.mail.SenderAvatar

@Composable
internal fun filters_description(text: String) {
    val colors = AsterMaterial.colors
    Text(
        text = text,
        color = colors.text_tertiary,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun filters_search_field(
    value: String,
    on_change: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val clear_label = stringResource(R.string.fix_filters_clear_search)
    val trailing: (@Composable () -> Unit)? = if (value.isNotEmpty()) {
        {
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = clear_label,
                tint = colors.text_tertiary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { on_change("") }
                    .padding(2.dp),
            )
        }
    } else {
        null
    }
    AsterTextField(
        value = value,
        onValueChange = on_change,
        placeholder = placeholder,
        modifier = modifier.fillMaxWidth(),
        min_height = 46.dp,
        leading_icon = {
            Icon(
                imageVector = TablerIcons.Search,
                contentDescription = stringResource(R.string.fix_filters_search),
                tint = colors.text_tertiary,
                modifier = Modifier.size(18.dp),
            )
        },
        trailing_icon = trailing,
    )
}

@Composable
internal fun filters_list_header(label: String, count: Int) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            color = colors.text_tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = count.toString(),
            color = colors.text_tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun filters_entry_row(
    address: String,
    is_domain: Boolean,
    domain_label: String,
    action_label: String,
    on_action: () -> Unit,
    action_test_tag: String? = null,
) {
    val colors = AsterMaterial.colors
    val action_modifier = Modifier
        .clip(SquircleShape(10.dp))
        .clickable(onClick = on_action)
        .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (is_domain) {
            Icon(
                imageVector = TablerIcons.World,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier
                    .size(34.dp)
                    .padding(6.dp),
            )
        } else {
            SenderAvatar(email = address, size = 34.dp)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = AsterSpacing.md, end = AsterSpacing.sm),
        ) {
            Text(
                text = if (is_domain) "*.$address" else address,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (is_domain) {
                Text(
                    text = domain_label,
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = action_label,
            color = colors.danger,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = if (action_test_tag != null) {
                action_modifier.testTag(action_test_tag)
            } else {
                action_modifier
            },
        )
    }
}

@Composable
internal fun filters_state_card(
    icon: ImageVector,
    title: String,
    body: String,
    action_label: String? = null,
    on_action: (() -> Unit)? = null,
    action_test_tag: String? = null,
) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(26.dp),
            )
            v_gap(AsterSpacing.md)
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            v_gap(AsterSpacing.xs)
            Text(
                text = body,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (action_label != null && on_action != null) {
                v_gap(AsterSpacing.lg)
                AsterButton(
                    label = action_label,
                    onClick = on_action,
                    modifier = if (action_test_tag != null) {
                        Modifier.testTag(action_test_tag)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
internal fun filters_loading_block() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AsterSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun filters_error_card(message: String?, on_retry: () -> Unit) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = TablerIcons.AlertTriangle,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(24.dp),
            )
            v_gap(AsterSpacing.sm)
            Text(
                text = message.orEmpty(),
                color = colors.text_secondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            v_gap(AsterSpacing.md)
            AsterSecondaryButton(
                label = stringResource(R.string.retry),
                onClick = on_retry,
            )
        }
    }
}

@Composable
fun BlockedSendersScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    var show_add_dialog by remember { mutableStateOf(false) }
    var add_email by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val senders = state.blocked_senders
    val filtered = remember(senders, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) senders else senders.filter { it.address.lowercase().contains(needle) }
    }
    val domain_label = stringResource(R.string.blocked_domain)
    val unblock_label = stringResource(R.string.unblock)

    val action_result_context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(action_result_context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) { vm.load_blocked_senders() }

    detail_scaffold(title = stringResource(R.string.blocked_senders), on_back = on_back) {
        when {
            state.blocked_senders_loading && senders.isEmpty() -> filters_loading_block()

            state.blocked_senders_error != null && senders.isEmpty() -> filters_error_card(
                message = state.blocked_senders_error,
                on_retry = { vm.load_blocked_senders() },
            )

            senders.isEmpty() -> filters_state_card(
                icon = TablerIcons.Ban,
                title = stringResource(R.string.no_blocked_senders),
                body = stringResource(R.string.fix_filters_blocked_empty_body),
                action_label = stringResource(R.string.block_a_sender),
                on_action = { show_add_dialog = true },
            )

            else -> {
                filters_description(stringResource(R.string.fix_filters_blocked_description))
                v_gap(AsterSpacing.md)
                AsterButton(
                    label = stringResource(R.string.block_a_sender),
                    onClick = { show_add_dialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                v_gap(AsterSpacing.md)
                filters_search_field(
                    value = query,
                    on_change = { query = it },
                    placeholder = stringResource(R.string.fix_filters_search_blocked),
                )
                v_gap(AsterSpacing.md)
                if (filtered.isEmpty()) {
                    filters_state_card(
                        icon = TablerIcons.Search,
                        title = stringResource(R.string.fix_filters_no_results_title),
                        body = stringResource(R.string.fix_filters_no_results_body),
                    )
                } else {
                    filters_list_header(
                        label = stringResource(R.string.blocked_senders),
                        count = filtered.size,
                    )
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        filtered.forEachIndexed { idx, sender ->
                            filters_entry_row(
                                address = sender.address,
                                is_domain = sender.is_domain,
                                domain_label = domain_label,
                                action_label = unblock_label,
                                on_action = { vm.unblock_sender(sender.sender_token) },
                            )
                            if (idx < filtered.lastIndex) AsterDivider(modifier = Modifier)
                        }
                    }
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_add_dialog) {
        AsterDialog(
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
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_add_dialog = false; add_email = "" },
                )
                AsterDialogDestructiveButton(
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
