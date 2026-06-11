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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.outlined.Unsubscribe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider

@Composable
fun FiltersScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors

    detail_scaffold(title = stringResource(R.string.filters_title), on_back = on_back) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(colors.accent_blue.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterAlt,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.mail_filters),
            color = colors.text_primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        v_gap(AsterSpacing.xs)
        Text(
            text = stringResource(R.string.filters_description),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.md),
        )
        v_gap(AsterSpacing.xl)
        section_label(stringResource(R.string.block_allow))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                detail_row(
                    title = stringResource(R.string.blocked_senders),
                    subtitle = stringResource(R.string.senders_never_hear),
                    icon = Icons.Outlined.Block,
                    on_click = { on_open("blocked") },
                )
                AsterDivider(modifier = Modifier)
                detail_row(
                    title = stringResource(R.string.auto_forward),
                    subtitle = stringResource(R.string.forward_matching),
                    icon = Icons.AutoMirrored.Outlined.Forward,
                    on_click = { on_open("auto_forward") },
                )
                AsterDivider(modifier = Modifier)
                detail_row(
                    title = stringResource(R.string.subscriptions_label),
                    subtitle = stringResource(R.string.mailing_lists_on),
                    icon = Icons.Outlined.Unsubscribe,
                    on_click = { on_open("subscriptions") },
                )
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
