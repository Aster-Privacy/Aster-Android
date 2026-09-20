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

package org.astermail.android.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Check
import compose.icons.tablericons.InfoCircle
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

enum class AsterAlertSeverity { danger, warning, info, success }

private const val alert_surface_alpha_dark = 0.20f
private const val alert_surface_alpha_light = 0.10f
private const val alert_border_alpha = 0.32f

@Composable
fun AsterAlert(
    message: String,
    modifier: Modifier = Modifier,
    severity: AsterAlertSeverity = AsterAlertSeverity.danger,
    title: String? = null,
) {
    val colors = AsterMaterial.colors
    val accent = when (severity) {
        AsterAlertSeverity.danger -> colors.danger
        AsterAlertSeverity.warning -> colors.warning
        AsterAlertSeverity.info -> colors.accent_blue
        AsterAlertSeverity.success -> colors.success
    }
    val icon: ImageVector = when (severity) {
        AsterAlertSeverity.danger -> TablerIcons.AlertCircle
        AsterAlertSeverity.warning -> TablerIcons.AlertTriangle
        AsterAlertSeverity.info -> TablerIcons.InfoCircle
        AsterAlertSeverity.success -> TablerIcons.Check
    }
    val surface_alpha = if (colors.is_dark) alert_surface_alpha_dark else alert_surface_alpha_light
    val surface = accent.copy(alpha = surface_alpha).compositeOver(colors.bg_card)
    val shape = SquircleShape(14.dp)
    val spoken = if (title.isNullOrBlank()) message else "$title. $message"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(surface, shape)
            .border(BorderStroke(1.dp, accent.copy(alpha = alert_border_alpha)), shape)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message,
                color = if (title.isNullOrBlank()) colors.text_primary else colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
