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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.astermail.android.design.AsterSpacing

const val swipe_claim_slop_multiplier = 3f
const val swipe_dominance_ratio = 2f
const val swipe_commit_fraction = 0.4f

fun is_removing_swipe_action(action: String): Boolean = action in setOf(
    "archive", "trash", "delete", "spam", "move_to_inbox", "unarchive",
    "restore_trash", "unmark_spam", "delete_permanent",
)

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
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var is_dismissed by remember { mutableStateOf(false) }
    val offset_x = remember { Animatable(0f) }
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
                coroutineScope {
                    while (true) {
                        val down = awaitPointerEventScope {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (is_dismissed || list_scrolling()) continue
                        offset_x.stop()
                        val limit = size.width.toFloat()
                        val commit_distance = limit * swipe_commit_fraction
                        var dx = 0f
                        var dy = 0f
                        var claimed = false
                        var passed_commit = false
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                dx += delta.x
                                dy += delta.y
                                if (!claimed) {
                                    if (abs(dy) > slop && abs(dy) >= abs(dx)) break
                                    if (abs(dx) < claim_distance) continue
                                    if (abs(dx) <= abs(dy) * swipe_dominance_ratio) break
                                    if (if (dx > 0f) !start_enabled else !end_enabled) break
                                    claimed = true
                                    change.consume()
                                    launch { offset_x.snapTo(dx - sign(dx) * claim_distance) }
                                } else {
                                    change.consume()
                                    val next = (offset_x.value + delta.x).coerceIn(
                                        if (end_enabled) -limit else 0f,
                                        if (start_enabled) limit else 0f,
                                    )
                                    launch { offset_x.snapTo(next) }
                                    if (!passed_commit && abs(next) >= commit_distance) {
                                        passed_commit = true
                                        if (haptic_enabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                            }
                        }
                        if (!claimed) continue
                        val travelled = offset_x.value
                        if (abs(travelled) < commit_distance) {
                            launch { offset_x.animateTo(0f, tween(220)) }
                            continue
                        }
                        val action = if (travelled > 0f) start_action else end_action
                        if (is_removing_swipe_action(action)) {
                            is_dismissed = true
                            launch { offset_x.animateTo(sign(travelled) * limit, tween(180)) }
                        } else {
                            launch { offset_x.animateTo(0f, tween(220)) }
                        }
                        if (travelled > 0f) on_swipe_start() else on_swipe_end()
                    }
                }
            },
    ) {
        val travelled = offset_x.value
        if (travelled != 0f) {
            val towards_start = travelled > 0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(background_padding)
                    .let { if (background_shape != null) it.clip(background_shape) else it }
                    .background(if (towards_start) start_color else end_color)
                    .padding(horizontal = AsterSpacing.xl),
                contentAlignment = if (towards_start) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (towards_start) start_icon else end_icon,
                        contentDescription = if (towards_start) start_label else end_label,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = if (towards_start) start_label else end_label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Box(modifier = Modifier.offset { IntOffset(offset_x.value.roundToInt(), 0) }) {
            content()
        }
    }
}
