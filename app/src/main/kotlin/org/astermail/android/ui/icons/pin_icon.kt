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
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val pin_icon: ImageVector by lazy { build_pin_icon(filled = false) }

val pin_icon_filled: ImageVector by lazy { build_pin_icon(filled = true) }

private fun build_pin_icon(filled: Boolean): ImageVector =
    ImageVector.Builder(
        name = if (filled) "pin_filled" else "pin",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pin_path(stroke_width = if (filled) 3f else 2f) {
            moveTo(9.4f, 3f)
            horizontalLineToRelative(5.2f)
        }
        pin_path(
            stroke_width = if (filled) 1.5f else 2f,
            fill = filled,
        ) {
            moveTo(10.6f, 3.4f)
            verticalLineToRelative(4.1f)
            arcToRelative(3.1f, 3.1f, 0f, false, true, -1.16f, 2.42f)
            lineToRelative(-1.9f, 1.53f)
            curveToRelative(-0.62f, 0.5f, -0.27f, 1.55f, 0.53f, 1.55f)
            horizontalLineToRelative(8.06f)
            curveToRelative(0.8f, 0f, 1.15f, -1.05f, 0.53f, -1.55f)
            lineToRelative(-1.9f, -1.53f)
            arcTo(3.1f, 3.1f, 0f, false, true, 13.4f, 7.5f)
            verticalLineTo(3.4f)
            close()
        }
        pin_path(stroke_width = if (filled) 2.6f else 2f) {
            moveTo(12f, 13f)
            verticalLineToRelative(7.4f)
        }
    }.build()

private fun ImageVector.Builder.pin_path(
    stroke_width: Float,
    fill: Boolean = false,
    block: PathBuilder.() -> Unit,
) {
    path(
        fill = if (fill) SolidColor(Color.Black) else null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = stroke_width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}
