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
import compose.icons.tablericons.Search

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

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
    var show_add_dialog by remember { mutableStateOf(false) }
    var add_value by remember { mutableStateOf("") }
    var add_is_domain by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val senders = state.allowed_senders
    val filtered = remember(senders, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) senders else senders.filter { it.address.lowercase().contains(needle) }
    }
    val domain_label = stringResource(R.string.allowlist_domain_label)
    val remove_label = stringResource(R.string.remove_from_allowlist)

    val action_result_context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(action_result_context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) { vm.load_allowed_senders() }

    detail_scaffold(title = stringResource(R.string.allowlist), on_back = on_back) {
        when {
            state.allowed_senders_loading && senders.isEmpty() -> filters_loading_block()

            state.allowed_senders_error != null && senders.isEmpty() -> filters_error_card(
                message = state.allowed_senders_error,
                on_retry = { vm.load_allowed_senders() },
            )

            senders.isEmpty() -> filters_state_card(
                icon = TablerIcons.CircleCheck,
                title = stringResource(R.string.no_allowlisted_senders),
                body = stringResource(R.string.fix_filters_allowed_empty_body),
                action_label = stringResource(R.string.allow_a_sender),
                on_action = { show_add_dialog = true },
                action_test_tag = "allowlist_add_button",
            )

            else -> {
                filters_description(stringResource(R.string.fix_filters_allowed_description))
                v_gap(AsterSpacing.md)
                AsterButton(
                    label = stringResource(R.string.allow_a_sender),
                    onClick = { show_add_dialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("allowlist_add_button"),
                )
                v_gap(AsterSpacing.md)
                filters_search_field(
                    value = query,
                    on_change = { query = it },
                    placeholder = stringResource(R.string.fix_filters_search_allowed),
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
                        label = stringResource(R.string.allowlist),
                        count = filtered.size,
                    )
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        filtered.forEachIndexed { idx, sender ->
                            filters_entry_row(
                                address = sender.address,
                                is_domain = sender.is_domain,
                                domain_label = domain_label,
                                action_label = remove_label,
                                on_action = { vm.remove_allowed_sender(sender.sender_token) },
                                action_test_tag = "allowlist_remove",
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
