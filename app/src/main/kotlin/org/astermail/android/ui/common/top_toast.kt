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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicLong
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpring
import org.astermail.android.design.aster_reduce_motion
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

internal val local_toast_card_bounds = staticCompositionLocalOf<((Long, toast_card_box?) -> Unit)?> { null }

private val toast_control_size = 32.dp

private val toast_max_width = 560.dp

private const val toast_enter_ms = 220

private const val toast_swap_ms = 120

private const val toast_exit_ms = 200

private val toast_hidden_margin = 24.dp

private val toast_bottom_clearance = 72.dp

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

@Composable
fun top_toast_overlay(
    state: TopToastState?,
    on_dismiss: (TopToastState) -> Unit,
    on_hold: (TopToastState, Boolean) -> Unit = { _, _ -> },
    fill_width: Boolean = true,
    top_padding: Dp? = null,
    edge: ToastEdge = ToastEdge.top,
    animate_first: Boolean = false,
    on_presence: (Boolean) -> Unit = {},
) {
    val reduce_motion = aster_reduce_motion()
    val starts_shown = !animate_first && state != null
    var shown by remember { mutableStateOf(if (starts_shown) state else null) }
    val presence = remember { Animatable(if (starts_shown) 1f else 0f) }
    val report_presence by rememberUpdatedState(on_presence)
    if (state != null && shown?.key == state.key && shown !== state) shown = state
    LaunchedEffect(state?.key) {
        val target = state
        if (target == null) {
            if (shown != null) {
                presence.animateTo(0f, tween(toast_exit_ms, easing = AsterEasing.emphasized_accelerate))
                shown = null
            }
            report_presence(false)
            return@LaunchedEffect
        }
        report_presence(true)
        val outgoing = shown
        if (outgoing != null && outgoing.key != target.key && presence.value > 0f) {
            presence.animateTo(0f, tween(toast_swap_ms, easing = AsterEasing.emphasized_accelerate))
        }
        shown = target
        presence.animateTo(1f, tween(toast_enter_ms, easing = AsterEasing.emphasized_decelerate))
    }
    val frame = if (fill_width) Modifier.fillMaxWidth() else Modifier
    val placed = when {
        top_padding != null && edge == ToastEdge.top -> frame.padding(top = top_padding)
        top_padding != null -> frame.padding(bottom = top_padding)
        edge == ToastEdge.top -> frame.statusBarsPadding()
        else -> frame.navigationBarsPadding().imePadding().padding(bottom = toast_bottom_clearance)
    }
    val alignment = if (edge == ToastEdge.top) Alignment.TopCenter else Alignment.BottomCenter
    Box(modifier = placed, contentAlignment = alignment) {
        val card = shown ?: return@Box
        androidx.compose.runtime.key(card.key) {
            toast_card(
                toast = card,
                is_current = state?.key == card.key,
                edge = edge,
                presence = { presence.value },
                reduce_motion = reduce_motion,
                on_dismiss = on_dismiss,
                on_hold = on_hold,
            )
        }
    }
}

@Composable
private fun toast_card(
    toast: TopToastState,
    is_current: Boolean,
    edge: ToastEdge,
    presence: () -> Float,
    reduce_motion: Boolean,
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
    val current_edge by rememberUpdatedState(edge)
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
    val report_bounds = local_toast_card_bounds.current
    val card_key = toast.key
    DisposableEffect(Unit) {
        onDispose {
            if (dragging) hold(latest, false)
            report_bounds?.invoke(card_key, null)
        }
    }
    val row_modifier = Modifier
        .graphicsLayer {
            val shown_fraction = presence().coerceIn(0f, 1f)
            val margin = toast_hidden_margin.toPx()
            val hidden = if (reduce_motion) 0f else toast_hidden_offset(edge, toast_height, margin)
            val raw = offset
            translationY = hidden * (1f - shown_fraction) + toast_drag_translation(edge, raw, toast_pull_resistance)
            val toward_edge = raw * toast_enter_sign(edge) > 0f
            val drag_alpha = if (toward_edge) {
                (1f - kotlin.math.abs(raw) / (toast_height.coerceAtLeast(1f) * 1.6f)).coerceIn(0.15f, 1f)
            } else {
                1f
            }
            alpha = shown_fraction * drag_alpha
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .widthIn(max = toast_max_width)
        .onSizeChanged { toast_height = it.height.toFloat() }
        .then(
            if (report_bounds == null) {
                Modifier
            } else {
                Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    report_bounds(card_key, toast_card_box(bounds.left, bounds.top, bounds.right, bounds.bottom))
                }
            },
        )
        .shadow(6.dp, shape, clip = false)
        .clip(shape)
        .background(fill)
        .then(if (colors.is_dark) Modifier else Modifier.border(1.dp, colors.border_secondary, shape))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = androidx.compose.material3.ripple(),
        ) {
            if (latest.on_tap != null) finish { it.on_tap?.invoke() }
        }
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            val spring_back: (Float) -> Unit = { velocity ->
                settle?.cancel()
                settle = scope.launch {
                    animate(
                        initialValue = offset,
                        targetValue = 0f,
                        initialVelocity = velocity,
                        animationSpec = AsterSpring.spatial(),
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
                    spring_back(0f)
                },
                onDragEnd = {
                    set_dragging(false)
                    val velocity = tracker.calculateVelocity().y
                    val sign = toast_enter_sign(current_edge)
                    val dismisses = toast_swipe_dismisses(
                        current_edge,
                        offset,
                        velocity,
                        toast_dismiss_distance.toPx(),
                        toast_dismiss_velocity.toPx(),
                    )
                    if (dismisses && active && !consumed) {
                        settle?.cancel()
                        settle = scope.launch {
                            val off_screen = sign * (toast_height + 120f)
                            animate(
                                initialValue = offset,
                                targetValue = off_screen,
                                initialVelocity = if (sign < 0f) velocity.coerceAtMost(0f) else velocity.coerceAtLeast(0f),
                                animationSpec = AsterSpring.fast_spatial(),
                            ) { value, _ ->
                                offset = value
                                if (value * sign >= toast_height) finish { it.on_close?.invoke() }
                            }
                            finish { it.on_close?.invoke() }
                        }
                    } else {
                        spring_back(velocity)
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
