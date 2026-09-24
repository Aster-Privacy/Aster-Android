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

package org.astermail.android.ui.settings.detail.bimi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.domains.BimiState
import org.astermail.android.api.domains.bimi_state_from
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.ui.settings.detail.v_gap

@Composable
internal fun bimi_row(domain: CustomDomain, is_active: Boolean, on_open: () -> Unit) {
    val colors = AsterMaterial.colors
    val state = bimi_state_from(domain.bimi_state) ?: BimiState.off
    val message = if (!is_active) {
        R.string.domain_bimi_row_inactive
    } else {
        bimi_row_message_res(state, domain.purchased)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        bimi_first_line_icon(Icons.Rounded.Image, colors.text_tertiary, 18.dp, 20.sp)
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.domain_bimi_title),
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (is_active && state != BimiState.off) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    bimi_state_chip(state)
                }
            }
            v_gap(2.dp)
            Text(
                text = stringResource(message),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        bimi_compact_button(
            label = stringResource(if (state == BimiState.off) R.string.domain_bimi_set_up else R.string.domain_bimi_manage),
            on_click = on_open,
            enabled = is_active,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}
