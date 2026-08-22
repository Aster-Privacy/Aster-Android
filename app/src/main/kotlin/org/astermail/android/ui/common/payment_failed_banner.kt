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

package org.astermail.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterSecondaryButton

@Composable
fun payment_failed_banner(
    plan_name: String,
    due_date: String,
    is_loading: Boolean,
    on_update_card: () -> Unit,
    modifier: Modifier = Modifier,
    days_left: Int? = null,
) {
    val colors = AsterMaterial.colors
    val message = if (days_left != null && due_date.isNotBlank()) {
        stringResource(R.string.payment_failed_banner_message, due_date, plan_name)
    } else {
        stringResource(R.string.payment_failed_banner_overdue, plan_name)
    }
    val days_text = when {
        days_left == null -> null
        days_left == 1 -> stringResource(R.string.payment_failed_one_day_left)
        else -> stringResource(R.string.payment_failed_days_left, days_left)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .background(colors.danger.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = TablerIcons.AlertTriangle,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                color = colors.text_primary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (days_text != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = days_text,
                color = colors.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 28.dp),
            )
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        AsterSecondaryButton(
            label = if (is_loading) stringResource(R.string.loading) else stringResource(R.string.payment_failed_update_card),
            onClick = on_update_card,
            enabled = !is_loading,
        )
    }
}
