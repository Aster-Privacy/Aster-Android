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

package org.astermail.android.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

const val CONTACT_PHOTO_MAX_EDGE = 512
const val CONTACT_PHOTO_MAX_BYTES = 180 * 1024

private val QUALITY_STEPS = intArrayOf(85, 70, 55, 40, 25)

sealed interface ContactPhotoEncodeResult {
    data class Encoded(val data_uri: String) : ContactPhotoEncodeResult
    data object Unreadable : ContactPhotoEncodeResult
    data object TooLarge : ContactPhotoEncodeResult
}

fun encode_contact_photo(context: Context, uri: Uri): String? =
    (encode_contact_photo_result(context, uri) as? ContactPhotoEncodeResult.Encoded)?.data_uri

fun encode_contact_photo_result(context: Context, uri: Uri): ContactPhotoEncodeResult {
    val decoded = runCatching { decode_contact_photo_uri(context, uri) }
        .getOrNull()
        ?: return ContactPhotoEncodeResult.Unreadable
    return compress_contact_photo(decoded)
        ?.let { ContactPhotoEncodeResult.Encoded(it) }
        ?: ContactPhotoEncodeResult.TooLarge
}

private fun decode_contact_photo_uri(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val from_decoder = runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val width = info.size.width
                val height = info.size.height
                val short_edge = minOf(width, height)
                if (short_edge > CONTACT_PHOTO_MAX_EDGE * 2) {
                    val ratio = (CONTACT_PHOTO_MAX_EDGE * 2).toFloat() / short_edge
                    decoder.setTargetSize(
                        (width * ratio).toInt().coerceAtLeast(1),
                        (height * ratio).toInt().coerceAtLeast(1),
                    )
                }
            }
        }.getOrNull()
        if (from_decoder != null) return from_decoder
    }
    val decoded = decode_sampled_stream { context.contentResolver.openInputStream(uri) } ?: return null
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    return apply_contact_photo_orientation(decoded, orientation)
}

private fun apply_contact_photo_orientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val rotated = runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrNull() ?: return bitmap
    if (rotated != bitmap) bitmap.recycle()
    return rotated
}

fun encode_contact_photo_bytes(bytes: ByteArray): String? =
    encode_contact_photo_stream { ByteArrayInputStream(bytes) }

fun decode_contact_photo_data_uri(data_uri: String): ByteArray? {
    if (!data_uri.startsWith("data:image/", ignoreCase = true)) return null
    val comma = data_uri.indexOf(',')
    if (comma < 0) return null
    val header = data_uri.substring(0, comma)
    if (!header.contains(";base64", ignoreCase = true)) return null
    return runCatching { Base64.decode(data_uri.substring(comma + 1), Base64.DEFAULT) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
}

fun normalize_contact_photo(data_uri: String): String {
    if (data_uri.isBlank()) return ""
    val bytes = decode_contact_photo_data_uri(data_uri) ?: return ""
    if (bytes.size <= CONTACT_PHOTO_MAX_BYTES) return data_uri
    return runCatching { encode_contact_photo_bytes(bytes) }.getOrNull().orEmpty()
}

private fun encode_contact_photo_stream(open: () -> InputStream?): String? {
    val decoded = decode_sampled_stream(open) ?: return null
    return compress_contact_photo(decoded)
}

private fun decode_sampled_stream(open: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val bounds_stream = open() ?: return null
    bounds_stream.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (
        bounds.outWidth / sample > CONTACT_PHOTO_MAX_EDGE * 2 ||
        bounds.outHeight / sample > CONTACT_PHOTO_MAX_EDGE * 2
    ) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return open()?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

private fun compress_contact_photo(decoded: Bitmap): String? {
    val scaled = scale_to_square(decoded)
    if (scaled != decoded) decoded.recycle()

    for (quality in QUALITY_STEPS) {
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        val bytes = output.toByteArray()
        if (bytes.size <= CONTACT_PHOTO_MAX_BYTES) {
            scaled.recycle()
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return "data:image/jpeg;base64,$encoded"
        }
    }

    scaled.recycle()
    return null
}

private fun scale_to_square(source: Bitmap): Bitmap {
    val edge = minOf(source.width, source.height)
    if (edge <= 0) return source
    val left = (source.width - edge) / 2
    val top = (source.height - edge) / 2
    val cropped = if (edge == source.width && edge == source.height) {
        source
    } else {
        Bitmap.createBitmap(source, left, top, edge, edge)
    }
    if (edge <= CONTACT_PHOTO_MAX_EDGE) return cropped
    val resized = Bitmap.createScaledBitmap(
        cropped,
        CONTACT_PHOTO_MAX_EDGE,
        CONTACT_PHOTO_MAX_EDGE,
        true,
    )
    if (cropped != source && cropped != resized) cropped.recycle()
    return resized
}
