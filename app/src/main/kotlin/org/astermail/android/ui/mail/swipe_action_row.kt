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

package org.astermail.android.ui.mail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSemanticColors
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch
import org.astermail.android.design.AsterSpacing
import org.astermail.android.mail.folder_keeps_archived

const val swipe_claim_slop_multiplier = 1f
const val swipe_dominance_ratio = 1.2f
const val swipe_commit_fraction = 0.4f
const val swipe_fling_velocity = 900f
const val swipe_fling_min_fraction = 0.08f

enum class SwipeAxis { undecided, horizontal, vertical }

fun swipe_axis_for(
    dx: Float,
    dy: Float,
    slop: Float,
    dominance: Float = swipe_dominance_ratio,
): SwipeAxis {
    val ax = abs(dx)
    val ay = abs(dy)
    if (ay > slop && ay >= ax) return SwipeAxis.vertical
    if (ax > slop && ax > ay * dominance) return SwipeAxis.horizontal
    if (ay > slop) return SwipeAxis.vertical
    return SwipeAxis.undecided
}

fun swipe_commits(
    travelled: Float,
    velocity: Float,
    limit: Float,
    commit_fraction: Float = swipe_commit_fraction,
    fling_velocity: Float = swipe_fling_velocity,
): Boolean {
    if (travelled == 0f || limit <= 0f) return false
    if (abs(travelled) >= limit * commit_fraction) return true
    if (abs(travelled) < limit * swipe_fling_min_fraction) return false
    return abs(velocity) >= fling_velocity && sign(velocity) == sign(travelled)
}

fun is_removing_swipe_action(action: String): Boolean = action in setOf(
    "archive", "trash", "delete", "spam", "move_to_inbox", "unarchive",
    "restore_trash", "unmark_spam", "delete_permanent",
)

fun archive_removes_row(folder: String): Boolean = !folder_keeps_archived(folder)

fun swipe_action_removes_row(action: String, folder: String): Boolean =
    is_removing_swipe_action(action) && (action != "archive" || archive_removes_row(folder))

fun swipe_accessibility_actions(
    start_action: String,
    end_action: String,
    start_label: String,
    end_label: String,
    on_swipe_start: () -> Unit,
    on_swipe_end: () -> Unit,
): List<CustomAccessibilityAction> = buildList {
    if (start_action != "none") add(CustomAccessibilityAction(start_label) { on_swipe_start(); true })
    if (end_action != "none") add(CustomAccessibilityAction(end_label) { on_swipe_end(); true })
}

fun swipe_tone(color: Color, colors: AsterSemanticColors): Color {
    val target = if (colors.is_dark) 0.30f else 0.46f
    val level = color.luminance()
    val toned = if (level > target) {
        lerp(color, Color.Black, (((level - target) / (1f - target)) * 0.7f).coerceIn(0f, 0.62f))
    } else {
        lerp(color, Color.White, (((target - level) / target) * 0.5f).coerceIn(0f, 0.34f))
    }
    return toned.copy(alpha = 1f)
}

fun swipe_ink(surface: Color): Color =
    if (surface.luminance() > 0.5f) Color(0xFF14181F) else Color.White

@Composable
fun swipe_action_row(
    start_action: String,
    end_action: String,
    start_label: String,
    end_label: String,
    start_icon: ImageVector,
    end_icon: ImageVector,
    start_color: Color,
    end_color: Color,
    on_swipe_start: () -> Unit,
    on_swipe_end: () -> Unit,
    modifier: Modifier = Modifier,
    background_shape: Shape? = null,
    background_padding: PaddingValues = PaddingValues(0.dp),
    haptic_enabled: Boolean = true,
    list_scrolling: () -> Boolean = { false },
    reset_token: Int = 0,
    removes_row: (String) -> Boolean = ::is_removing_swipe_action,
    content: @Composable () -> Unit,
) {
    val haptics = org.astermail.android.design.remember_haptic()
    var is_dismissed by remember { mutableStateOf(false) }
    val offset_x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val current_removes_row by rememberUpdatedState(removes_row)
    val start_enabled = start_action != "none"
    val end_enabled = end_action != "none"

    LaunchedEffect(reset_token) {
        if (reset_token == 0) return@LaunchedEffect
        is_dismissed = false
        offset_x.animateTo(0f, tween(220))
    }

    Box(
        modifier = modifier
            .pointerInput(start_enabled, end_enabled) {
                if (!start_enabled && !end_enabled) return@pointerInput
                val slop = viewConfiguration.touchSlop
                val claim_distance = slop * swipe_claim_slop_multiplier
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (is_dismissed || list_scrolling()) continue
                        val limit = size.width.toFloat()
                        val commit_distance = limit * swipe_commit_fraction
                        val lower = if (end_enabled) -limit else 0f
                        val upper = if (start_enabled) limit else 0f
                        var dx = 0f
                        var dy = 0f
                        var position = 0f
                        var claimed = false
                        var released = false
                        var passed_commit = false
                        val velocity_tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        velocity_tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                dx += delta.x
                                dy += delta.y
                                velocity_tracker.addPosition(change.uptimeMillis, change.position)
                                if (!claimed) {
                                    val axis = swipe_axis_for(dx, dy, slop)
                                    if (axis == SwipeAxis.vertical) break
                                    if (axis == SwipeAxis.undecided) continue
                                    if (if (dx > 0f) !start_enabled else !end_enabled) break
                                    claimed = true
                                    change.consume()
                                    position = (offset_x.value + dx - sign(dx) * claim_distance).coerceIn(lower, upper)
                                } else {
                                    change.consume()
                                    position = (position + delta.x).coerceIn(lower, upper)
                                }
                                val target = position
                                scope.launch { offset_x.snapTo(target) }
                                val past = abs(target) >= commit_distance
                                if (past != passed_commit) {
                                    passed_commit = past
                                    if (haptic_enabled) {
                                        haptics(
                                            if (past) {
                                                org.astermail.android.design.aster_haptic.gesture_threshold
                                            } else {
                                                org.astermail.android.design.aster_haptic.tick
                                            },
                                        )
                                    }
                                }
                            }
                            released = true
                        } finally {
                            if (claimed && !released) scope.launch { offset_x.animateTo(0f, tween(220)) }
                        }
                        if (!claimed) continue
                        val travelled = position
                        val velocity_x = velocity_tracker.calculateVelocity().x
                        if (!swipe_commits(travelled, velocity_x, limit)) {
                            scope.launch { offset_x.animateTo(0f, tween(220)) }
                            continue
                        }
                        val action = if (travelled > 0f) start_action else end_action
                        if (current_removes_row(action)) {
                            is_dismissed = true
                            scope.launch { offset_x.animateTo(sign(travelled) * limit, tween(180)) }
                        } else {
                            scope.launch { offset_x.animateTo(0f, tween(220)) }
                        }
                        if (travelled > 0f) on_swipe_start() else on_swipe_end()
                    }
                }
            },
    ) {
        val travelled = offset_x.value
        if (travelled != 0f) {
            val towards_start = travelled > 0f
            val surface = swipe_tone(if (towards_start) start_color else end_color, AsterMaterial.colors)
            val ink = swipe_ink(surface)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(background_padding)
                    .let { if (background_shape != null) it.clip(background_shape) else it }
                    .background(surface)
                    .padding(horizontal = AsterSpacing.xl),
                contentAlignment = if (towards_start) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (towards_start) start_icon else end_icon,
                        contentDescription = if (towards_start) start_label else end_label,
                        tint = ink,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = if (towards_start) start_label else end_label,
                        color = ink,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Box(modifier = Modifier.absoluteOffset { IntOffset(offset_x.value.roundToInt(), 0) }) {
            content()
        }
    }
}
