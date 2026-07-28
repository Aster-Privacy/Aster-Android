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

package org.astermail.android.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

class horizontal_rule_span(private val line_color: Int) : android.text.style.LineBackgroundSpan {
    override fun drawBackground(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val previous_color = paint.color
        val previous_width = paint.strokeWidth
        paint.color = line_color
        paint.strokeWidth = 2f
        val y = (top + bottom) / 2f
        canvas.drawLine(left.toFloat(), y, right.toFloat(), y, paint)
        paint.color = previous_color
        paint.strokeWidth = previous_width
    }
}

private fun ImageVector.Builder.filled_rect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    path(fill = SolidColor(Color.Black)) {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }
}

val numbered_list_icon: ImageVector by lazy {
    ImageVector.Builder(
        name = "numbered_list",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        filled_rect(9f, 4.4f, 20.5f, 6.1f)
        filled_rect(9f, 11.2f, 20.5f, 12.9f)
        filled_rect(9f, 18f, 20.5f, 19.7f)
        filled_rect(4.3f, 3.2f, 5.3f, 7.3f)
        filled_rect(3.3f, 3.2f, 4.3f, 4.2f)
        filled_rect(3.1f, 10.0f, 6.2f, 11.0f)
        filled_rect(5.2f, 10.0f, 6.2f, 12.2f)
        filled_rect(3.1f, 12.0f, 6.2f, 13.0f)
        filled_rect(3.1f, 12.6f, 4.1f, 14.1f)
        filled_rect(3.1f, 13.9f, 6.2f, 14.9f)
        filled_rect(3.1f, 16.8f, 6.2f, 17.8f)
        filled_rect(5.2f, 16.8f, 6.2f, 21.0f)
        filled_rect(3.6f, 18.4f, 6.2f, 19.4f)
        filled_rect(3.1f, 20.0f, 6.2f, 21.0f)
    }.build()
}
