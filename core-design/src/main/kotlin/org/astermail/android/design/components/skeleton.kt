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

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.aster_reduce_motion

internal const val skeleton_reveal_delay_ms = 160L
internal const val skeleton_reveal_fade_ms = 180
internal const val skeleton_continuity_ms = 320L

internal fun skeleton_reveal_continues(visible_count: Int, last_hidden_at: Long, now: Long): Boolean =
    visible_count > 0 || (last_hidden_at > 0L && now - last_hidden_at in 0L until skeleton_continuity_ms)

private object skeleton_presence {
    var visible_count = 0
    var last_hidden_at = 0L

    fun continues(): Boolean =
        skeleton_reveal_continues(visible_count, last_hidden_at, SystemClock.uptimeMillis())

    fun mark_visible(counted: BooleanArray) {
        if (counted[0]) return
        counted[0] = true
        visible_count += 1
    }

    fun mark_hidden(counted: BooleanArray) {
        if (!counted[0]) return
        counted[0] = false
        visible_count = (visible_count - 1).coerceAtLeast(0)
        last_hidden_at = SystemClock.uptimeMillis()
    }
}

@Composable
fun Modifier.skeleton_reveal(): Modifier {
    val reduce_motion = aster_reduce_motion()
    val alpha = remember { Animatable(if (skeleton_presence.continues()) 1f else 0f) }
    val counted = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) {
        if (alpha.value >= 1f) skeleton_presence.mark_visible(counted)
        onDispose { skeleton_presence.mark_hidden(counted) }
    }
    LaunchedEffect(reduce_motion) {
        if (alpha.value >= 1f) return@LaunchedEffect
        delay(skeleton_reveal_delay_ms)
        skeleton_presence.mark_visible(counted)
        if (reduce_motion) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(1f, tween(skeleton_reveal_fade_ms, easing = AsterEasing.standard_decelerate))
        }
    }
    return this.graphicsLayer { this.alpha = alpha.value }
}

internal data class skeleton_line_slot(val top: Float, val fraction: Float)

private val skeleton_paragraph_lengths = intArrayOf(4, 3, 5, 2, 4, 3)
private val skeleton_full_fractions = floatArrayOf(1f, 0.94f, 0.97f, 0.9f, 0.96f, 0.92f)
private val skeleton_tail_fractions = floatArrayOf(0.62f, 0.38f, 0.74f, 0.5f, 0.44f, 0.68f)

internal fun skeleton_paragraph_slots(
    available_height: Float,
    line_height: Float,
    bar_height: Float,
    paragraph_gap: Float,
    max_lines: Int,
): List<skeleton_line_slot> {
    val slots = ArrayList<skeleton_line_slot>(max_lines.coerceAtLeast(0))
    val half_leading = ((line_height - bar_height) / 2f).coerceAtLeast(0f)
    var y = 0f
    var paragraph = 0
    var line_in_paragraph = 0
    var full_index = 0
    for (index in 0 until max_lines) {
        val length = skeleton_paragraph_lengths[paragraph % skeleton_paragraph_lengths.size]
        val is_tail = line_in_paragraph == length - 1
        val top = y + half_leading
        if (top + bar_height > available_height) break
        val fraction = if (is_tail) {
            skeleton_tail_fractions[paragraph % skeleton_tail_fractions.size]
        } else {
            skeleton_full_fractions[full_index++ % skeleton_full_fractions.size]
        }
        slots.add(skeleton_line_slot(top, fraction))
        y += line_height
        if (is_tail) {
            y += paragraph_gap
            paragraph += 1
            line_in_paragraph = 0
        } else {
            line_in_paragraph += 1
        }
    }
    return slots
}

@Composable
fun skeleton_paragraph(
    state: shimmer_appearance,
    modifier: Modifier = Modifier,
    max_lines: Int = 12,
    line_height: Dp = 22.dp,
    bar_height: Dp = 11.dp,
    paragraph_gap: Dp = 10.dp,
) {
    val shape = remember { RoundedCornerShape(percent = 50) }
    Layout(
        content = { repeat(max_lines) { Box(Modifier.shimmer(state, shape)) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val line_px = line_height.toPx()
        val bar_px = bar_height.toPx()
        val available = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.POSITIVE_INFINITY
        val slots = skeleton_paragraph_slots(available, line_px, bar_px, paragraph_gap.toPx(), max_lines)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 280.dp.roundToPx()
        val bar_height_px = bar_px.roundToInt().coerceAtLeast(1)
        val placeables = slots.mapIndexed { index, slot ->
            val line_width = (width * slot.fraction).roundToInt().coerceIn(0, width)
            measurables[index].measure(Constraints.fixed(line_width, bar_height_px))
        }
        val half_leading = ((line_px - bar_px) / 2f).coerceAtLeast(0f)
        val natural = slots.lastOrNull()?.let { (it.top + bar_px + half_leading).roundToInt() } ?: 0
        layout(width, constraints.constrainHeight(natural)) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(0, slots[index].top.roundToInt())
            }
        }
    }
}
