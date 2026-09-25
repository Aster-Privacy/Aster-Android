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

package org.astermail.android.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Base64

const val SIGNATURE_MAX_ENCRYPTED_BYTES = 65_536
const val SIGNATURE_GCM_TAG_BYTES = 16
const val SIGNATURE_MAX_PLAINTEXT_BYTES = SIGNATURE_MAX_ENCRYPTED_BYTES - SIGNATURE_GCM_TAG_BYTES
const val SIGNATURE_TARGET_BYTES = 48 * 1024

data class SignatureImageLevel(val max_dimension: Int, val jpeg_quality: Int)

class SignatureImage(val mime: String, val bytes: ByteArray)

fun interface SignatureImageEncoder {
    fun reencode(bytes: ByteArray, level: SignatureImageLevel): SignatureImage?
}

val SIGNATURE_IMAGE_LEVELS = listOf(
    SignatureImageLevel(600, 85),
    SignatureImageLevel(480, 80),
    SignatureImageLevel(400, 75),
    SignatureImageLevel(320, 70),
    SignatureImageLevel(240, 65),
    SignatureImageLevel(200, 60),
    SignatureImageLevel(160, 55),
    SignatureImageLevel(120, 50),
    SignatureImageLevel(96, 45),
    SignatureImageLevel(64, 40),
)

private val data_image_regex = Regex("""data:image/([A-Za-z0-9.+-]+);base64,([A-Za-z0-9+/=]+)""")

fun signature_byte_size(content: String): Int = content.toByteArray(Charsets.UTF_8).size

fun signature_fits(content: String): Boolean = signature_byte_size(content) <= SIGNATURE_MAX_PLAINTEXT_BYTES

fun fit_signature_content(
    content: String,
    encoder: SignatureImageEncoder,
    target_bytes: Int = SIGNATURE_TARGET_BYTES,
    max_bytes: Int = SIGNATURE_MAX_PLAINTEXT_BYTES,
): String? {
    if (signature_byte_size(content) <= target_bytes) return content
    val images = data_image_regex.findAll(content).toList()
    if (images.isEmpty()) return content.takeIf { signature_byte_size(it) <= max_bytes }
    val decoded = images.map { match ->
        runCatching { Base64.getDecoder().decode(match.groupValues[2]) }.getOrNull()
    }
    var best = content
    var best_size = signature_byte_size(content)
    for (level in SIGNATURE_IMAGE_LEVELS) {
        val rebuilt = StringBuilder(content.length)
        var cursor = 0
        images.forEachIndexed { index, match ->
            rebuilt.append(content, cursor, match.range.first)
            val original = decoded[index]
            val replacement = original
                ?.let { runCatching { encoder.reencode(it, level) }.getOrNull() }
                ?.let { "data:image/${it.mime};base64,${Base64.getEncoder().encodeToString(it.bytes)}" }
                ?.takeIf { it.length < match.value.length }
            rebuilt.append(replacement ?: match.value)
            cursor = match.range.last + 1
        }
        rebuilt.append(content, cursor, content.length)
        val candidate = rebuilt.toString()
        val size = signature_byte_size(candidate)
        if (size < best_size) {
            best = candidate
            best_size = size
        }
        if (size <= target_bytes) return candidate
    }
    return best.takeIf { best_size <= max_bytes }
}

object BitmapSignatureImageEncoder : SignatureImageEncoder {
    override fun reencode(bytes: ByteArray, level: SignatureImageLevel): SignatureImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= level.max_dimension) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        val scaled = if (longest > level.max_dimension) {
            val ratio = level.max_dimension.toFloat() / longest
            Bitmap.createScaledBitmap(
                decoded,
                maxOf(1, (decoded.width * ratio).toInt()),
                maxOf(1, (decoded.height * ratio).toInt()),
                true,
            )
        } else {
            decoded
        }
        try {
            val out = ByteArrayOutputStream()
            return if (scaled.hasAlpha()) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                SignatureImage("png", out.toByteArray())
            } else {
                scaled.compress(Bitmap.CompressFormat.JPEG, level.jpeg_quality, out)
                SignatureImage("jpeg", out.toByteArray())
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }
}
