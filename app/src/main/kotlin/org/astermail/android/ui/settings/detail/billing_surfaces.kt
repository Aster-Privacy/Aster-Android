/*
 * Aster Mail Android
 * Copyright (C) 2026 Aster Privacy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.astermail.android.ui.settings.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic

internal val billing_panel_shape: Shape = SquircleShape(18.dp)
internal val billing_tile_shape: Shape = SquircleShape(14.dp)
internal val billing_control_shape: Shape = SquircleShape(12.dp)

@Composable
internal fun Modifier.billing_surface(shape: Shape, tint: Color? = null): Modifier {
    val colors = AsterMaterial.colors
    return this.acrylic(colors, shape, tint ?: colors.bg_secondary)
}

@Composable
internal fun billing_pill(
    text: String,
    modifier: Modifier = Modifier,
    foreground: Color? = null,
    background: Color? = null,
    icon: ImageVector? = null,
) {
    val colors = AsterMaterial.colors
    val fg = foreground ?: colors.accent_blue
    val bg = background ?: fg.copy(alpha = 0.14f)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun billing_feature_row(
    @StringRes feature_res: Int,
    icon: ImageVector = plan_feature_icon(feature_res),
    tint: Color? = null,
) {
    val colors = AsterMaterial.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: colors.accent_blue,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = stringResource(feature_res),
            color = colors.text_secondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
internal fun billing_meter(
    label: String,
    value_text: String,
    trailing_text: String,
    fraction: Float,
    is_over: Boolean,
    modifier: Modifier = Modifier,
    trailing_action: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colors.text_secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = trailing_text,
                color = if (is_over) colors.danger else colors.text_tertiary,
                fontSize = 12.sp,
                fontWeight = if (is_over) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        solid_progress_bar(fraction = fraction, is_over = is_over, height = 8.dp)
        Spacer(Modifier.height(AsterSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value_text,
                color = if (is_over) colors.danger else colors.text_tertiary,
                fontSize = 12.sp,
                fontWeight = if (is_over) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (trailing_action != null) {
                Spacer(Modifier.width(AsterSpacing.sm))
                trailing_action()
            }
        }
    }
}

@Composable
internal fun billing_divider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(colors.border_primary),
    )
}
