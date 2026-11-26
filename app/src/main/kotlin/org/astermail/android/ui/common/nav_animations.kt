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

import android.content.res.Resources
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import org.astermail.android.design.AsterBackMotion
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMotionSpec
import org.astermail.android.design.aster_fade_in
import org.astermail.android.design.aster_fade_out
import org.astermail.android.design.aster_threshold_easing

const val nav_anim_forward_ms = 280
const val nav_anim_backward_ms = 240
const val nav_anim_duration_ms = nav_anim_forward_ms
const val nav_slide_fraction = 0.1f
const val nav_sheet_exit_ms = 250
private const val nav_slide_dp = 44f

private val nav_fade_out_easing =
    aster_threshold_easing(LinearEasing, 0f, AsterMotionSpec.fade_through_threshold)
private val nav_fade_in_easing =
    aster_threshold_easing(AsterEasing.emphasized_decelerate, 0.08f, 0.6f)
private val nav_sheet_fade_in_easing = aster_threshold_easing(LinearEasing, 0f, 0.3f)
private val nav_predictive_fade_out_easing = aster_threshold_easing(LinearEasing, 0.8f, 1f)
private val nav_through_out_easing =
    aster_threshold_easing(AsterEasing.emphasized, 0f, AsterMotionSpec.fade_through_threshold)
private val nav_through_in_easing =
    aster_threshold_easing(AsterEasing.emphasized, AsterMotionSpec.fade_through_threshold, 1f)

private fun default_slide_px(): Int = (nav_slide_dp * Resources.getSystem().displayMetrics.density).toInt()

private fun shared_axis_enter(duration: Int, from_end: Boolean, slide_px: Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = AsterEasing.emphasized_decelerate),
        initialOffsetX = { (if (from_end) 1 else -1) * slide_px },
    ) + fadeIn(animationSpec = tween(durationMillis = duration, easing = nav_fade_in_easing))

private fun shared_axis_exit(duration: Int, to_start: Boolean, slide_px: Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(durationMillis = duration, easing = AsterEasing.emphasized_decelerate),
        targetOffsetX = { (if (to_start) -1 else 1) * slide_px },
    ) + fadeOut(animationSpec = tween(durationMillis = duration, easing = nav_fade_out_easing))

fun nav_forward_enter(duration: Int = nav_anim_forward_ms, slide_px: Int? = null): EnterTransition {
    if (duration == 0) return aster_fade_in(true)
    return shared_axis_enter(duration, from_end = true, slide_px = slide_px ?: default_slide_px())
}

fun nav_forward_exit(duration: Int = nav_anim_forward_ms, slide_px: Int? = null): ExitTransition {
    if (duration == 0) return aster_fade_out(true)
    return shared_axis_exit(duration, to_start = true, slide_px = slide_px ?: default_slide_px())
}

fun nav_backward_enter(duration: Int = nav_anim_backward_ms, slide_px: Int? = null): EnterTransition {
    if (duration == 0) return aster_fade_in(true)
    return shared_axis_enter(duration, from_end = false, slide_px = slide_px ?: default_slide_px())
}

fun nav_backward_exit(duration: Int = nav_anim_backward_ms, slide_px: Int? = null): ExitTransition {
    if (duration == 0) return aster_fade_out(true)
    return shared_axis_exit(duration, to_start = false, slide_px = slide_px ?: default_slide_px())
}

fun nav_sheet_enter(duration: Int = nav_anim_forward_ms): EnterTransition {
    if (duration == 0) return aster_fade_in(true)
    return slideInVertically(
        animationSpec = tween(durationMillis = duration, easing = AsterEasing.emphasized_decelerate),
        initialOffsetY = { h -> h },
    ) + fadeIn(animationSpec = tween(durationMillis = duration, easing = nav_sheet_fade_in_easing))
}

fun nav_sheet_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return aster_fade_out(true)
    return slideOutVertically(
        animationSpec = tween(durationMillis = nav_sheet_exit_ms, easing = AsterEasing.emphasized_accelerate),
        targetOffsetY = { h -> h },
    ) + fadeOut(animationSpec = tween(durationMillis = nav_sheet_exit_ms, easing = nav_predictive_fade_out_easing))
}

fun nav_predictive_pop_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return aster_fade_out(true)
    return scaleOut(
        animationSpec = tween(durationMillis = duration, easing = AsterEasing.back_progress),
        targetScale = AsterBackMotion.min_scale,
    ) + fadeOut(animationSpec = tween(durationMillis = duration, easing = nav_predictive_fade_out_easing))
}

fun nav_predictive_pop_enter(duration: Int = nav_anim_backward_ms, offset_px: Int? = null): EnterTransition {
    if (duration == 0) return aster_fade_in(true)
    val start_px = offset_px
        ?: (AsterBackMotion.entering_start_offset_dp * Resources.getSystem().displayMetrics.density).toInt()
    return slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = AsterEasing.back_progress),
        initialOffsetX = { -start_px },
    )
}

fun nav_search_enter(duration: Int = nav_anim_forward_ms): EnterTransition {
    if (duration == 0) return aster_fade_in(true)
    return fadeIn(animationSpec = tween(durationMillis = duration, easing = nav_through_in_easing))
}

fun nav_search_exit(duration: Int = nav_anim_backward_ms): ExitTransition {
    if (duration == 0) return aster_fade_out(true)
    return fadeOut(animationSpec = tween(durationMillis = duration, easing = nav_through_out_easing))
}

fun nav_search_under_enter(duration: Int = nav_anim_backward_ms): EnterTransition {
    if (duration == 0) return aster_fade_in(true)
    return fadeIn(animationSpec = tween(durationMillis = duration, easing = nav_through_in_easing))
}

fun nav_search_under_exit(duration: Int = nav_anim_forward_ms): ExitTransition {
    if (duration == 0) return aster_fade_out(true)
    return fadeOut(animationSpec = tween(durationMillis = duration, easing = nav_through_out_easing))
}
