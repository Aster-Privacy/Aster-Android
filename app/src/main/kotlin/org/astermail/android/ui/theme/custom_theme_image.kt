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
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.ui.graphics.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.design.ColorThemeId

const val custom_theme_background = "custom"

data class CustomThemeImageMeta(
    val version: Long,
    val tint: Color,
    val accent: ColorThemeId,
)

enum class CustomThemeImageError { too_large, unsupported, unreadable }

class CustomThemeImageException(val reason: CustomThemeImageError) : Exception(reason.name)

internal fun theme_image_sample_size(width: Int, height: Int, target_w: Int, target_h: Int, max_pixels: Long): Int {
    if (width <= 0 || height <= 0 || target_w <= 0 || target_h <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= target_w && height / (sample * 2) >= target_h) sample *= 2
    while (sample < 1024 && (width.toLong() / sample) * (height.toLong() / sample) > max_pixels) sample *= 2
    return sample
}

internal fun theme_image_cap_factor(width: Int, height: Int, target_w: Int, target_h: Int, max_pixels: Long): Float {
    if (width <= 0 || height <= 0) return 1f
    val cover = max(target_w.toFloat() / width, target_h.toFloat() / height)
    val budget = kotlin.math.sqrt(max_pixels.toDouble() / (width.toDouble() * height)).toFloat()
    return min(min(1f, cover * 2f), budget).coerceAtLeast(0f)
}

object custom_theme_image {
    private const val key_alias = "aster_theme_image_v1"
    private const val keystore = "AndroidKeyStore"
    private const val transformation = "AES/GCM/NoPadding"
    private const val iv_bytes = 12
    private const val tag_bits = 128
    private const val max_input_bytes = 40 * 1024 * 1024
    private const val max_source_pixels = 120_000_000L
    private const val max_source_side = 20_000
    private const val target_width = 1080
    private const val target_height = 2400
    private const val max_decode_pixels = 8_000_000L
    private const val max_working_pixels = 8_000_000L
    private const val prefs_name = "theme_custom_image"
    private val allowed_mime = setOf(
        "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/avif",
    )
    private val accent_choices = listOf(
        ColorThemeId.purple, ColorThemeId.green, ColorThemeId.rose, ColorThemeId.orange,
        ColorThemeId.teal, ColorThemeId.indigo, ColorThemeId.amber, ColorThemeId.cyan,
        ColorThemeId.aster_blue, ColorThemeId.lime, ColorThemeId.fuchsia, ColorThemeId.emerald,
        ColorThemeId.pink,
    )

    private val meta_state = MutableStateFlow<CustomThemeImageMeta?>(null)
    val meta: StateFlow<CustomThemeImageMeta?> = meta_state.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
            val version = prefs.getLong("version", 0L)
            val stored = image_file(app)
            meta_state.value = if (version > 0L && stored.isFile) {
                CustomThemeImageMeta(
                    version = version,
                    tint = Color(prefs.getInt("tint", 0xFF111111.toInt())),
                    accent = ColorThemeId.from_key(prefs.getString("accent", null) ?: ColorThemeId.aster_blue.name),
                )
            } else {
                if (version > 0L || stored.exists() || source_file(app).exists()) runCatching { discard(app) }
                null
            }
            initialized = true
        }
    }

    private fun image_file(context: Context): File =
        File(File(context.noBackupFilesDir, "theme"), "custom_background.bin")

    private fun source_file(context: Context): File =
        File(File(context.noBackupFilesDir, "theme"), "custom_source.bin")

    val frame_aspect: Float get() = target_width.toFloat() / target_height

    suspend fun import_source(context: Context, uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val app = context.applicationContext
            val bytes = read_capped(app, uri)
            cap_source(decode_validated(bytes))
        }.recoverCatching { error ->
            throw if (error is CustomThemeImageException) error else CustomThemeImageException(CustomThemeImageError.unreadable)
        }
    }

    suspend fun load_source(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val file = source_file(app).takeIf { it.isFile } ?: image_file(app).takeIf { it.isFile } ?: return@withContext null
        val plain = runCatching { decrypt(file.readBytes()) }.getOrNull() ?: return@withContext null
        BitmapFactory.decodeByteArray(
            plain,
            0,
            plain.size,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ).also { plain.fill(0) }
    }

    fun stored_crop(context: Context): RectF? {
        val prefs = context.applicationContext.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
        if (!prefs.contains("crop_left") || !source_file(context.applicationContext).isFile) return null
        val rect = RectF(
            prefs.getFloat("crop_left", 0f),
            prefs.getFloat("crop_top", 0f),
            prefs.getFloat("crop_right", 1f),
            prefs.getFloat("crop_bottom", 1f),
        )
        return rect.takeIf { it.width() > 0f && it.height() > 0f }
    }

    suspend fun commit(
        context: Context,
        source: Bitmap,
        crop: RectF,
        source_changed: Boolean,
    ): Result<CustomThemeImageMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val app = context.applicationContext
            val safe = RectF(
                crop.left.coerceIn(0f, 1f),
                crop.top.coerceIn(0f, 1f),
                crop.right.coerceIn(0f, 1f),
                crop.bottom.coerceIn(0f, 1f),
            )
            if (safe.width() <= 0f || safe.height() <= 0f) throw CustomThemeImageException(CustomThemeImageError.unreadable)
            if (source_changed || !source_file(app).isFile) {
                write_encrypted(app, encode(source), source_file(app))
            }
            val framed = render_crop(source, safe)
            val (tint, accent) = analyze(framed)
            val encoded = encode(framed)
            framed.recycle()
            write_encrypted(app, encoded, image_file(app))
            evict_custom_theme_bitmaps()
            val next = CustomThemeImageMeta(
                version = max(System.currentTimeMillis(), (meta_state.value?.version ?: 0L) + 1L),
                tint = tint,
                accent = accent,
            )
            app.getSharedPreferences(prefs_name, Context.MODE_PRIVATE).edit()
                .putLong("version", next.version)
                .putInt("tint", argb_of(tint))
                .putString("accent", accent.name)
                .putFloat("crop_left", safe.left)
                .putFloat("crop_top", safe.top)
                .putFloat("crop_right", safe.right)
                .putFloat("crop_bottom", safe.bottom)
                .commit()
            meta_state.value = next
            next
        }.recoverCatching { error ->
            throw if (error is CustomThemeImageException) error else CustomThemeImageException(CustomThemeImageError.unreadable)
        }
    }

    fun delete(context: Context) {
        discard(context.applicationContext)
        meta_state.value = null
    }

    private fun discard(app: Context) {
        image_file(app).delete()
        source_file(app).delete()
        File(app.noBackupFilesDir, "theme").listFiles()?.forEach { it.delete() }
        app.getSharedPreferences(prefs_name, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching { KeyStore.getInstance(keystore).apply { load(null) }.deleteEntry(key_alias) }
        evict_custom_theme_bitmaps()
    }

    private fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream(512 * 1024).use { out ->
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bitmap.compress(format, 90, out)
        out.toByteArray()
    }

    private fun cap_source(source: Bitmap): Bitmap {
        val factor = theme_image_cap_factor(source.width, source.height, target_width, target_height, max_working_pixels)
        if (factor >= 1f) return source
        val w = (source.width * factor).roundToInt().coerceAtLeast(1)
        val h = (source.height * factor).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    private fun render_crop(source: Bitmap, crop: RectF): Bitmap {
        val src_left = crop.left * source.width
        val src_top = crop.top * source.height
        val src_w = crop.width() * source.width
        val src_h = crop.height() * source.height
        val scale = max(target_width / src_w, target_height / src_h)
        val dx = (target_width - src_w * scale) / 2f
        val dy = (target_height - src_h * scale) / 2f
        val out = Bitmap.createBitmap(target_width, target_height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            source,
            Matrix().apply {
                postTranslate(-src_left, -src_top)
                postScale(scale, scale)
                postTranslate(dx, dy)
            },
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    fun load(context: Context, sample: Int): Bitmap? {
        val app = context.applicationContext
        val file = image_file(app)
        if (!file.isFile) {
            if (meta_state.value != null) delete(app)
            return null
        }
        val plain = runCatching { decrypt(file.readBytes()) }.getOrNull() ?: run {
            delete(app)
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            if (sample > 1) inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(plain, 0, plain.size, options).also { plain.fill(0) }
        if (decoded == null) delete(app)
        return decoded
    }

    private fun read_capped(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val declared = resolver.getType(uri)?.lowercase()
        if (declared != null && declared !in allowed_mime) throw CustomThemeImageException(CustomThemeImageError.unsupported)
        val stream = resolver.openInputStream(uri) ?: throw CustomThemeImageException(CustomThemeImageError.unreadable)
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > max_input_bytes) throw CustomThemeImageException(CustomThemeImageError.too_large)
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun decode_validated(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val mime = bounds.outMimeType?.lowercase()
        if (mime == null || mime !in allowed_mime) throw CustomThemeImageException(CustomThemeImageError.unsupported)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw CustomThemeImageException(CustomThemeImageError.unreadable)
        if (width > max_source_side || height > max_source_side || width.toLong() * height > max_source_pixels) {
            throw CustomThemeImageException(CustomThemeImageError.too_large)
        }
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        val (upright_w, upright_h) = if (rotation % 180 == 0) width to height else height to width
        val sample = theme_image_sample_size(upright_w, upright_h, target_width, target_height, max_decode_pixels)
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: throw CustomThemeImageException(CustomThemeImageError.unreadable)
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true,
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun analyze(bitmap: Bitmap): Pair<Color, ColorThemeId> {
        val small = Bitmap.createScaledBitmap(bitmap, 54, 120, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== bitmap) small.recycle()
        var r = 0L
        var g = 0L
        var b = 0L
        var x = 0.0
        var y = 0.0
        var vivid = 0
        val hsv = FloatArray(3)
        for (p in pixels) {
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            r += pr
            g += pg
            b += pb
            android.graphics.Color.RGBToHSV(pr, pg, pb, hsv)
            if (hsv[1] > 0.3f && hsv[2] > 0.25f) {
                val weight = (hsv[1] * hsv[2]).toDouble()
                val angle = Math.toRadians(hsv[0].toDouble())
                x += cos(angle) * weight
                y += sin(angle) * weight
                vivid++
            }
        }
        val n = pixels.size.toFloat()
        val peak = max(max(r, g), max(b, 1L)).toFloat()
        val tint = Color(
            red = min(1f, r / peak * 0x22 / 255f),
            green = min(1f, g / peak * 0x22 / 255f),
            blue = min(1f, b / peak * 0x22 / 255f),
        )
        if (vivid < n * 0.03f) return tint to ColorThemeId.aster_blue
        val hue = ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
        val accent = accent_choices.minByOrNull { id ->
            val color = AsterColorThemes.palette_for(id)?.accent_color ?: return@minByOrNull Float.MAX_VALUE
            val c = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                (color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt(), c,
            )
            val d = kotlin.math.abs(c[0] - hue)
            min(d, 360f - d)
        } ?: ColorThemeId.aster_blue
        return tint to accent
    }

    private fun argb_of(color: Color): Int {
        val a = (color.alpha * 255).roundToInt()
        val r = (color.red * 255).roundToInt()
        val g = (color.green * 255).roundToInt()
        val b = (color.blue * 255).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun secret_key(): SecretKey {
        val store = KeyStore.getInstance(keystore).apply { load(null) }
        (store.getKey(key_alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keystore)
        generator.init(
            KeyGenParameterSpec.Builder(key_alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun write_encrypted(context: Context, plain: ByteArray, target: File) {
        val cipher = Cipher.getInstance(transformation).apply { init(Cipher.ENCRYPT_MODE, secret_key()) }
        val sealed = cipher.iv + cipher.doFinal(plain)
        plain.fill(0)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.nameWithoutExtension}.tmp")
        temp.writeBytes(sealed)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw CustomThemeImageException(CustomThemeImageError.unreadable)
            }
        }
    }

    private fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > iv_bytes)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, secret_key(), GCMParameterSpec(tag_bits, sealed, 0, iv_bytes))
        return cipher.doFinal(sealed, iv_bytes, sealed.size - iv_bytes)
    }
}
