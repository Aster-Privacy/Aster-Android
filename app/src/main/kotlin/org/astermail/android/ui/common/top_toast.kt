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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicLong
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.lighten
import org.astermail.android.R

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
    val key: Long = next_toast_key(),
    val accumulation_key: String? = null,
)

private val toast_key_counter = AtomicLong(0L)

fun next_toast_key(): Long = toast_key_counter.incrementAndGet()

private val toast_control_size = 32.dp

private val toast_max_width = 560.dp

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
    on_dismiss: (TopToastState) -> Unit,
    on_hold: (TopToastState, Boolean) -> Unit = { _, _ -> },
) {
    var last_state by remember { mutableStateOf<TopToastState?>(null) }
    if (state != null) last_state = state
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
            val shown = last_state ?: return@AnimatedVisibility
            key(shown.key) {
                toast_card(
                    toast = shown,
                    is_current = state?.key == shown.key,
                    on_dismiss = on_dismiss,
                    on_hold = on_hold,
                )
            }
        }
    }
}

@Composable
private fun toast_card(
    toast: TopToastState,
    is_current: Boolean,
    on_dismiss: (TopToastState) -> Unit,
    on_hold: (TopToastState, Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(26.dp)
    val fill = toast_surface_fill(colors)
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(toast)
    val active by rememberUpdatedState(is_current)
    val dismiss by rememberUpdatedState(on_dismiss)
    val hold by rememberUpdatedState(on_hold)
    var offset by remember { mutableFloatStateOf(0f) }
    var toast_height by remember { mutableFloatStateOf(0f) }
    var consumed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val finish: ((TopToastState) -> Unit) -> Unit = { action ->
        if (active && !consumed) {
            consumed = true
            val target = latest
            action(target)
            dismiss(target)
        }
    }
    val set_dragging: (Boolean) -> Unit = { value ->
        if (dragging != value) {
            dragging = value
            hold(latest, value)
        }
    }
    LaunchedEffect(is_current) {
        if (is_current) {
            settle?.cancel()
            consumed = false
            offset = 0f
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (dragging) hold(latest, false) }
    }
    val row_modifier = Modifier
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .widthIn(max = toast_max_width)
        .onSizeChanged { toast_height = it.height.toFloat() }
        .graphicsLayer {
            val raw = offset
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
            if (latest.on_tap != null) finish { it.on_tap?.invoke() }
        }
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            val spring_back: (Float) -> Unit = { damping ->
                settle?.cancel()
                settle = scope.launch {
                    animate(
                        initialValue = offset,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = damping,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { value, _ -> offset = value }
                }
            }
            detectVerticalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    settle?.cancel()
                    set_dragging(true)
                },
                onDragCancel = {
                    set_dragging(false)
                    spring_back(0.68f)
                },
                onDragEnd = {
                    set_dragging(false)
                    val velocity = tracker.calculateVelocity().y
                    val far_enough = offset < -toast_dismiss_distance.toPx()
                    val fast_enough = velocity < -toast_dismiss_velocity.toPx()
                    if ((far_enough || fast_enough) && active && !consumed) {
                        settle?.cancel()
                        settle = scope.launch {
                            animate(
                                initialValue = offset,
                                targetValue = -(toast_height + 120f),
                                animationSpec = tween(durationMillis = 150),
                            ) { value, _ -> offset = value }
                            finish { it.on_close?.invoke() }
                        }
                    } else {
                        spring_back(0.62f)
                    }
                },
            ) { change, drag ->
                tracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                offset += drag
            }
        }
        .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
    Row(
        modifier = row_modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = toast.message,
            color = colors.text_primary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            maxLines = if (toast.secondary_label == null && toast.undo_label == null) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (toast.undo_label != null && toast.on_undo != null) {
            Spacer(Modifier.width(10.dp))
            toast_action(
                label = toast.undo_label,
                on_click = { finish { it.on_undo?.invoke() } },
            )
        }
        if (toast.secondary_label != null) {
            Spacer(Modifier.width(6.dp))
            val secondary_click = { finish { it.on_secondary?.invoke() } }
            if (toast.secondary_icon != null) {
                toast_icon_action(
                    icon = toast.secondary_icon,
                    label = toast.secondary_label,
                    enabled = toast.on_secondary != null,
                    on_click = secondary_click,
                )
            } else {
                toast_action(
                    label = toast.secondary_label,
                    enabled = toast.on_secondary != null,
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
                .clickable(role = Role.Button) { finish { it.on_close?.invoke() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = stringResource(R.string.close),
                tint = colors.text_secondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
