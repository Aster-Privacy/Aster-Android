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

package org.astermail.android.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class aster_haptic {
    tap,
    tick,
    toggle_on,
    toggle_off,
    confirm,
    reject,
    gesture_threshold,
    long_press,
}

internal fun aster_haptic_constant(kind: aster_haptic, sdk: Int = Build.VERSION.SDK_INT): Int = when (kind) {
    aster_haptic.tap -> HapticFeedbackConstants.VIRTUAL_KEY
    aster_haptic.tick -> HapticFeedbackConstants.CLOCK_TICK
    aster_haptic.toggle_on -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.VIRTUAL_KEY
    aster_haptic.toggle_off -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
    aster_haptic.confirm -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    aster_haptic.reject -> if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    aster_haptic.gesture_threshold ->
        if (sdk >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
    aster_haptic.long_press -> HapticFeedbackConstants.LONG_PRESS
}

fun View.aster_perform_haptic(kind: aster_haptic) {
    performHapticFeedback(aster_haptic_constant(kind))
}

@Composable
fun remember_haptic(): (aster_haptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind -> view.aster_perform_haptic(kind) } }
}
