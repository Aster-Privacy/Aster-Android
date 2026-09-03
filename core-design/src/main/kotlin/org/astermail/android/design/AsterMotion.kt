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

package org.astermail.android.design

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

object AsterEasing {
    val standard_in_out = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val standard_enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val standard_exit = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val emphasized_in_out = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val emphasized_enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val emphasized_exit = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val tap_down = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val tap_up = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val menu_enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val menu_exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val dialog_enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val dialog_exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val scrim = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

object AsterDuration {
    const val short_1 = 50
    const val short_2 = 100
    const val short_3 = 120
    const val short_4 = 180
    const val medium_1 = 200
    const val medium_2 = 250
    const val medium_3 = 300
    const val medium_4 = 350
    const val long_1 = 400
    const val long_2 = 450

    const val emphasized_enter = 400
    const val emphasized_exit = 250
    const val emphasized_enter_delay = 60
    const val pop_exit = 180
    const val pop_enter = 250

    const val tap_down = 120
    const val tap_up = 180

    const val menu_enter = 170
    const val menu_exit = 110
    const val menu_fade_enter = 110
    const val menu_fade_exit = 85
    const val menu_state_change = 110

    const val dialog_enter = 210
    const val dialog_exit = 130
    const val scrim_enter = 170
    const val scrim_exit = 130

    const val instant = 0
}

object AsterScale {
    const val menu_enter_from = 0.92f
    const val menu_exit_to = 0.96f
    const val dialog_enter_from = 0.94f
}

object AsterSlide {
    const val menu_dp = 8
    const val dialog_dp = 10
}

val local_reduce_motion = staticCompositionLocalOf { false }

fun system_animations_disabled(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (e: Exception) {
    false
}

@Composable
fun aster_reduce_motion(): Boolean {
    if (local_reduce_motion.current) return true
    val context = LocalContext.current
    return remember(context) { system_animations_disabled(context) }
}
