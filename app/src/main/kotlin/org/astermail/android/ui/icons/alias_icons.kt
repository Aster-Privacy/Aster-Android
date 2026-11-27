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

package org.astermail.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

internal val ALIAS_AT_PATHS = listOf(
    "M15.625 12A3.75 3.75 0 1 1 8.125 12A3.75 3.75 0 1 1 15.625 12Z",
    "M15.625 8.25V13.25A2.625 2.625 0 0 0 20.875 13.25V12A9 9 0 1 0 17.66 18.894",
)

internal val GHOST_MASK_PATHS = listOf(
    "M12 3.05C16.75 3.05 19.75 5.2 19.75 9.3C19.75 14.55 15.9 19.6 12 21.55C8.1 19.6 4.25 14.55 4.25 9.3C4.25 5.2 7.25 3.05 12 3.05Z",
    "M6.85 10.2C7.9 8.65 9.75 8.6 10.6 10.05",
    "M17.15 10.2C16.1 8.65 14.25 8.6 13.4 10.05",
    "M12 14.7C10.75 13.95 9.1 14.1 8.25 15.45C7.95 15.85 7.4 15.7 7.45 15.15",
    "M12 14.7C13.25 13.95 14.9 14.1 15.75 15.45C16.05 15.85 16.6 15.7 16.55 15.15",
    "M12 17.55V19.3",
)

private fun alias_line_icon(name: String, paths: List<String>): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach {
        builder.addPath(
            pathData = addPathNodes(it),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

val alias_at_icon: ImageVector by lazy { alias_line_icon("alias_at", ALIAS_AT_PATHS) }

val ghost_mask_icon: ImageVector by lazy { alias_line_icon("ghost_mask", GHOST_MASK_PATHS) }
