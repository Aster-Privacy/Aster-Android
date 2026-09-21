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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.util.lerp

private const val morph_grid = 24f

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
