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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCompactButton
import org.astermail.android.design.control_surface_color
import org.astermail.android.design.island_surface_color

@Composable
fun storage_nudge_banner(
    tier: StorageNudgeTier,
    percent: Double,
    usage_text: String,
    offer_text: String,
    on_free_up: () -> Unit,
    on_get_storage: () -> Unit,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val accent = when (tier) {
        StorageNudgeTier.almost -> colors.accent_blue
        StorageNudgeTier.critical -> colors.warning
        StorageNudgeTier.full -> colors.danger
    }
    val title = stringResource(
        when (tier) {
            StorageNudgeTier.almost -> R.string.storage_nudge_almost_title
            StorageNudgeTier.critical -> R.string.storage_nudge_critical_title
            StorageNudgeTier.full -> R.string.storage_nudge_full_title
        },
    )
    val body = stringResource(
        if (tier == StorageNudgeTier.full) R.string.storage_nudge_full_body else R.string.storage_nudge_body,
    )
    val fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(AsterShapes.island)
            .background(island_surface_color(colors))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = TablerIcons.AlertCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = title,
                        color = colors.text_secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = offer_text,
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            if (tier != StorageNudgeTier.full) {
                val dismiss_label = stringResource(R.string.close)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = on_dismiss)
                        .semantics { contentDescription = dismiss_label },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.bg_tertiary)
                    .semantics {
                        contentDescription = usage_text
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = usage_text,
                color = accent,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterCompactButton(
                    label = stringResource(R.string.storage_nudge_free_up),
                    onClick = on_free_up,
                    fill = control_surface_color(colors),
                    content_color = colors.text_primary,
                )
                AsterCompactButton(
                    label = stringResource(R.string.storage_nudge_get_storage),
                    onClick = on_get_storage,
                )
            }
        }
    }
}
