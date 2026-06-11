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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ripple
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

private val aster_button_height = 52.dp
private val aster_button_shape = SquircleShape(18.dp)
private val aster_button_label_size = 16.sp

private val depth_blue_top = Color(0xFF4A7AFF)
private val depth_blue_mid = Color(0xFF3B6AEF)
private val depth_blue_bot = Color(0xFF2D5AE0)
private val depth_border_top = Color(0xFF5A8AFF)
private val depth_border_bot = Color(0xFF2350D0)
private val depth_red_top = Color(0xFFEF4444)
private val depth_red_mid = Color(0xFFDC2626)
private val depth_red_bot = Color(0xFFB91C1C)
private val depth_red_border_top = Color(0xFFF87171)
private val depth_red_border_bot = Color(0xFF991B1B)

@Composable
private fun aster_button_content(
    label: String,
    is_loading: Boolean,
    content_color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (is_loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = content_color,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                fontSize = aster_button_label_size,
                fontWeight = FontWeight.SemiBold,
                color = content_color,
            )
        }
    }
}

@Composable
fun AsterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    depth_button(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        is_loading = is_loading,
        fill_top = depth_blue_top,
        fill_mid = depth_blue_mid,
        fill_bot = depth_blue_bot,
        border_top = depth_border_top,
        border_bot = depth_border_bot,
    )
}

@Composable
private fun depth_button(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    is_loading: Boolean,
    fill_top: Color,
    fill_mid: Color,
    fill_bot: Color,
    border_top: Color,
    border_bot: Color,
) {
    val interactive = enabled && !is_loading
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha = if (interactive) 1f else 0.5f
    val brightness = if (pressed) 0.9f else 1f
    val fill = Brush.verticalGradient(
        colors = listOf(
            fill_top.copy(alpha = alpha).scale(brightness),
            fill_mid.copy(alpha = alpha).scale(brightness),
            fill_bot.copy(alpha = alpha).scale(brightness),
        ),
    )
    val border_brush = Brush.verticalGradient(
        colors = listOf(
            border_top.copy(alpha = alpha),
            fill_mid.copy(alpha = alpha),
            border_bot.copy(alpha = alpha),
        ),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(aster_button_height)
            .clip(aster_button_shape)
            .background(fill, aster_button_shape)
            .border(1.dp, border_brush, aster_button_shape)
            .clickable(
                enabled = interactive,
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = AsterSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        aster_button_content(label, is_loading, Color.White.copy(alpha = if (interactive) 1f else 0.8f))
    }
}

private fun Color.scale(factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return Color(red = red * f, green = green * f, blue = blue * f, alpha = alpha)
}

@Composable
fun AsterSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    val colors = AsterMaterial.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !is_loading,
        modifier = modifier
            .fillMaxWidth()
            .height(aster_button_height),
        shape = aster_button_shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.bg_card,
            contentColor = colors.text_primary,
            disabledContentColor = colors.text_muted,
        ),
        border = BorderStroke(1.5.dp, colors.border_primary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AsterSpacing.lg),
    ) {
        aster_button_content(label, is_loading, colors.text_primary)
    }
}

@Composable
fun AsterGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    val colors = AsterMaterial.colors
    TextButton(
        onClick = onClick,
        enabled = enabled && !is_loading,
        modifier = modifier.height(aster_button_height),
        shape = aster_button_shape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.accent_blue,
            disabledContentColor = colors.text_muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AsterSpacing.lg),
    ) {
        aster_button_content(label, is_loading, colors.accent_blue)
    }
}

@Composable
fun AsterDestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    depth_button(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        is_loading = is_loading,
        fill_top = depth_red_top,
        fill_mid = depth_red_mid,
        fill_bot = depth_red_bot,
        border_top = depth_red_border_top,
        border_bot = depth_red_border_bot,
    )
}
