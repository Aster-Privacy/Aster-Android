// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterMaterial

private val plan_tag_shape = RoundedCornerShape(6.dp)

@Composable
fun AsterPlanTag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    font_size: TextUnit = 12.sp,
    horizontal_padding: Dp = 8.dp,
    vertical_padding: Dp = 3.dp,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .clip(plan_tag_shape)
            .background(colors.accent_blue, plan_tag_shape)
            .padding(horizontal = horizontal_padding, vertical = vertical_padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.on_accent,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            color = colors.on_accent,
            fontSize = font_size,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
