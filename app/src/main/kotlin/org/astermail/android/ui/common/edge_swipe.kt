// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import kotlin.math.abs

private val edge_swipe_zone = 24.dp

internal enum class EdgeSwipeDecision { pending, open, release }

internal fun edge_swipe_starts_in_zone(x_px: Float, width_px: Float, zone_px: Float, rtl: Boolean): Boolean =
    if (rtl) x_px >= width_px - zone_px else x_px <= zone_px

internal fun edge_swipe_decision(dx_px: Float, dy_px: Float, slop_px: Float, rtl: Boolean): EdgeSwipeDecision {
    val inward = if (rtl) -dx_px else dx_px
    return when {
        abs(dy_px) > slop_px && abs(dy_px) >= abs(dx_px) -> EdgeSwipeDecision.release
        inward < -slop_px -> EdgeSwipeDecision.release
        inward > slop_px && inward > abs(dy_px) -> EdgeSwipeDecision.open
        else -> EdgeSwipeDecision.pending
    }
}

fun Modifier.edge_swipe_to_open(enabled: Boolean, on_open: () -> Unit): Modifier = composed {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slop = LocalViewConfiguration.current.touchSlop
    val live_enabled by rememberUpdatedState(enabled)
    val live_on_open by rememberUpdatedState(on_open)
    pointerInput(rtl, slop) {
        val zone = edge_swipe_zone.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!live_enabled) return@awaitEachGesture
            if (!edge_swipe_starts_in_zone(down.position.x, size.width.toFloat(), zone, rtl)) return@awaitEachGesture
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                when (edge_swipe_decision(dx, dy, slop, rtl)) {
                    EdgeSwipeDecision.release -> return@awaitEachGesture
                    EdgeSwipeDecision.open -> {
                        change.consume()
                        live_on_open()
                        while (true) {
                            val rest = awaitPointerEvent(PointerEventPass.Initial)
                            rest.changes.forEach { it.consume() }
                            if (rest.changes.none { it.pressed }) return@awaitEachGesture
                        }
                    }
                    EdgeSwipeDecision.pending -> Unit
                }
            }
        }
    }
}
