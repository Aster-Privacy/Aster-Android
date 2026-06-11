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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

const val nav_anim_forward_ms = 300
const val nav_anim_backward_ms = 280
const val nav_anim_duration_ms = nav_anim_forward_ms
const val nav_slide_fraction = 0.22f
private const val nav_fade_in_ms = 220
private const val nav_fade_out_ms = 200

private val nav_easing_enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val nav_easing_exit = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

fun nav_forward_enter(duration: Int = nav_anim_forward_ms): EnterTransition {
    if (duration == 0) return EnterTransition.None
    return slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_enter),
        initialOffsetX = { w -> (w * nav_slide_fraction).toInt() },
    ) + fadeIn(animationSpec = tween(durationMillis = nav_fade_in_ms, easing = nav_easing_enter))
}

fun nav_forward_exit(duration: Int = nav_anim_forward_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_exit),
        targetOffsetX = { w -> -(w * nav_slide_fraction).toInt() },
    ) + fadeOut(animationSpec = tween(durationMillis = nav_fade_out_ms, easing = nav_easing_exit))
}

fun nav_backward_enter(duration: Int = nav_anim_backward_ms): EnterTransition {
    if (duration == 0) return EnterTransition.None
    return slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_enter),
        initialOffsetX = { w -> -(w * nav_slide_fraction).toInt() },
    ) + fadeIn(animationSpec = tween(durationMillis = nav_fade_in_ms, easing = nav_easing_enter))
}

fun nav_backward_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_exit),
        targetOffsetX = { w -> (w * nav_slide_fraction).toInt() },
    ) + fadeOut(animationSpec = tween(durationMillis = nav_fade_out_ms, easing = nav_easing_exit))
}

fun nav_sheet_enter(duration: Int = nav_anim_forward_ms): EnterTransition {
    if (duration == 0) return EnterTransition.None
    return slideInVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        initialOffsetY = { h -> h },
    ) + fadeIn(animationSpec = tween(durationMillis = nav_fade_in_ms, easing = nav_easing_enter))
}

fun nav_sheet_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return slideOutVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        targetOffsetY = { h -> h },
    ) + fadeOut(animationSpec = tween(durationMillis = nav_fade_out_ms, easing = nav_easing_exit))
}
