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

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.astermail.android.design.aster_reduce_motion

private const val static_spinner_progress = 0.75f
private const val static_linear_progress = 0.4f
private val spinner_default_diameter = 40.dp

internal fun Modifier.square_spinner_bounds(): Modifier = layout { measurable, constraints ->
    val preferred = spinner_default_diameter.roundToPx()
    val width = if (constraints.hasBoundedWidth) preferred.coerceIn(constraints.minWidth, constraints.maxWidth) else preferred.coerceAtLeast(constraints.minWidth)
    val height = if (constraints.hasBoundedHeight) preferred.coerceIn(constraints.minHeight, constraints.maxHeight) else preferred.coerceAtLeast(constraints.minHeight)
    val side = minOf(width, height)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(width, height) {
        placeable.place((width - side) / 2, (height - side) / 2)
    }
}

@Composable
fun aster_spinner(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    stroke_width: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    track_color: Color = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
    stroke_cap: StrokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
) {
    if (aster_reduce_motion()) {
        CircularProgressIndicator(
            progress = { static_spinner_progress },
            modifier = modifier.square_spinner_bounds(),
            color = color,
            strokeWidth = stroke_width,
            trackColor = track_color,
            strokeCap = stroke_cap,
            gapSize = 0.dp,
        )
    } else {
        CircularProgressIndicator(
            modifier = modifier.square_spinner_bounds(),
            color = color,
            strokeWidth = stroke_width,
            trackColor = track_color,
            strokeCap = stroke_cap,
        )
    }
}

@Composable
fun aster_linear_progress(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    track_color: Color = ProgressIndicatorDefaults.linearTrackColor,
    stroke_cap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
) {
    if (aster_reduce_motion()) {
        LinearProgressIndicator(
            progress = { static_linear_progress },
            modifier = modifier,
            color = color,
            trackColor = track_color,
            strokeCap = stroke_cap,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = track_color,
            strokeCap = stroke_cap,
        )
    }
}
