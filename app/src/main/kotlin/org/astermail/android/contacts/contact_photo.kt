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
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

const val CONTACT_PHOTO_MAX_EDGE = 512
const val CONTACT_PHOTO_MAX_BYTES = 180 * 1024

private val QUALITY_STEPS = intArrayOf(85, 70, 55, 40, 25)

fun encode_contact_photo(context: Context, uri: Uri): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val bounds_stream = context.contentResolver.openInputStream(uri) ?: return null
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
    val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: return null

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
