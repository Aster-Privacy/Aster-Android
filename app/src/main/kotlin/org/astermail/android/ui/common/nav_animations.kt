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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import org.astermail.android.design.AsterEasing

const val nav_anim_forward_ms = 220
const val nav_anim_backward_ms = 200
const val nav_anim_duration_ms = nav_anim_forward_ms
const val nav_slide_fraction = 0.18f
const val nav_anim_expand_ms = 500
const val nav_anim_collapse_ms = 220
private const val nav_fade_in_ms = 150
private const val nav_fade_out_ms = 130

private val nav_easing_expand = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val nav_easing_enter = AsterEasing.standard_enter
private val nav_easing_exit = AsterEasing.standard_exit

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
        animationSpec = tween(durationMillis = 320, easing = AsterEasing.standard_enter),
        initialOffsetY = { h -> h },
    ) + fadeIn(animationSpec = tween(durationMillis = nav_fade_in_ms, easing = nav_easing_enter))
}

fun nav_sheet_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return slideOutVertically(
        animationSpec = tween(durationMillis = 220, easing = AsterEasing.standard_exit),
        targetOffsetY = { h -> h },
    ) + fadeOut(animationSpec = tween(durationMillis = nav_fade_out_ms, easing = nav_easing_exit))
}

private val nav_expand_origin = TransformOrigin(0.5f, 0.09f)

fun nav_expand_enter(duration: Int = nav_anim_expand_ms): EnterTransition {
    if (duration == 0) return EnterTransition.None
    return scaleIn(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_expand),
        initialScale = 0.9f,
        transformOrigin = nav_expand_origin,
    ) + fadeIn(animationSpec = tween(durationMillis = 140, easing = nav_easing_enter))
}

fun nav_expand_exit(duration: Int = nav_anim_collapse_ms): ExitTransition {
    if (duration == 0) return ExitTransition.None
    return scaleOut(
        animationSpec = tween(durationMillis = duration, easing = nav_easing_expand),
        targetScale = 0.9f,
        transformOrigin = nav_expand_origin,
    ) + fadeOut(animationSpec = tween(durationMillis = duration, easing = nav_easing_exit))
}
