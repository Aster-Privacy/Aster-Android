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

val alias_mask_icon: ImageVector by lazy {
    ImageVector.Builder(
        name = "alias_mask",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        alias_mask_path {
            moveTo(12f, 8.25f)
            curveTo(13.2f, 8.25f, 13.7f, 6.75f, 15f, 6.75f)
            horizontalLineTo(18f)
            curveTo(20.2f, 6.75f, 21.75f, 8.3f, 21.75f, 10.5f)
            curveTo(21.75f, 14.9f, 19.8f, 17.25f, 16.5f, 17.25f)
            curveTo(14.5f, 17.25f, 13.8f, 16.5f, 12f, 16.5f)
            curveTo(10.2f, 16.5f, 9.5f, 17.25f, 7.5f, 17.25f)
            curveTo(4.2f, 17.25f, 2.25f, 14.9f, 2.25f, 10.5f)
            curveTo(2.25f, 8.3f, 3.8f, 6.75f, 6f, 6.75f)
            horizontalLineTo(9f)
            curveTo(10.3f, 6.75f, 10.8f, 8.25f, 12f, 8.25f)
            close()
        }
        alias_mask_path {
            moveTo(5.25f, 13.5f)
            arcTo(2.4375f, 2.4375f, 0f, false, true, 9.75f, 13.5f)
        }
        alias_mask_path {
            moveTo(14.25f, 13.5f)
            arcTo(2.4375f, 2.4375f, 0f, false, true, 18.75f, 13.5f)
        }
    }.build()
}

private val alias_mask_tones_light = listOf(
    Color(0xFF2563EB),
    Color(0xFF0F766E),
    Color(0xFFB45309),
    Color(0xFFBE185D),
    Color(0xFF7C3AED),
)

private val alias_mask_tones_dark = listOf(
    Color(0xFF60A5FA),
    Color(0xFF2DD4BF),
    Color(0xFFFBBF24),
    Color(0xFFF472B6),
    Color(0xFFA78BFA),
)

fun alias_mask_tone_index(address: String): Int {
    val key = address.trim().lowercase()
    var hash = 2166136261L.toInt()
    for (c in key) {
        hash = (hash xor c.code) * 16777619
    }
    return ((hash.toLong() and 0xFFFFFFFFL) % alias_mask_tones_light.size).toInt()
}

fun alias_mask_tone(address: String, is_dark: Boolean): Color {
    val tones = if (is_dark) alias_mask_tones_dark else alias_mask_tones_light
    return tones[alias_mask_tone_index(address)]
}

private fun ImageVector.Builder.alias_mask_path(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}
