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

package org.astermail.android.ui.settings.mail_rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial

@Composable
fun chip_segment(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    is_active: Boolean = false,
    is_placeholder: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val bg = if (is_active) colors.accent_blue.copy(alpha = 0.12f) else Color.Transparent
    val text_color = when {
        is_placeholder -> colors.text_tertiary
        is_active -> colors.accent_blue
        else -> colors.text_primary
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(bg)
            .clickable(onClick = on_click)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = text_color,
        )
    }
}

@Composable
fun chip_pill_row(
    segments: List<chip_segment_spec>,
    on_remove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(SquircleShape(12.dp))
            .background(colors.bg_card, SquircleShape(12.dp))
            .border(1.dp, colors.border_secondary, SquircleShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, spec ->
            chip_segment(
                label = spec.label,
                on_click = spec.on_click,
                is_active = spec.is_active,
                is_placeholder = spec.is_placeholder,
            )
            if (index < segments.lastIndex || on_remove != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(colors.border_secondary),
                )
            }
        }
        if (on_remove != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = on_remove)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.rules_remove),
                    tint = colors.text_tertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

data class chip_segment_spec(
    val label: String,
    val on_click: () -> Unit,
    val is_active: Boolean = false,
    val is_placeholder: Boolean = false,
)

@Composable
fun add_chip_pill(label: String, on_click: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(SquircleShape(12.dp))
            .background(Color.Transparent)
            .border(1.dp, colors.accent_blue.copy(alpha = 0.5f), SquircleShape(12.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.accent_blue,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun and_or_pill(label: String, on_click: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(SquircleShape(999.dp))
            .background(colors.bg_secondary)
            .clickable(onClick = on_click)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
