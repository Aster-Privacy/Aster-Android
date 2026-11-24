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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import org.astermail.android.mail.message_preloader

internal fun press_turned_into_scroll(travel: Offset, touch_slop: Float, consumed_elsewhere: Boolean): Boolean =
    consumed_elsewhere || travel.getDistance() > touch_slop

internal fun Modifier.press_preload(item_id: String, enabled: Boolean): Modifier {
    if (!enabled || item_id.isBlank()) return this
    return pointerInput(item_id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            message_preloader.request(item_id)
            val slop = viewConfiguration.touchSlop
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val travel = change.position - down.position
                if (press_turned_into_scroll(travel, slop, change.isConsumed && travel != Offset.Zero)) {
                    message_preloader.cancel_if_running(item_id)
                    break
                }
            }
        }
    }
}
