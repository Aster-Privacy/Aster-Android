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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import org.astermail.android.R
import org.astermail.android.design.ColorThemeId

enum class ThemeCategory(val label_res: Int) {
    space(R.string.image_theme_category_space),
    planets(R.string.image_theme_category_planets),
    night_sky(R.string.image_theme_category_night_sky),
    cities(R.string.image_theme_category_cities),
    landscapes(R.string.image_theme_category_landscapes),
    water(R.string.image_theme_category_water),
    yours(R.string.image_theme_category_yours),
}

data class ThemeBackground(
    val id: String,
    val drawable_res: Int,
    val category: ThemeCategory,
    val color_theme: ColorThemeId,
    val tint: Color,
    val credit: String,
    val custom_version: Long = 0L,
) {
    val is_custom: Boolean get() = custom_version > 0L
    val cache_key: String get() = if (is_custom) "custom:$custom_version" else drawable_res.toString()
}

const val no_theme_background = "none"

val theme_categories: List<Pair<ThemeCategory, List<ThemeBackground>>> by lazy {
    ThemeCategory.entries.map { category -> category to theme_backgrounds.filter { it.category == category } }
        .filter { it.second.isNotEmpty() }
}

fun custom_theme_background_entry(meta: CustomThemeImageMeta): ThemeBackground = ThemeBackground(
    id = custom_theme_background,
    drawable_res = 0,
    category = ThemeCategory.yours,
    color_theme = meta.accent,
    tint = meta.tint,
    credit = "",
    custom_version = meta.version,
)

fun theme_background_for(id: String?): ThemeBackground? {
    if (id == custom_theme_background) return custom_theme_image.meta.value?.let(::custom_theme_background_entry)
    return theme_backgrounds.firstOrNull { it.id == id }
}

@Composable
fun theme_background_bitmap(): ImageBitmap? {
    val custom_meta by custom_theme_image.meta.collectAsState()
    val id = local_background_image.current
    val background = remember(id, custom_meta) { theme_background_for(id) } ?: return null
    val context = LocalContext.current.applicationContext
    return remember(background.cache_key) { decode_theme_bitmap(context, background, 1, false) }
}

private val theme_bitmap_cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private val theme_thumbnail_cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 2
}

private fun decode_source(context: Context, background: ThemeBackground, options: BitmapFactory.Options): Bitmap? =
    if (background.is_custom) {
        custom_theme_image.load(context, options.inSampleSize)
    } else {
        BitmapFactory.decodeResource(context.resources, background.drawable_res, options)
    }

private fun decode_theme_bitmap(context: Context, background: ThemeBackground, sample: Int, soften: Boolean): ImageBitmap? {
    val key = "${background.cache_key}:$sample:$soften"
    theme_bitmap_cache.get(key)?.let { return it }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = decode_source(context, background, options) ?: return null
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

private fun decode_theme_thumbnail(context: Context, background: ThemeBackground): ImageBitmap? {
    theme_thumbnail_cache.get(background.cache_key)?.let { return it }
    val options = BitmapFactory.Options().apply {
        inSampleSize = 4
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val decoded = decode_source(context, background, options) ?: return null
    return decoded.asImageBitmap().also { theme_thumbnail_cache.put(background.cache_key, it) }
}

fun trim_theme_caches() {
    theme_thumbnail_cache.evictAll()
    theme_bitmap_cache.trimToSize(12 * 1024 * 1024)
}

suspend fun preload_theme_bitmap(context: Context, background: ThemeBackground) {
    withContext(Dispatchers.IO) { decode_theme_bitmap(context.applicationContext, background, 1, false) }
}

@Composable
fun remember_theme_bitmap(background: ThemeBackground, sample: Int = 1, soften: Boolean = false): State<ImageBitmap?> {
    val context = LocalContext.current.applicationContext
    val key = background.cache_key
    return produceState(initialValue = theme_bitmap_cache.get("$key:$sample:$soften"), key, sample, soften) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { decode_theme_bitmap(context, background, sample, soften) }
        }
    }
}

@Composable
fun remember_theme_thumbnail(background: ThemeBackground): State<ImageBitmap?> {
    val context = LocalContext.current.applicationContext
    val key = background.cache_key
    return produceState(initialValue = theme_thumbnail_cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { decode_theme_thumbnail(context, background) }
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
