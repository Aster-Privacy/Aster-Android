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

package org.astermail.android.ui.contacts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import org.astermail.android.ui.mail.avatar_colors_for
import org.astermail.android.ui.mail.avatar_key_for
import org.astermail.android.ui.mail.avatar_initial_style
import org.astermail.android.ui.mail.initial_for

@Composable
fun ContactAvatar(
    avatar_url: String,
    email: String,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    content_description: String? = null,
    profile_color: String = "",
) {
    val (background_color, text_color) = avatar_colors_for(avatar_key_for(email, name), profile_color)
    val initial = initial_for(name, email)
    val bitmap by androidx.compose.runtime.produceState(
        initialValue = contact_photo_cache.get(avatar_url),
        avatar_url,
    ) {
        if (value == null && is_inline_contact_photo(avatar_url)) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                decode_contact_photo(avatar_url)
            }?.also { contact_photo_cache.put(avatar_url, it) }
        }
    }
    val remote_url = remember(avatar_url) {
        avatar_url.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }
    val context = LocalContext.current
    var remote_loaded by remember(remote_url) { mutableStateOf(false) }
    val label = content_description
    val label_modifier = if (label != null) {
        modifier.semantics { contentDescription = label }
    } else {
        modifier
    }

    Box(
        modifier = label_modifier
            .size(size)
            .clip(CircleShape)
            .background(if (bitmap != null || remote_loaded) Color.Transparent else background_color),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
            return@Box
        }
        if (!remote_loaded) {
            Text(
                text = initial,
                color = text_color,
                style = avatar_initial_style(size),
            )
        }
        if (remote_url != null) {
            val request = remember(remote_url, context) {
                ImageRequest.Builder(context)
                    .data(remote_url)
                    .memoryCacheKey(remote_url)
                    .placeholderMemoryCacheKey(remote_url)
                    .diskCacheKey(remote_url)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state -> remote_loaded = state is AsyncImagePainter.State.Success },
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

private const val contact_photo_min_bytes = 64

private const val contact_photo_max_px = 256

private val contact_photo_cache = object : android.util.LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun is_inline_contact_photo(avatar_url: String): Boolean =
    avatar_url.startsWith("data:image", ignoreCase = true)

private fun decode_contact_photo(avatar_url: String): ImageBitmap? {
    if (!is_inline_contact_photo(avatar_url)) return null
    val payload = avatar_url.substringAfter("base64,", "")
    if (payload.length < contact_photo_min_bytes) return null

    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        if (bytes.size < contact_photo_min_bytes) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= contact_photo_max_px &&
            bounds.outHeight / (sample * 2) >= contact_photo_max_px
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        decoded?.takeIf { it.width > 0 && it.height > 0 }?.asImageBitmap()
    }.getOrNull()
}
