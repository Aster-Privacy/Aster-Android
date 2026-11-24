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
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.PathEasing
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

object AsterEasing {
    val emphasized: Easing = PathEasing(
        PathParser().parsePathString("M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4 C 0.208333, 0.82, 0.25, 1, 1, 1").toPath(),
    )
    val emphasized_decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasized_accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val legacy = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val back_progress = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard_decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val standard_accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val standard_in_out = standard
    val standard_enter = standard_decelerate
    val standard_exit = standard_accelerate
    val emphasized_in_out = emphasized
    val emphasized_enter = emphasized_decelerate
    val emphasized_exit = emphasized_accelerate
    val tap_down = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val tap_up = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val menu_enter = emphasized_decelerate
    val menu_exit = emphasized_accelerate
    val dialog_enter = emphasized_decelerate
    val dialog_exit = emphasized_accelerate
    val scrim = standard
    val island = AsterIslandMotion.ease
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

object AsterSpring {
    const val spatial_damping = 0.9f
    const val spatial_stiffness = 700f
    const val fast_spatial_stiffness = 1400f
    const val slow_spatial_stiffness = 300f
    const val effects_damping = 1f
    const val effects_stiffness = 1600f
    const val fast_effects_stiffness = 3800f
    const val expressive_fast_spatial_damping = 0.6f
    const val expressive_fast_spatial_stiffness = 800f

    fun <T> spatial(): SpringSpec<T> = spring(spatial_damping, spatial_stiffness)
    fun <T> fast_spatial(): SpringSpec<T> = spring(spatial_damping, fast_spatial_stiffness)
    fun <T> slow_spatial(): SpringSpec<T> = spring(spatial_damping, slow_spatial_stiffness)
    fun <T> effects(): SpringSpec<T> = spring(effects_damping, effects_stiffness)
    fun <T> fast_effects(): SpringSpec<T> = spring(effects_damping, fast_effects_stiffness)
    fun <T> pop(): SpringSpec<T> = spring(expressive_fast_spatial_damping, expressive_fast_spatial_stiffness)
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
