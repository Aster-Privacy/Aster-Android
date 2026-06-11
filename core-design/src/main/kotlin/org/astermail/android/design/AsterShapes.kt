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

package org.astermail.android.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class SquircleShape(
    private val radius: Dp = 18.dp,
    private val smoothing: Float = 0f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r_px = with(density) { radius.toPx() }
        val max_r = minOf(size.width, size.height) / 2f
        val r = r_px.coerceAtMost(max_r)
        val s = smoothing.coerceIn(0f, 1f)
        val s_cap = if (r > 0f) ((max_r / r) - 1f).coerceAtLeast(0f) else 0f
        val effective_s = s.coerceAtMost(s_cap * 0.7f)
        val edge = (r * (1f + effective_s)).coerceAtMost(max_r)
        val control = edge * 0.5f
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(edge, 0f)
            lineTo(w - edge, 0f)
            cubicTo(w - control, 0f, w, control, w, edge)
            lineTo(w, h - edge)
            cubicTo(w, h - control, w - control, h, w - edge, h)
            lineTo(edge, h)
            cubicTo(control, h, 0f, h - control, 0f, h - edge)
            lineTo(0f, edge)
            cubicTo(0f, control, control, 0f, edge, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

object AsterShapes {
    val squircle_chip = SquircleShape(8.dp)
    val squircle_icon_btn = SquircleShape(10.dp)
    val squircle_input = SquircleShape(12.dp)
    val squircle_btn_sm = SquircleShape(12.dp)
    val squircle_btn_md = SquircleShape(14.dp)
    val squircle_card = SquircleShape(14.dp)
    val squircle_btn_lg = SquircleShape(16.dp)
    val squircle_btn_xl = SquircleShape(18.dp)
    val squircle_modal = SquircleShape(18.dp)
    val squircle_pill = SquircleShape(999.dp)

    val squircle_xs = SquircleShape(8.dp)
    val squircle_sm = SquircleShape(12.dp)
    val squircle_md = SquircleShape(14.dp)
    val squircle_lg = SquircleShape(16.dp)
    val squircle_xl = SquircleShape(18.dp)
}
