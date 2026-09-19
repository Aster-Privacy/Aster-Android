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

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val tint: Color,
    val credit: String,
)

const val no_theme_background = "none"

val theme_backgrounds = listOf(
    ThemeBackground("matterhorn", R.drawable.theme_bg_matterhorn, R.string.image_theme_matterhorn, ColorThemeId.teal, Color(0xFF081A20), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("aurora", R.drawable.theme_bg_aurora, R.string.image_theme_aurora, ColorThemeId.emerald, Color(0xFF081A18), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("fjord", R.drawable.theme_bg_fjord, R.string.image_theme_fjord, ColorThemeId.aster_blue, Color(0xFF0A1428), "W.carter, CC0"),
    ThemeBackground("desert", R.drawable.theme_bg_desert, R.string.image_theme_desert, ColorThemeId.amber, Color(0xFF120F1C), "Sergey Pesterev, CC BY-SA 4.0"),
    ThemeBackground("milky_way", R.drawable.theme_bg_milky_way, R.string.image_theme_milky_way, ColorThemeId.indigo, Color(0xFF10121E), "Anil Öztas, CC BY 4.0"),
    ThemeBackground("forest", R.drawable.theme_bg_forest, R.string.image_theme_forest, ColorThemeId.green, Color(0xFF0C1414), "Giles Laurent, CC BY-SA 4.0"),
)

fun theme_background_for(id: String?): ThemeBackground? = theme_backgrounds.firstOrNull { it.id == id }

@Composable
fun theme_background_bitmap(): ImageBitmap? {
    val background = theme_background_for(local_background_image.current) ?: return null
    return ImageBitmap.imageResource(background.drawable_res)
}

private val theme_bitmap_cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun decode_theme_bitmap(resources: Resources, res: Int, sample: Int, soften: Boolean): ImageBitmap? {
    val key = "$res:$sample:$soften"
    theme_bitmap_cache.get(key)?.let { return it }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeResource(resources, res, options) ?: return null
    val result = if (soften) {
        val up = Bitmap.createScaledBitmap(decoded, decoded.width * 4, decoded.height * 4, true)
        Bitmap.createScaledBitmap(up, decoded.width * 2, decoded.height * 2, true).also {
            if (up !== it) up.recycle()
        }
    } else {
        decoded
    }
    return result.asImageBitmap().also { theme_bitmap_cache.put(key, it) }
}

@Composable
fun remember_theme_bitmap(res: Int, sample: Int = 1, soften: Boolean = false): State<ImageBitmap?> {
    val resources = LocalContext.current.resources
    return produceState(initialValue = theme_bitmap_cache.get("$res:$sample:$soften"), res, sample, soften) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { decode_theme_bitmap(resources, res, sample, soften) }
        }
    }
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
    0f to Color.Black.copy(alpha = 0.35f),
    0.2f to Color.Black.copy(alpha = 0.12f),
    0.65f to Color.Black.copy(alpha = 0.16f),
    1f to Color.Black.copy(alpha = 0.4f),
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
