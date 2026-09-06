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

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.astermail.android.ui.mail.SenderAvatar

@Composable
fun ContactAvatar(
    avatar_url: String,
    email: String,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    content_description: String? = null,
) {
    val bitmap = remember(avatar_url) { decode_contact_photo(avatar_url) }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = content_description,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }

    SenderAvatar(
        email = email,
        name = name,
        size = size,
        modifier = modifier,
        profile_picture_url = avatar_url.takeIf { it.startsWith("http", ignoreCase = true) },
    )
}

private fun decode_contact_photo(avatar_url: String): ImageBitmap? {
    if (!avatar_url.startsWith("data:image", ignoreCase = true)) return null
    val payload = avatar_url.substringAfter("base64,", "")
    if (payload.isEmpty()) return null

    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
