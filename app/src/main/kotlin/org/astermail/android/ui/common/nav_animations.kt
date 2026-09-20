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

package org.astermail.android.ui.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import org.astermail.android.design.AsterEasing

const val nav_anim_forward_ms = 280
const val nav_anim_backward_ms = 260
const val nav_anim_duration_ms = nav_anim_forward_ms
const val nav_slide_fraction = 0.1f
private const val nav_fade_out_ms = 90
private const val nav_fade_in_delay_ms = 70

private val nav_easing_emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val nav_easing_fade_in = CubicBezierEasing(0f, 0f, 0.2f, 1f)
private val nav_easing_fade_out = CubicBezierEasing(0.4f, 0f, 1f, 1f)

private fun shared_axis_enter(duration: Int, from_end: Boolean): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_emphasized),
        initialOffsetX = { w -> ((if (from_end) 1 else -1) * w * nav_slide_fraction).toInt() },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = (duration - nav_fade_in_delay_ms).coerceAtLeast(1),
            delayMillis = nav_fade_in_delay_ms,
            easing = nav_easing_fade_in,
        ),
    )

private fun shared_axis_exit(duration: Int, to_start: Boolean): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_emphasized),
        targetOffsetX = { w -> ((if (to_start) -1 else 1) * w * nav_slide_fraction).toInt() },
    ) + fadeOut(animationSpec = tween(durationMillis = nav_fade_out_ms, easing = nav_easing_fade_out))

fun nav_forward_enter(duration: Int = nav_anim_forward_ms): EnterTransition {
    if (duration == 0) return EnterTransition.None
    return shared_axis_enter(duration, from_end = true)
}

fun nav_forward_exit(duration: Int = nav_anim_forward_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return shared_axis_exit(duration, to_start = true)
}

fun nav_backward_enter(duration: Int = nav_anim_backward_ms): EnterTransition {
    if (duration == 0) return EnterTransition.None
    return shared_axis_enter(duration, from_end = false)
}

fun nav_backward_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return shared_axis_exit(duration, to_start = false)
}

fun nav_sheet_enter(duration: Int = nav_anim_forward_ms): EnterTransition {
    if (duration == 0) return EnterTransition.None
    return slideInVertically(
        animationSpec = tween(durationMillis = 320, easing = AsterEasing.standard_enter),
        initialOffsetY = { h -> h },
    ) + fadeIn(animationSpec = tween(durationMillis = 150, easing = LinearEasing))
}

fun nav_sheet_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return slideOutVertically(
        animationSpec = tween(durationMillis = 220, easing = AsterEasing.standard_exit),
        targetOffsetY = { h -> h },
    ) + fadeOut(animationSpec = tween(durationMillis = 130, easing = LinearEasing))
}
