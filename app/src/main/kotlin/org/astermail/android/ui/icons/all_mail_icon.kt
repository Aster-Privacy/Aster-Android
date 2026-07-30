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
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val all_mail_icon: ImageVector by lazy {
    ImageVector.Builder(
        name = "all_mail",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        stroked_path {
            moveTo(7f, 3f)
            lineTo(17f, 3f)
        }
        stroked_path {
            moveTo(5f, 6.5f)
            lineTo(19f, 6.5f)
        }
        stroked_path {
            moveTo(5f, 10f)
            lineTo(19f, 10f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            lineTo(21f, 19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            lineTo(5f, 21f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            lineTo(3f, 12f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
        stroked_path {
            moveTo(3.5f, 11f)
            lineTo(12f, 16.5f)
            lineTo(20.5f, 11f)
        }
    }.build()
}

private fun ImageVector.Builder.stroked_path(
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}
