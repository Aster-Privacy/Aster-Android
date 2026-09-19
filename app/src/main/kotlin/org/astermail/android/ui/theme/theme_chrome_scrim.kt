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

package org.astermail.android.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

const val chrome_scrim_top_alpha = 0.40f
const val chrome_scrim_mid_alpha = 0.26f
const val chrome_scrim_mid_stop = 0.30f
const val chrome_scrim_span_factor = 1.6f

private val theme_veil_stops = arrayOf(
    0f to 0.42f,
    0.22f to 0.24f,
    0.68f to 0.28f,
    1f to 0.46f,
)

fun DrawScope.draw_theme_veil(ink: Color) {
    if (size.width <= 0f || size.height <= 0f) return
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = theme_veil_stops.map { it.first to ink.copy(alpha = it.second) }.toTypedArray(),
            endY = size.height,
        ),
    )
}

fun DrawScope.draw_chrome_scrim(ink: Color, alpha: Float = 1f) {
    if (size.width <= 0f || size.height <= 0f || alpha <= 0f) return
    val span = size.height * chrome_scrim_span_factor
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to ink.copy(alpha = chrome_scrim_top_alpha),
                chrome_scrim_mid_stop to ink.copy(alpha = chrome_scrim_mid_alpha),
                1f to Color.Transparent,
            ),
            endY = span,
        ),
        size = Size(size.width, span),
        alpha = alpha,
    )
}
