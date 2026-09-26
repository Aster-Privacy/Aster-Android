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

import org.astermail.android.design.remember_click_interaction
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.aster_ripple
import org.astermail.android.design.disabled_surface_color
import org.astermail.android.design.darken

private val aster_button_height = 54.dp
private val aster_button_shape = AsterShapes.control
private val aster_button_label_size = 16.sp
private val aster_button_spinner_size = 20.dp

private val depth_red = Color(0xFFDC2626)

@Composable
private fun aster_button_label(label: String, content_color: Color, modifier: Modifier = Modifier) {
    Text(
        text = label,
        fontSize = aster_button_label_size,
        fontWeight = FontWeight.SemiBold,
        color = content_color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

@Composable
private fun aster_button_spinner(content_color: Color) {
    CircularProgressIndicator(
        modifier = Modifier.size(aster_button_spinner_size),
        color = content_color,
        strokeWidth = 2.dp,
    )
}

@Composable
private fun aster_button_content(
    label: String,
    is_loading: Boolean,
    content_color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        aster_button_label(label, content_color, Modifier.alpha(if (is_loading) 0f else 1f))
        if (is_loading) aster_button_spinner(content_color)
    }
}

@Composable
private fun press_scale(pressed: Boolean, interactive: Boolean, label: String): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed && interactive) 0.965f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) AsterDuration.tap_down else AsterDuration.tap_up,
            easing = AsterEasing.tap_down,
        ),
        label = label,
    )
    return scale
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
        content_color = AsterMaterial.colors.on_accent,
    )
}

@Composable
fun AsterAccentButton(
    label: String,
    onClick: () -> Unit,
    fill: Color,
    content_color: Color,
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
        fill = fill,
        content_color = content_color,
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
    content_color: Color = Color.White,
) {
    val colors = AsterMaterial.colors
    val interactive = enabled && !is_loading
    val interaction = remember_click_interaction()
    val pressed by interaction.collectIsPressedAsState()
    val scale = press_scale(pressed, interactive, "btn_scale")
    val resting_fill = if (enabled) fill else disabled_surface_color(colors)
    val resolved_content = if (enabled) content_color else colors.text_muted
    val press_color by animateColorAsState(
        targetValue = if (pressed && interactive) resting_fill.darken(0.12f) else resting_fill,
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
            .background(press_color, aster_button_shape)
            .clickable(
                enabled = interactive,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = AsterSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        aster_button_content(label, is_loading, resolved_content, Modifier.fillMaxWidth())
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
    depth_button(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        is_loading = is_loading,
        fill = colors.secondary_control_bg.copy(alpha = 1f),
        content_color = colors.text_primary,
    )
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
    val interactive = enabled && !is_loading
    val interaction = remember_click_interaction()
    val pressed by interaction.collectIsPressedAsState()
    val scale = press_scale(pressed, interactive, "ghost_btn_scale")
    val content_color = if (enabled) colors.accent_blue else colors.text_muted
    Box(
        modifier = modifier
            .height(aster_button_height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(aster_button_shape)
            .clickable(
                enabled = interactive,
                interactionSource = interaction,
                indication = aster_ripple(color = colors.accent_blue),
                onClick = onClick,
            )
            .padding(horizontal = AsterSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        aster_button_content(label, is_loading, content_color)
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
