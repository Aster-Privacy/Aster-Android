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

package org.astermail.android.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.util.lerp
import org.astermail.android.design.aster_reduce_motion

private const val morph_grid = 24f

const val menu_back_morph_ms = 320

private var menu_back_return_armed = false

fun arm_menu_back_return_morph() {
    menu_back_return_armed = true
}

@Composable
fun menu_back_return_morph_icon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val reduce_motion = aster_reduce_motion()
    val morph = remember { Animatable(if (menu_back_return_armed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (morph.value > 0f) {
            if (reduce_motion) {
                morph.snapTo(0f)
            } else {
                morph.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = menu_back_morph_ms,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
        menu_back_return_armed = false
    }
    menu_back_morph_icon(progress = morph.value, tint = tint, modifier = modifier)
}

@Composable
fun menu_back_morph_icon(
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val unit = size.minDimension / morph_grid
        val stroke = unit * 1.9f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun arm(
            from_start_x: Float,
            from_start_y: Float,
            from_end_x: Float,
            from_end_y: Float,
            to_start_x: Float,
            to_start_y: Float,
            to_end_x: Float,
            to_end_y: Float,
        ) {
            drawLine(
                color = tint,
                start = point(
                    lerp(from_start_x, to_start_x, p),
                    lerp(from_start_y, to_start_y, p),
                ),
                end = point(
                    lerp(from_end_x, to_end_x, p),
                    lerp(from_end_y, to_end_y, p),
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        arm(4f, 6f, 20f, 6f, 4f, 12f, 11f, 5f)
        arm(4f, 12f, 20f, 12f, 4f, 12f, 20f, 12f)
        arm(4f, 18f, 20f, 18f, 4f, 12f, 11f, 19f)
    }
}
