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
package org.astermail.android.ui.mail

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

internal enum class LogoTone { dark, light, none }

private const val logo_tone_sample_size = 16
private const val logo_opaque_coverage = 0.9f
private const val logo_dark_luminance = 0.13
private const val logo_light_luminance = 0.5
private const val logo_mixed_share = 0.25
private const val logo_tone_cache_size = 512

internal val logo_light_backdrop = Color(0xFFF4F4F5)
internal val logo_dark_backdrop = Color(0xFF3F3F46)
internal const val logo_backdrop_inset = 0.1f

private fun linear_channel(value: Int): Double {
    val srgb = value / 255.0
    return if (srgb <= 0.03928) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
}

internal fun classify_logo_pixels(pixels: IntArray): LogoTone {
    var alpha_sum = 0.0
    var luminance_sum = 0.0
    var dark_weight = 0.0
    var light_weight = 0.0
    for (pixel in pixels) {
        val alpha = ((pixel ushr 24) and 0xFF) / 255.0
        if (alpha == 0.0) continue
        val luminance = 0.2126 * linear_channel((pixel shr 16) and 0xFF) +
            0.7152 * linear_channel((pixel shr 8) and 0xFF) +
            0.0722 * linear_channel(pixel and 0xFF)
        alpha_sum += alpha
        luminance_sum += alpha * luminance
        if (luminance < logo_dark_luminance) dark_weight += alpha
        if (luminance > logo_light_luminance) light_weight += alpha
    }
    if (pixels.isEmpty() || alpha_sum == 0.0) return LogoTone.none
    if (alpha_sum / pixels.size >= logo_opaque_coverage) return LogoTone.none
    if (dark_weight / alpha_sum >= logo_mixed_share && light_weight / alpha_sum >= logo_mixed_share) {
        return LogoTone.none
    }
    val mean_luminance = luminance_sum / alpha_sum
    return when {
        mean_luminance < logo_dark_luminance -> LogoTone.dark
        mean_luminance > logo_light_luminance -> LogoTone.light
        else -> LogoTone.none
    }
}

internal fun logo_contrast_backdrop(tone: LogoTone?, dark_theme: Boolean): Color? = when {
    tone == LogoTone.dark && dark_theme -> logo_light_backdrop
    tone == LogoTone.light && !dark_theme -> logo_dark_backdrop
    else -> null
}

internal fun sender_logo_backdrop(tone: LogoTone?, dark_theme: Boolean): Color =
    logo_contrast_backdrop(tone, dark_theme)
        ?: if (tone == null || tone == LogoTone.dark) Color.White else Color.Transparent

private val logo_tones = object : LinkedHashMap<String, LogoTone?>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LogoTone?>?): Boolean =
        size > logo_tone_cache_size
}

private fun cached_logo_tone(key: String): Pair<Boolean, LogoTone?> = synchronized(logo_tones) {
    logo_tones.containsKey(key) to logo_tones[key]
}

private fun sample_logo_pixels(drawable: Drawable): IntArray? = runCatching {
    val source = (drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
    if (source.width <= 0 || source.height <= 0) return@runCatching null
    val readable = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    val scaled = Bitmap.createScaledBitmap(readable, logo_tone_sample_size, logo_tone_sample_size, true)
    val pixels = IntArray(logo_tone_sample_size * logo_tone_sample_size)
    scaled.getPixels(pixels, 0, logo_tone_sample_size, 0, 0, logo_tone_sample_size, logo_tone_sample_size)
    pixels
}.getOrNull()

private fun measure_logo_tone(key: String, drawable: Drawable): LogoTone? {
    val (known, tone) = cached_logo_tone(key)
    if (known) return tone
    val measured = sample_logo_pixels(drawable)?.let(::classify_logo_pixels)
    synchronized(logo_tones) { logo_tones[key] = measured }
    return measured
}

@Composable
internal fun remember_logo_tone(key: String?, drawable: Drawable?): LogoTone? {
    val tone by produceState(
        initialValue = key?.let { cached_logo_tone(it).second },
        key,
        drawable,
    ) {
        if (key == null) {
            value = null
            return@produceState
        }
        val (known, cached) = cached_logo_tone(key)
        value = when {
            known -> cached
            drawable != null -> withContext(Dispatchers.Default) { measure_logo_tone(key, drawable) }
            else -> null
        }
    }
    return tone
}
