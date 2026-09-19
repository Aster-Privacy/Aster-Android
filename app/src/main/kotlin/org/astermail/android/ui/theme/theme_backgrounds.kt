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

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import org.astermail.android.R
import org.astermail.android.design.ColorThemeId

data class ThemeBackground(
    val id: String,
    val drawable_res: Int,
    val label_res: Int,
    val color_theme: ColorThemeId,
)

const val no_theme_background = "none"

val theme_backgrounds = listOf(
    ThemeBackground("pillars", R.drawable.theme_bg_pillars, R.string.image_theme_pillars, ColorThemeId.amber),
    ThemeBackground("aurora", R.drawable.theme_bg_aurora, R.string.image_theme_aurora, ColorThemeId.emerald),
    ThemeBackground("sunrise", R.drawable.theme_bg_sunrise, R.string.image_theme_sunrise, ColorThemeId.aster_blue),
    ThemeBackground("city_lights", R.drawable.theme_bg_city_lights, R.string.image_theme_city_lights, ColorThemeId.slate),
    ThemeBackground("northern_lights", R.drawable.theme_bg_northern_lights, R.string.image_theme_northern_lights, ColorThemeId.orange),
)

fun theme_background_for(id: String?): ThemeBackground? = theme_backgrounds.firstOrNull { it.id == id }

@Composable
fun theme_background_bitmap(): ImageBitmap? {
    val background = theme_background_for(local_background_image.current) ?: return null
    return ImageBitmap.imageResource(background.drawable_res)
}

fun DrawScope.draw_theme_background_at(
    bitmap: ImageBitmap,
    area: Size,
    top_in_area: Float = 0f,
    alpha: Float = 1f,
) {
    if (area.width <= 0f || area.height <= 0f) return
    val scale = maxOf(area.width / bitmap.width, area.height / bitmap.height)
    val src_w = (area.width / scale).roundToInt().coerceIn(1, bitmap.width)
    val src_h = (area.height / scale).roundToInt().coerceIn(1, bitmap.height)
    val src_x = (bitmap.width - src_w) / 2
    val src_y = (bitmap.height - src_h) / 2
    clipRect(0f, 0f, size.width, size.height) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(src_x, src_y),
            srcSize = IntSize(src_w, src_h),
            dstOffset = IntOffset(0, (-top_in_area).roundToInt()),
            dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
            alpha = alpha,
        )
    }
}

private val theme_backdrop_scrim = listOf(
    0f to Color.Black.copy(alpha = 0.5f),
    0.22f to Color.Black.copy(alpha = 0.3f),
    0.7f to Color.Black.copy(alpha = 0.34f),
    1f to Color.Black.copy(alpha = 0.55f),
)

fun DrawScope.draw_theme_backdrop(
    bitmap: ImageBitmap,
    area: Size,
    top_in_area: Float = 0f,
    alpha: Float = 1f,
) {
    if (area.width <= 0f || area.height <= 0f) return
    draw_theme_background_at(bitmap, area, top_in_area, alpha)
    clipRect(0f, 0f, size.width, size.height) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = theme_backdrop_scrim.toTypedArray(),
                startY = -top_in_area,
                endY = area.height - top_in_area,
            ),
            topLeft = Offset.Zero,
            size = size,
            alpha = alpha,
        )
    }
}
