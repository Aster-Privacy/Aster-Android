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
package org.astermail.android.ui.settings.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.aster_reduce_motion

internal const val domain_skeleton_delay_ms = 150L

@Composable
internal fun remember_delayed_flag(active: Boolean, delay_ms: Long = domain_skeleton_delay_ms): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) {
            shown = false
            return@LaunchedEffect
        }
        delay(delay_ms)
        shown = true
    }
    return active && shown
}

@Composable
internal fun <T> domain_tween(duration: Int = AsterDuration.short_4): FiniteAnimationSpec<T> =
    if (aster_reduce_motion()) snap() else tween(duration, easing = AsterEasing.standard_decelerate)

@Composable
internal fun domain_enter(): EnterTransition {
    if (aster_reduce_motion()) return EnterTransition.None
    return fadeIn(tween(AsterDuration.short_4, easing = AsterEasing.standard_decelerate)) +
        expandVertically(
            animationSpec = tween<IntSize>(AsterDuration.medium_1, easing = AsterEasing.emphasized_decelerate),
        )
}

@Composable
internal fun domain_exit(): ExitTransition {
    if (aster_reduce_motion()) return ExitTransition.None
    return fadeOut(tween(AsterDuration.short_3, easing = AsterEasing.standard_accelerate)) +
        shrinkVertically(animationSpec = tween<IntSize>(AsterDuration.short_4, easing = AsterEasing.standard_accelerate))
}

@Composable
internal fun domain_reveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = remember { MutableTransitionState(visible) }
    transition.targetState = visible
    AnimatedVisibility(
        visibleState = transition,
        modifier = modifier,
        enter = domain_enter(),
        exit = domain_exit(),
    ) {
        content()
    }
}
