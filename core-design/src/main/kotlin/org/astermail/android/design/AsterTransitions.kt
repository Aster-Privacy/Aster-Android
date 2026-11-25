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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

object AsterMotionSpec {
    const val transition_ms = 450
    const val fade_through_threshold = 0.35f
    const val fade_through_from = 0.92f
    const val shared_axis_z_from = 0.8f
    const val shared_axis_z_to = 1.1f
    const val fade_enter_ms = 400
    const val fade_exit_ms = 150
    const val fade_enter_threshold = 0.3f
    const val fade_from = 0.8f
    const val expand_ms = 280
    const val collapse_ms = 200
    const val reduced_fade_ms = 0
    const val reduced_fade_out_ms = 0
    const val icon_from = 0.8f
    const val fade_in_ms = 150
    const val fade_out_ms = 75
    const val color_ms = 150
    const val icon_ms = 200
    const val pop_from = 0.8f
    const val through_from = 0.92f
}

fun aster_threshold_easing(base: Easing, start: Float, end: Float): Easing =
    Easing { t -> ((base.transform(t) - start) / (end - start)).coerceIn(0f, 1f) }

private val fade_through_out_easing = aster_threshold_easing(AsterEasing.emphasized, 0f, AsterMotionSpec.fade_through_threshold)
private val fade_through_in_easing = aster_threshold_easing(AsterEasing.emphasized, AsterMotionSpec.fade_through_threshold, 1f)
private val fade_enter_alpha_easing = aster_threshold_easing(AsterEasing.emphasized_decelerate, 0f, AsterMotionSpec.fade_enter_threshold)

fun aster_fade_in(reduce_motion: Boolean = false): EnterTransition =
    if (reduce_motion) {
        EnterTransition.None
    } else {
        fadeIn(tween(AsterMotionSpec.fade_enter_ms, easing = fade_enter_alpha_easing))
    }

fun aster_fade_out(reduce_motion: Boolean = false): ExitTransition =
    if (reduce_motion) {
        ExitTransition.None
    } else {
        fadeOut(tween(AsterMotionSpec.fade_exit_ms, easing = AsterEasing.emphasized_accelerate))
    }

fun aster_expand_in(reduce_motion: Boolean = false, expand_from: Alignment.Vertical = Alignment.Top): EnterTransition {
    if (reduce_motion) return aster_fade_in(true)
    return expandVertically(
        animationSpec = tween(AsterMotionSpec.expand_ms, easing = AsterEasing.emphasized_decelerate),
        expandFrom = expand_from,
    ) + fadeIn(tween(AsterMotionSpec.fade_exit_ms + 30, easing = AsterEasing.standard_decelerate))
}

fun aster_collapse_out(reduce_motion: Boolean = false, shrink_towards: Alignment.Vertical = Alignment.Top): ExitTransition {
    if (reduce_motion) return aster_fade_out(true)
    return shrinkVertically(
        animationSpec = tween(AsterMotionSpec.collapse_ms, easing = AsterEasing.standard),
        shrinkTowards = shrink_towards,
    ) + fadeOut(tween(AsterMotionSpec.fade_exit_ms - 30, easing = AsterEasing.standard))
}

fun aster_expand_horizontal_in(reduce_motion: Boolean = false): EnterTransition {
    if (reduce_motion) return aster_fade_in(true)
    return expandHorizontally(
        animationSpec = tween(AsterMotionSpec.expand_ms, easing = AsterEasing.emphasized_decelerate),
    ) + fadeIn(tween(AsterMotionSpec.fade_exit_ms + 30, easing = AsterEasing.standard_decelerate))
}

fun aster_collapse_horizontal_out(reduce_motion: Boolean = false): ExitTransition {
    if (reduce_motion) return aster_fade_out(true)
    return shrinkHorizontally(
        animationSpec = tween(AsterMotionSpec.collapse_ms, easing = AsterEasing.standard),
    ) + fadeOut(tween(AsterMotionSpec.fade_exit_ms, easing = AsterEasing.standard))
}

fun aster_pop_in(reduce_motion: Boolean = false, from: Float = AsterMotionSpec.fade_from): EnterTransition {
    if (reduce_motion) return aster_fade_in(true)
    return scaleIn(
        animationSpec = tween(AsterMotionSpec.fade_enter_ms, easing = AsterEasing.emphasized_decelerate),
        initialScale = from,
    ) + fadeIn(tween(AsterMotionSpec.fade_enter_ms, easing = fade_enter_alpha_easing))
}

fun aster_pop_out(reduce_motion: Boolean = false): ExitTransition = aster_fade_out(reduce_motion)

private fun aster_size_transform(reduce_motion: Boolean, clip: Boolean, duration_ms: Int = AsterMotionSpec.transition_ms): SizeTransform =
    SizeTransform(clip = clip) { _, _ ->
        if (reduce_motion) snap() else tween(duration_ms, easing = AsterEasing.emphasized)
    }

fun aster_fade_through(reduce_motion: Boolean = false, clip_size: Boolean = false): ContentTransform {
    if (reduce_motion) return aster_crossfade(true)
    return ContentTransform(
        targetContentEnter = fadeIn(tween(AsterMotionSpec.transition_ms, easing = fade_through_in_easing)) +
            scaleIn(
                animationSpec = tween(AsterMotionSpec.transition_ms, easing = AsterEasing.emphasized),
                initialScale = AsterMotionSpec.fade_through_from,
            ),
        initialContentExit = fadeOut(tween(AsterMotionSpec.transition_ms, easing = fade_through_out_easing)),
        sizeTransform = aster_size_transform(false, clip_size),
    )
}

fun aster_crossfade(reduce_motion: Boolean = false): ContentTransform =
    ContentTransform(
        targetContentEnter = aster_fade_in(reduce_motion),
        initialContentExit = aster_fade_out(reduce_motion),
        sizeTransform = aster_size_transform(reduce_motion, false),
    )

fun aster_shared_axis_x(forward: Boolean, slide_px: Int, reduce_motion: Boolean = false): ContentTransform {
    if (reduce_motion) return aster_crossfade(true)
    val dir = if (forward) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInHorizontally(
            animationSpec = tween(AsterMotionSpec.transition_ms, easing = AsterEasing.emphasized),
            initialOffsetX = { dir * slide_px },
        ) + fadeIn(tween(AsterMotionSpec.transition_ms, easing = fade_through_in_easing)),
        initialContentExit = slideOutHorizontally(
            animationSpec = tween(AsterMotionSpec.transition_ms, easing = AsterEasing.emphasized),
            targetOffsetX = { -dir * slide_px },
        ) + fadeOut(tween(AsterMotionSpec.transition_ms, easing = fade_through_out_easing)),
        sizeTransform = aster_size_transform(false, false),
    )
}

fun aster_shared_axis_y(forward: Boolean, slide_px: Int, reduce_motion: Boolean = false): ContentTransform {
    if (reduce_motion) return aster_crossfade(true)
    val dir = if (forward) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInVertically(
            animationSpec = tween(AsterMotionSpec.transition_ms, easing = AsterEasing.emphasized),
            initialOffsetY = { dir * slide_px },
        ) + fadeIn(tween(AsterMotionSpec.transition_ms, easing = fade_through_in_easing)),
        initialContentExit = slideOutVertically(
            animationSpec = tween(AsterMotionSpec.transition_ms, easing = AsterEasing.emphasized),
            targetOffsetY = { -dir * slide_px },
        ) + fadeOut(tween(AsterMotionSpec.transition_ms, easing = fade_through_out_easing)),
        sizeTransform = aster_size_transform(false, false),
    )
}

fun aster_shared_axis_z(forward: Boolean, reduce_motion: Boolean = false): ContentTransform {
    if (reduce_motion) return aster_crossfade(true)
    val in_from = if (forward) AsterMotionSpec.shared_axis_z_from else AsterMotionSpec.shared_axis_z_to
    val out_to = if (forward) AsterMotionSpec.shared_axis_z_to else AsterMotionSpec.shared_axis_z_from
    return ContentTransform(
        targetContentEnter = scaleIn(tween(AsterMotionSpec.transition_ms, easing = AsterEasing.emphasized), initialScale = in_from) +
            fadeIn(tween(AsterMotionSpec.transition_ms, easing = fade_through_in_easing)),
        initialContentExit = scaleOut(tween(AsterMotionSpec.transition_ms, easing = AsterEasing.emphasized), targetScale = out_to) +
            fadeOut(tween(AsterMotionSpec.transition_ms, easing = fade_through_out_easing)),
        sizeTransform = aster_size_transform(false, false),
    )
}

@Composable
fun aster_shared_axis_slide_px(): Int = with(LocalDensity.current) { 30.dp.roundToPx() }

@Composable
fun AsterExpandVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduce = aster_reduce_motion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = aster_expand_in(reduce),
        exit = aster_collapse_out(reduce),
    ) { content() }
}

@Composable
fun ColumnScope.AsterExpandVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduce = aster_reduce_motion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = aster_expand_in(reduce),
        exit = aster_collapse_out(reduce),
    ) { content() }
}

@Composable
fun AsterPopVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduce = aster_reduce_motion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = aster_pop_in(reduce),
        exit = aster_pop_out(reduce),
    ) { content() }
}

@Composable
fun RowScope.AsterPopVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduce = aster_reduce_motion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = aster_pop_in(reduce),
        exit = aster_pop_out(reduce),
    ) { content() }
}

@Composable
fun <S> AsterFadeThrough(
    target_state: S,
    modifier: Modifier = Modifier,
    content_alignment: Alignment = Alignment.TopStart,
    label: String = "aster_fade_through",
    content_key: (S) -> Any? = { it },
    content: @Composable (S) -> Unit,
) {
    val reduce = aster_reduce_motion()
    AnimatedContent(
        targetState = target_state,
        modifier = modifier,
        contentAlignment = content_alignment,
        transitionSpec = { aster_fade_through(reduce) },
        contentKey = content_key,
        label = label,
    ) { s -> content(s) }
}

@Composable
fun <S> AsterIconSwap(
    target_state: S,
    modifier: Modifier = Modifier,
    label: String = "aster_icon_swap",
    content: @Composable (S) -> Unit,
) {
    val reduce = aster_reduce_motion()
    AnimatedContent(
        targetState = target_state,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            if (reduce) {
                aster_crossfade(true)
            } else {
                ContentTransform(
                    targetContentEnter = scaleIn(tween(AsterMotionSpec.icon_ms, easing = AsterEasing.emphasized_decelerate), initialScale = AsterMotionSpec.icon_from) +
                        fadeIn(tween(AsterMotionSpec.fade_in_ms, easing = AsterEasing.standard)),
                    initialContentExit = scaleOut(tween(AsterMotionSpec.fade_out_ms + 25, easing = AsterEasing.emphasized_accelerate), targetScale = AsterMotionSpec.icon_from) +
                        fadeOut(tween(AsterMotionSpec.fade_out_ms)),
                    sizeTransform = SizeTransform(clip = false),
                )
            }
        },
        label = label,
    ) { s -> content(s) }
}

@Composable
fun aster_animate_color(target: Color, label: String = "aster_color", duration_ms: Int = AsterMotionSpec.color_ms): State<Color> {
    val reduce = aster_reduce_motion()
    return animateColorAsState(
        targetValue = target,
        animationSpec = if (reduce) snap() else tween(duration_ms, easing = AsterEasing.standard),
        label = label,
    )
}

@Composable
fun Modifier.aster_error_shake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    val reduce = aster_reduce_motion()
    val density = LocalDensity.current
    LaunchedEffect(trigger) {
        if (trigger == 0 || reduce) return@LaunchedEffect
        val steps = listOf(12f, -10f, 7f, -4f, 2f, 0f)
        for (dp in steps) {
            offset.animateTo(with(density) { dp.dp.toPx() }, tween(45, easing = AsterEasing.standard))
        }
    }
    return this.graphicsLayer { translationX = offset.value }
}

object AsterBackMotion {
    const val min_scale = 0.9f
    const val edge_margin_dp = 8f
    const val max_offset_y_dp = 24f
    const val max_offset_x_ratio = 1f / 20f
    const val entering_start_offset_dp = 96f
    const val commit_ms = 450
    const val cancel_ms = 100
    const val closing_fade_rate = 5f
    const val scrim_light = 0.2f
    const val scrim_dark = 0.8f
    const val sheet_max_scale_x_dp = 48f
    const val sheet_max_scale_y_dp = 24f
    const val drawer_shrink_dp = 24f
    const val drawer_grow_dp = 12f
    const val drawer_scale_y_dp = 48f

    fun progress(raw: Float): Float = AsterEasing.back_progress.transform(raw.coerceIn(0f, 1f))
}
