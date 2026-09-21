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

package org.astermail.android.ui.common

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.lighten

data class TopToastState(
    val message: String,
    val undo_label: String? = null,
    val on_undo: (() -> Unit)? = null,
    val secondary_label: String? = null,
    val secondary_icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val on_secondary: (() -> Unit)? = null,
    val on_tap: (() -> Unit)? = null,
    val show_close: Boolean = false,
    val on_close: (() -> Unit)? = null,
    val duration_ms: Long? = null,
    val on_timeout: (() -> Unit)? = null,
    val key: Long = System.currentTimeMillis(),
    val accumulation_key: String? = null,
)

private val toast_control_size = 32.dp

@Composable
private fun toast_action(
    label: String,
    on_click: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(999.dp)
    Box(
        modifier = Modifier
            .height(toast_control_size)
            .clip(shape)
            .background(colors.accent_blue)
            .clickable(enabled = enabled, onClick = on_click)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.on_accent,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun toast_icon_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on_click: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(toast_control_size)
            .clip(SquircleShape(999.dp))
            .background(toast_control_fill(colors))
            .clickable(enabled = enabled, onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.text_primary,
            modifier = Modifier.size(17.dp),
        )
    }
}

private val toast_dismiss_distance = 44.dp

private val toast_dismiss_velocity = 420.dp

private const val toast_pull_resistance = 0.32f

private fun toast_surface_fill(colors: org.astermail.android.design.AsterSemanticColors) =
    if (colors.is_dark) colors.bg_card.lighten(0.14f) else colors.bg_card

private fun toast_control_fill(colors: org.astermail.android.design.AsterSemanticColors) =
    if (colors.is_dark) colors.bg_card.lighten(0.28f) else colors.bg_hover

@Composable
fun top_toast_overlay(
    state: TopToastState?,
    on_dismiss: () -> Unit,
    duration_ms: Long = 4500,
) {
    var toast_dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state?.key) { toast_dragging = false }
    LaunchedEffect(state?.key, toast_dragging) {
        if (state != null && !toast_dragging) {
            delay(state.duration_ms ?: duration_ms)
            state.on_timeout?.invoke()
            on_dismiss()
        }
    }
    val toast_scope = rememberCoroutineScope()
    var last_state by remember { mutableStateOf<TopToastState?>(null) }
    if (state != null) last_state = state
    val colors = AsterMaterial.colors
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = state != null,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset(1, 1),
                ),
                initialOffsetY = { -it },
            ) + fadeIn(animationSpec = tween(140)) + scaleIn(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 0f),
            ),
            exit = slideOutVertically(animationSpec = tween(180), targetOffsetY = { -it }) +
                fadeOut(animationSpec = tween(140)) +
                scaleOut(animationSpec = tween(180), targetScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0f)),
        ) {
            val s = last_state ?: return@AnimatedVisibility
            val shape = SquircleShape(26.dp)
            val fill = toast_surface_fill(colors)
            val drag_offset = remember(s.key) { Animatable(0f) }
            var toast_height by remember(s.key) { mutableStateOf(0f) }
            LaunchedEffect(s.key) { drag_offset.snapTo(0f) }
            val row_modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .onSizeChanged { toast_height = it.height.toFloat() }
                .graphicsLayer {
                    val raw = drag_offset.value
                    translationY = if (raw > 0f) raw * toast_pull_resistance else raw
                    alpha = if (raw < 0f) {
                        (1f + raw / (toast_height.coerceAtLeast(1f) * 1.6f)).coerceIn(0.15f, 1f)
                    } else {
                        1f
                    }
                }
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(fill)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (s.on_tap != null) {
                        s.on_tap.invoke()
                        on_dismiss()
                    }
                }
                .pointerInput(s.key) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            toast_dragging = true
                        },
                        onDragCancel = {
                            toast_dragging = false
                            toast_scope.launch {
                                drag_offset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        onDragEnd = {
                            toast_dragging = false
                            val velocity = tracker.calculateVelocity().y
                            val travelled = drag_offset.value
                            val far_enough = travelled < -toast_dismiss_distance.toPx()
                            val fast_enough = velocity < -toast_dismiss_velocity.toPx()
                            if (far_enough || fast_enough) {
                                toast_scope.launch {
                                    drag_offset.animateTo(
                                        targetValue = -(toast_height + 120f),
                                        animationSpec = tween(durationMillis = 150),
                                    )
                                    s.on_close?.invoke()
                                    on_dismiss()
                                }
                            } else {
                                toast_scope.launch {
                                    drag_offset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.62f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        },
                    ) { change, drag ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        toast_scope.launch { drag_offset.snapTo(drag_offset.value + drag) }
                    }
                }
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
            Row(
                modifier = row_modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.message,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (s.secondary_label == null && s.undo_label == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (s.undo_label != null && s.on_undo != null) {
                    Spacer(Modifier.width(10.dp))
                    toast_action(
                        label = s.undo_label,
                        on_click = {
                            s.on_undo.invoke()
                            on_dismiss()
                        },
                    )
                }
                if (s.secondary_label != null) {
                    Spacer(Modifier.width(6.dp))
                    val secondary_click = {
                        s.on_secondary?.invoke()
                        on_dismiss()
                    }
                    if (s.secondary_icon != null) {
                        toast_icon_action(
                            icon = s.secondary_icon,
                            label = s.secondary_label,
                            enabled = s.on_secondary != null,
                            on_click = secondary_click,
                        )
                    } else {
                        toast_action(
                            label = s.secondary_label,
                            enabled = s.on_secondary != null,
                            on_click = secondary_click,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(SquircleShape(999.dp))
                        .background(toast_control_fill(colors))
                        .clickable {
                            s.on_close?.invoke()
                            on_dismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = null,
                        tint = colors.text_secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
