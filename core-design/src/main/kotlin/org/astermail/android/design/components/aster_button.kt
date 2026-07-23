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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.darken

private val aster_button_height = 54.dp
private val aster_button_shape = SquircleShape(20.dp)
private val aster_button_label_size = 16.sp

private val depth_red = Color(0xFFDC2626)

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
        fill = AsterMaterial.colors.accent_blue,
    )
}

@Composable
private fun depth_button(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    is_loading: Boolean,
    fill: Color,
) {
    val interactive = enabled && !is_loading
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && interactive) 0.97f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) AsterDuration.tap_down else AsterDuration.tap_up,
            easing = AsterEasing.tap_down,
        ),
        label = "btn_scale",
    )
    val alpha = if (interactive) 1f else 0.5f
    val press_color by animateColorAsState(
        targetValue = if (pressed && interactive) fill.darken(0.12f) else fill,
        animationSpec = tween(
            durationMillis = if (pressed) AsterDuration.tap_down else AsterDuration.tap_up,
            easing = AsterEasing.tap_down,
        ),
        label = "btn_fill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(aster_button_height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(aster_button_shape)
            .background(press_color.copy(alpha = alpha), aster_button_shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f * alpha), aster_button_shape)
            .clickable(
                enabled = interactive,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = AsterSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        aster_button_content(label, is_loading, Color.White.copy(alpha = if (interactive) 1f else 0.8f))
    }
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !is_loading) 0.97f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) AsterDuration.tap_down else AsterDuration.tap_up,
            easing = AsterEasing.tap_down,
        ),
        label = "sec_btn_scale",
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !is_loading,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(aster_button_height)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = aster_button_shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.text_primary,
            disabledContentColor = colors.text_muted,
        ),
        border = BorderStroke(1.5.dp, colors.text_primary.copy(alpha = 0.14f)),
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !is_loading) 0.97f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) AsterDuration.tap_down else AsterDuration.tap_up,
            easing = AsterEasing.tap_down,
        ),
        label = "ghost_btn_scale",
    )
    TextButton(
        onClick = onClick,
        enabled = enabled && !is_loading,
        interactionSource = interaction,
        modifier = modifier
            .height(aster_button_height)
            .graphicsLayer { scaleX = scale; scaleY = scale },
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
        fill = depth_red,
    )
}
