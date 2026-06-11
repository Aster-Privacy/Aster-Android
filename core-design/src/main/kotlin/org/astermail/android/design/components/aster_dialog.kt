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

import org.astermail.android.design.SquircleShape

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.astermail.android.design.AsterMaterial

private val dialog_shape = SquircleShape(18.dp)
private val dialog_button_shape = SquircleShape(12.dp)
private val dialog_button_height = 40.dp
private val dialog_max_width = 360.dp

private val dlg_blue_top = Color(0xFF4A7AFF)
private val dlg_blue_mid = Color(0xFF3B6AEF)
private val dlg_blue_bot = Color(0xFF2D5AE0)
private val dlg_blue_border_top = Color(0xFF5A8AFF)
private val dlg_blue_border_bot = Color(0xFF2350D0)
private val dlg_red_top = Color(0xFFEF4444)
private val dlg_red_mid = Color(0xFFDC2626)
private val dlg_red_bot = Color(0xFFB91C1C)
private val dlg_red_border_top = Color(0xFFF87171)
private val dlg_red_border_bot = Color(0xFF991B1B)

enum class DialogConfirmStyle { primary, destructive }

@Composable
fun AsterDialog(
    on_dismiss: () -> Unit,
    title: String,
    message: String? = null,
    body: @Composable (() -> Unit)? = null,
    footer: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 180),
        finishedListener = { if (!visible) on_dismiss() },
        label = "dialog_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "dialog_alpha",
    )
    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = dialog_max_width)
                .padding(horizontal = 16.dp)
                .scale(scale)
                .alpha(alpha),
            shape = dialog_shape,
            color = colors.bg_card,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(
                        text = title,
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    if (body != null) {
                        Spacer(Modifier.height(16.dp))
                        body()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
    }
}

@Composable
fun AsterAlertDialog(
    on_dismiss: () -> Unit,
    title: String,
    message: String? = null,
    confirm_label: String,
    cancel_label: String? = null,
    on_confirm: () -> Unit,
    confirm_style: DialogConfirmStyle = DialogConfirmStyle.primary,
    confirm_enabled: Boolean = true,
    is_busy: Boolean = false,
    extra_content: @Composable (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    var visible by remember { mutableStateOf(false) }
    var pending_confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 180),
        finishedListener = {
            if (!visible) {
                if (pending_confirm) on_confirm() else on_dismiss()
            }
        },
        label = "alert_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "alert_alpha",
    )
    val start_dismiss: () -> Unit = { pending_confirm = false; visible = false }
    val start_confirm: () -> Unit = { pending_confirm = true; visible = false }
    Dialog(
        onDismissRequest = start_dismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = dialog_max_width)
                .padding(horizontal = 16.dp)
                .scale(scale)
                .alpha(alpha),
            shape = dialog_shape,
            color = colors.bg_card,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(
                        text = title,
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    if (extra_content != null) {
                        Spacer(Modifier.height(16.dp))
                        extra_content()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cancel_label != null) {
                        AsterDialogOutlineButton(
                            label = cancel_label,
                            onClick = start_dismiss,
                            enabled = !is_busy,
                        )
                    }
                    when (confirm_style) {
                        DialogConfirmStyle.destructive -> AsterDialogDestructiveButton(
                            label = confirm_label,
                            onClick = start_confirm,
                            enabled = confirm_enabled && !is_busy,
                            is_loading = is_busy,
                        )
                        DialogConfirmStyle.primary -> AsterDialogPrimaryButton(
                            label = confirm_label,
                            onClick = start_confirm,
                            enabled = confirm_enabled && !is_busy,
                            is_loading = is_busy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun dialog_button_label(label: String, color: Color, is_loading: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        if (is_loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = color,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
fun AsterDialogPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    dialog_depth_button(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        is_loading = is_loading,
        fill_top = dlg_blue_top,
        fill_mid = dlg_blue_mid,
        fill_bot = dlg_blue_bot,
        border_top = dlg_blue_border_top,
        border_bot = dlg_blue_border_bot,
    )
}

@Composable
fun AsterDialogDestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    dialog_depth_button(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        is_loading = is_loading,
        fill_top = dlg_red_top,
        fill_mid = dlg_red_mid,
        fill_bot = dlg_red_bot,
        border_top = dlg_red_border_top,
        border_bot = dlg_red_border_bot,
    )
}

@Composable
private fun dialog_depth_button(
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
    val a = if (interactive) 1f else 0.5f
    val k = if (pressed) 0.9f else 1f
    val fill = Brush.verticalGradient(
        colors = listOf(
            scale_color(fill_top, k, a),
            scale_color(fill_mid, k, a),
            scale_color(fill_bot, k, a),
        ),
    )
    val border_brush = Brush.verticalGradient(
        colors = listOf(
            border_top.copy(alpha = a),
            fill_mid.copy(alpha = a),
            border_bot.copy(alpha = a),
        ),
    )
    Box(
        modifier = modifier
            .height(dialog_button_height)
            .defaultMinSize(minWidth = 80.dp)
            .clip(dialog_button_shape)
            .background(fill, dialog_button_shape)
            .border(1.dp, border_brush, dialog_button_shape)
            .clickable(
                enabled = interactive,
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        dialog_button_label(label, Color.White.copy(alpha = if (interactive) 1f else 0.8f), is_loading)
    }
}

private fun scale_color(c: Color, k: Float, a: Float): Color {
    val kk = k.coerceIn(0f, 1f)
    return Color(red = c.red * kk, green = c.green * kk, blue = c.blue * kk, alpha = a)
}

@Composable
fun AsterDialogOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(dialog_button_height),
        shape = dialog_button_shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.text_primary,
            disabledContentColor = colors.text_muted,
        ),
        border = BorderStroke(1.dp, colors.border_primary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
    ) {
        dialog_button_label(label, colors.text_primary, false)
    }
}
