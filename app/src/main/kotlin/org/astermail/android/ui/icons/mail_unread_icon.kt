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
import compose.icons.TablerIcons
import compose.icons.tablericons.MailOpened

val mail_unread_icon: ImageVector by lazy {
    ImageVector.Builder(
        name = "mail_unread",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        stroked_path {
            moveTo(13f, 5f)
            horizontalLineToRelative(-8f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
            verticalLineToRelative(10f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
            horizontalLineToRelative(14f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
            verticalLineToRelative(-6f)
        }
        stroked_path {
            moveTo(3f, 7f)
            lineToRelative(9f, 6f)
            lineToRelative(4f, -2.667f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 5f)
            arcToRelative(3f, 3f, 0f, true, false, 6f, 0f)
            arcToRelative(3f, 3f, 0f, true, false, -6f, 0f)
            close()
        }
    }.build()
}

val mark_read_icon: ImageVector
    get() = TablerIcons.MailOpened

fun read_toggle_icon(is_read: Boolean): ImageVector =
    if (is_read) mail_unread_icon else mark_read_icon

private fun ImageVector.Builder.stroked_path(block: PathBuilder.() -> Unit) {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}
