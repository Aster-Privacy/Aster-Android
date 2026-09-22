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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterMaterial

private val plan_tag_shape = RoundedCornerShape(999.dp)

enum class aster_plan_kind { star, nova, supernova }

fun aster_plan_kind_of(plan_code: String?): aster_plan_kind? =
    when (plan_code?.trim()?.lowercase()) {
        "star" -> aster_plan_kind.star
        "nova" -> aster_plan_kind.nova
        "supernova" -> aster_plan_kind.supernova
        else -> null
    }

private val plan_tag_shade = Color(0xFF05070F)

@Composable
private fun plan_tag_gradient(): List<Color> {
    val accent = AsterMaterial.colors.accent_blue
    return listOf(
        lerp(accent, Color.White, 0.16f),
        lerp(accent, Color.Black, 0.04f),
        lerp(accent, plan_tag_shade, 0.26f),
    )
}

@Composable
fun AsterPlanTag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    plan: aster_plan_kind? = null,
    font_size: TextUnit = 11.sp,
    horizontal_padding: Dp = 7.dp,
    vertical_padding: Dp = 2.dp,
) {
    val colors = AsterMaterial.colors
    val stops = plan_tag_gradient()
    val brush = remember(stops) { Brush.verticalGradient(stops) }
    Row(
        modifier = modifier
            .clip(plan_tag_shape)
            .background(brush, plan_tag_shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), plan_tag_shape)
            .padding(horizontal = horizontal_padding, vertical = vertical_padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.on_accent,
                modifier = Modifier.size(10.dp),
            )
        }
        Text(
            text = text,
            color = colors.on_accent,
            fontSize = font_size,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
            maxLines = 1,
        )
    }
}
