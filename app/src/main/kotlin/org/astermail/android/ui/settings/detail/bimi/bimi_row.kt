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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val subtitle = if (!is_active) {
        stringResource(R.string.domain_bimi_row_inactive)
    } else {
        when (state) {
            BimiState.off -> stringResource(R.string.domain_bimi_row_off)
            BimiState.draft -> stringResource(R.string.domain_bimi_row_draft)
            BimiState.pending -> stringResource(R.string.domain_bimi_row_pending)
            BimiState.live -> stringResource(R.string.domain_bimi_row_live)
            BimiState.attention -> stringResource(R.string.domain_bimi_row_attention)
            BimiState.external -> stringResource(R.string.domain_bimi_row_external)
        }
    }
    val content_alpha = if (is_active) 1f else 0.4f
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.domain_bimi_title),
                color = colors.text_primary.copy(alpha = content_alpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (is_active && state != BimiState.off) {
                v_gap(3.dp)
                bimi_state_chip(state)
            }
            v_gap(2.dp)
            Text(
                text = subtitle,
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        TextButton(onClick = on_open, enabled = is_active) {
            Text(
                text = stringResource(
                    if (state == BimiState.off) R.string.domain_bimi_set_up else R.string.domain_bimi_manage,
                ),
                color = colors.accent_blue.copy(alpha = content_alpha),
                fontSize = 14.sp,
            )
        }
    }
}
