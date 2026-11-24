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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.compose.sending_block_row
import org.astermail.android.ui.compose.sending_block_rows

@Composable
internal fun ColumnScope.alias_sending_tab(
    vm: SettingsViewModel,
    state: SettingsUiState,
) {
    var query by remember { mutableStateOf("") }
    val all_rows = remember(
        state.aliases,
        state.custom_domain_addresses,
        state.sending_blocked_alias_ids,
        state.sending_blocked_domain_address_ids,
    ) {
        sending_block_rows(
            state.aliases,
            state.custom_domain_addresses,
            state.sending_blocked_alias_ids,
            state.sending_blocked_domain_address_ids,
        )
    }
    val rows = remember(all_rows, query) {
        sending_block_rows(
            state.aliases,
            state.custom_domain_addresses,
            state.sending_blocked_alias_ids,
            state.sending_blocked_domain_address_ids,
            query,
        )
    }

    if (all_rows.isNotEmpty()) {
        org.astermail.android.ui.common.list_search_bar(
            query = query,
            on_query_change = { query = it },
            placeholder = stringResource(R.string.search_aliases),
            test_tag = "alias_sending_search",
        )
        v_gap(AsterSpacing.md)
    }
    alias_card(
        title = stringResource(R.string.alias_sending_disabled_title),
        description = stringResource(R.string.alias_sending_disabled_description),
    ) {
        when {
            all_rows.isEmpty() -> sending_empty_text(stringResource(R.string.alias_sending_empty))
            rows.isEmpty() -> sending_empty_text(stringResource(R.string.alias_sending_no_match))
            else -> {
                settings_row_gap()
                rows.forEachIndexed { idx, row ->
                    if (idx > 0) settings_row_gap()
                    sending_block_item(
                        row = row,
                        pending = row.id in state.sending_block_pending_ids,
                        on_toggle = { allowed -> vm.set_sending_disabled(row.kind, row.id, !allowed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun sending_empty_text(text: String) {
    val colors = AsterMaterial.colors
    Text(
        text = text,
        color = colors.text_tertiary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl)
            .testTag("alias_sending_empty"),
    )
}

@Composable
private fun sending_block_item(
    row: sending_block_row,
    pending: Boolean,
    on_toggle: (Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val a11y_label = stringResource(R.string.alias_sending_allow_a11y, row.address)
    val allowed = !row.sending_disabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height)
            .toggleable(
                value = allowed,
                enabled = !pending,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    on_toggle(it)
                },
            )
            .semantics { contentDescription = a11y_label }
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)
            .testTag("alias_sending_row_" + row.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            alias_address_text(address = row.address)
            val secondary = buildList {
                add(
                    if (row.sending_disabled) {
                        stringResource(R.string.alias_sending_disabled)
                    } else {
                        stringResource(R.string.alias_sending_allowed)
                    },
                )
                if (row.display_name.isNotBlank()) add(row.display_name)
            }.joinToString(" · ")
            Text(
                text = secondary,
                color = if (row.sending_disabled) colors.danger else colors.text_tertiary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        AsterSwitch(
            checked = allowed,
            enabled = !pending,
            onCheckedChange = null,
            modifier = Modifier.testTag("alias_sending_switch_" + row.id),
        )
    }
}
