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

package org.astermail.android.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCompactButton
import org.astermail.android.design.control_surface_color
import org.astermail.android.design.island_surface_color
import org.astermail.android.imports.ExternalAccountAlert
import org.astermail.android.imports.ExternalAccountHealthState

@Composable
fun external_account_health_banner(
    alerts: List<ExternalAccountAlert>,
    on_review: () -> Unit,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val single = alerts.singleOrNull()?.takeIf { it.record.email.isNotBlank() }
    val message = when {
        single == null -> stringResource(R.string.ext_health_banner_multiple)
        single.state == ExternalAccountHealthState.NEEDS_REAUTH ->
            stringResource(R.string.ext_health_banner_reauth, single.record.email)
        else -> stringResource(R.string.ext_health_banner_failing, single.record.email)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(AsterShapes.island)
            .background(island_surface_color(colors))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.padding(top = 1.dp).size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                color = colors.text_primary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterCompactButton(
                    label = stringResource(R.string.ext_health_banner_action),
                    onClick = on_review,
                )
                AsterCompactButton(
                    label = stringResource(R.string.ext_health_banner_dismiss),
                    onClick = on_dismiss,
                    fill = control_surface_color(colors),
                    content_color = colors.text_primary,
                )
            }
        }
    }
}
