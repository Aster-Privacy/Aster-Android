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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

class row_tap_guard {
    var blocked: Boolean = false
}

fun tap_opens_row(
    total_dx: Float,
    total_dy: Float,
    slop: Float,
    refresh_engaged: Boolean,
): Boolean {
    if (refresh_engaged) return false
    if (abs(total_dy) > slop) return false
    return abs(total_dx) <= slop
}

@Composable
fun Modifier.row_tap_guard(
    guard: row_tap_guard,
    refresh_engaged: () -> Boolean = { false },
): Modifier {
    val current_guard = remember(guard) { guard }
    return pointerInput(current_guard) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            current_guard.blocked = refresh_engaged()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val moved = change.position - down.position
                if (!tap_opens_row(moved.x, moved.y, slop, refresh_engaged())) {
                    current_guard.blocked = true
                }
                if (!change.pressed) break
            }
        }
    }
}
