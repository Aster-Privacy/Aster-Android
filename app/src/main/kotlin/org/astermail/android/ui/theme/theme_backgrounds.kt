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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.ColorThemeId

enum class ThemeCategory(val label_res: Int) {
    calm(R.string.image_theme_category_calm),
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
    val is_generated: Boolean get() = category == ThemeCategory.calm
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

private const val theme_source_width = 1080
private const val theme_source_height = 2400

private val theme_bitmap_cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private val theme_thumbnail_cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 2
}

private fun screen_sample(context: Context): Int {
    val metrics = context.resources.displayMetrics
    val short_side = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    val long_side = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    var sample = 1
    while (theme_source_width / (sample * 2) >= short_side && theme_source_height / (sample * 2) >= long_side) {
        sample *= 2
    }
    return sample
}

private fun screen_key(background: ThemeBackground, sample: Int): String = "${background.cache_key}:$sample"

private fun decode_source(context: Context, background: ThemeBackground, options: BitmapFactory.Options): Bitmap? =
    if (background.is_custom) {
        custom_theme_image.load(context, options.inSampleSize)
    } else {
        BitmapFactory.decodeResource(context.resources, background.drawable_res, options)
    }

private fun decode_screen_bitmap(context: Context, background: ThemeBackground): ImageBitmap? {
    val sample = screen_sample(context)
    val key = screen_key(background, sample)
    theme_bitmap_cache.get(key)?.let { return it }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = decode_source(context, background, options) ?: return null
    decoded.prepareToDraw()
    return decoded.asImageBitmap().also { theme_bitmap_cache.put(key, it) }
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

private val theme_blur_cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private const val theme_blur_sample_short_side = 30
private const val theme_blur_output_short_side = 320

private fun decode_theme_blur(context: Context, background: ThemeBackground): ImageBitmap? {
    theme_blur_cache.get(background.cache_key)?.let { return it }
    val options = BitmapFactory.Options().apply {
        inSampleSize = 8
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = decode_source(context, background, options) ?: return null
    val short_side = minOf(decoded.width, decoded.height).coerceAtLeast(1)
    val down = theme_blur_sample_short_side.toFloat() / short_side
    val small = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * down).roundToInt().coerceAtLeast(1),
        (decoded.height * down).roundToInt().coerceAtLeast(1),
        true,
    )
    if (small != decoded) decoded.recycle()
    val up = theme_blur_output_short_side.toFloat() / minOf(small.width, small.height).coerceAtLeast(1)
    val smooth = Bitmap.createScaledBitmap(
        small,
        (small.width * up).roundToInt().coerceAtLeast(1),
        (small.height * up).roundToInt().coerceAtLeast(1),
        true,
    )
    if (smooth != small) small.recycle()
    smooth.prepareToDraw()
    return smooth.asImageBitmap().also { theme_blur_cache.put(background.cache_key, it) }
}

@Composable
fun remember_active_theme_blur(): ImageBitmap? {
    val custom_meta by custom_theme_image.meta.collectAsState()
    val id = local_background_image.current
    val background = remember(id, custom_meta) { theme_background_for(id) }
    val context = LocalContext.current.applicationContext
    val state = produceState(
        initialValue = background?.let { theme_blur_cache.get(it.cache_key) },
        background?.cache_key,
    ) {
        val target = background
        value = if (target == null) {
            null
        } else {
            theme_blur_cache.get(target.cache_key)
                ?: withContext(Dispatchers.IO) { decode_theme_blur(context, target) }
        }
    }
    return state.value
}

fun DrawScope.draw_theme_window_slice(bitmap: ImageBitmap, window: Size, origin: androidx.compose.ui.geometry.Offset) {
    if (window.width <= 0f || window.height <= 0f) return
    if (size.width <= 0f || size.height <= 0f) return
    val scale = maxOf(window.width / bitmap.width, window.height / bitmap.height)
    val src_w = (window.width / scale).roundToInt().coerceIn(1, bitmap.width)
    val src_h = (window.height / scale).roundToInt().coerceIn(1, bitmap.height)
    val src_x = (bitmap.width - src_w) / 2
    val src_y = (bitmap.height - src_h) / 2
    clipRect(0f, 0f, size.width, size.height) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(src_x, src_y),
            srcSize = IntSize(src_w, src_h),
            dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
            dstSize = IntSize(window.width.roundToInt(), window.height.roundToInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
    }
}

fun evict_custom_theme_bitmaps() {
    theme_bitmap_cache.snapshot().keys.filter { it.startsWith("custom:") }.forEach { theme_bitmap_cache.remove(it) }
    theme_thumbnail_cache.snapshot().keys.filter { it.startsWith("custom:") }.forEach { theme_thumbnail_cache.remove(it) }
    theme_blur_cache.snapshot().keys.filter { it.startsWith("custom:") }.forEach { theme_blur_cache.remove(it) }
}

fun trim_theme_caches() {
    theme_thumbnail_cache.evictAll()
    theme_bitmap_cache.trimToSize(12 * 1024 * 1024)
}

suspend fun preload_theme_bitmap(context: Context, background: ThemeBackground) {
    withContext(Dispatchers.IO) { decode_screen_bitmap(context.applicationContext, background) }
}

@Composable
fun remember_active_theme_bitmap(): ImageBitmap? {
    val custom_meta by custom_theme_image.meta.collectAsState()
    val id = local_background_image.current
    val background = remember(id, custom_meta) { theme_background_for(id) }
    return remember_theme_bitmap(background)
}

@Composable
fun remember_theme_bitmap(background: ThemeBackground?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val sample = remember(context) { screen_sample(context) }
    val state = produceState(
        initialValue = background?.let { theme_bitmap_cache.get(screen_key(it, sample)) },
        background?.cache_key,
    ) {
        val target = background
        value = if (target == null) {
            null
        } else {
            theme_bitmap_cache.get(screen_key(target, sample))
                ?: withContext(Dispatchers.IO) { decode_screen_bitmap(context, target) }
        }
    }
    return state.value
}

@Composable
fun remember_theme_thumbnail(background: ThemeBackground): State<ImageBitmap?> {
    val context = LocalContext.current.applicationContext
    val key = background.cache_key
    return produceState(initialValue = theme_thumbnail_cache.get(key), key) {
        value = theme_thumbnail_cache.get(key)
            ?: withContext(Dispatchers.IO) { decode_theme_thumbnail(context, background) }
    }
}

fun DrawScope.draw_theme_background(bitmap: ImageBitmap) {
    val area: Size = size
    if (area.width <= 0f || area.height <= 0f) return
    val scale = maxOf(area.width / bitmap.width, area.height / bitmap.height)
    val src_w = (area.width / scale).roundToInt().coerceIn(1, bitmap.width)
    val src_h = (area.height / scale).roundToInt().coerceIn(1, bitmap.height)
    val src_x = (bitmap.width - src_w) / 2
    val src_y = (bitmap.height - src_h) / 2
    clipRect(0f, 0f, area.width, area.height) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(src_x, src_y),
            srcSize = IntSize(src_w, src_h),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
        )
    }
}
