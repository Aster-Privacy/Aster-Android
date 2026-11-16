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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.acrylic
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.island_surface_color
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.compose.sending_block_row
import org.astermail.android.ui.compose.sending_block_rows

@Composable
internal fun ColumnScope.alias_sending_tab(
    vm: SettingsViewModel,
    state: SettingsUiState,
) {
    val colors = AsterMaterial.colors
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

    Text(
        text = stringResource(R.string.alias_sending_disabled_title),
        color = colors.text_primary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    v_gap(AsterSpacing.xs)
    Text(
        text = stringResource(R.string.alias_sending_disabled_description),
        color = colors.text_muted,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    v_gap(AsterSpacing.sm)
    if (all_rows.isNotEmpty()) {
        org.astermail.android.ui.common.list_search_bar(
            query = query,
            on_query_change = { query = it },
            placeholder = stringResource(R.string.search_aliases),
            test_tag = "alias_sending_search",
        )
        v_gap(AsterSpacing.sm)
    }
    when {
        all_rows.isEmpty() -> sending_empty_text(stringResource(R.string.alias_sending_empty))
        rows.isEmpty() -> sending_empty_text(stringResource(R.string.alias_sending_no_match))
        else -> {
            val last_index = rows.lastIndex
            rows.forEachIndexed { idx, row ->
                sending_block_item(
                    row = row,
                    idx = idx,
                    last_index = last_index,
                    pending = row.id in state.sending_block_pending_ids,
                    on_toggle = { allowed -> vm.set_sending_disabled(row.kind, row.id, !allowed) },
                )
                if (idx != last_index) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun sending_empty_text(text: String) {
    val colors = AsterMaterial.colors
    Text(
        text = text,
        color = colors.text_muted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsterSpacing.xl)
            .testTag("alias_sending_empty"),
    )
}

@Composable
private fun sending_block_item(
    row: sending_block_row,
    idx: Int,
    last_index: Int,
    pending: Boolean,
    on_toggle: (Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = list_item_shape(idx, last_index)
    val a11y_label = stringResource(R.string.alias_sending_allow_a11y, row.address)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .acrylic(colors, shape, island_surface_color(colors))
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)
            .testTag("alias_sending_row_" + row.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.address,
                color = colors.text_primary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.display_name.isNotBlank()) {
                Text(
                    text = row.display_name,
                    color = colors.text_secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (row.sending_disabled) {
                    stringResource(R.string.alias_sending_disabled)
                } else {
                    stringResource(R.string.alias_sending_allowed)
                },
                color = if (row.sending_disabled) colors.danger else colors.text_tertiary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        AsterSwitch(
            checked = !row.sending_disabled,
            enabled = !pending,
            onCheckedChange = { on_toggle(it) },
            modifier = Modifier
                .semantics { contentDescription = a11y_label }
                .testTag("alias_sending_switch_" + row.id),
        )
    }
}
