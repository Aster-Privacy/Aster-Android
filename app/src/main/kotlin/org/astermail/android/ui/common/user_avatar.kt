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

package org.astermail.android.ui.common

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.astermail.android.storage.AccountStore
import org.astermail.android.ui.mail.avatar_colors_for
import org.astermail.android.ui.mail.initial_for

@Composable
fun current_user_avatar(
    account_store: AccountStore,
    size: Dp = 88.dp,
    modifier: Modifier = Modifier,
) {
    val initial = remember(account_store) { account_store.current_account.value?.profile_picture }
    val account by account_store.current_account.collectAsStateWithLifecycle(initialValue = account_store.current_account.value)
    val url = (account?.profile_picture ?: initial)?.takeIf { it.isNotBlank() }
    val context = LocalContext.current

    if (url == null) {
        val email = account?.email.orEmpty()
        val name = account?.display_name.orEmpty()
        val seed = name.ifBlank { email }
        val (bg, fg) = avatar_colors_for(seed)
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial_for(name, email),
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Transparent),
    ) {
        val cache_key = remember(url) { stable_cache_key(url) }
        val model = remember(url) { decode_avatar_model(url) }
        val request = remember(cache_key, context, model) {
            ImageRequest.Builder(context)
                .data(model)
                .memoryCacheKey(cache_key)
                .placeholderMemoryCacheKey(cache_key)
                .diskCacheKey(cache_key)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    }
}

private fun decode_avatar_model(url: String): Any {
    if (!url.startsWith("data:")) return url
    val comma = url.indexOf(',')
    if (comma < 0) return url
    val meta = url.substring(5, comma)
    val payload = url.substring(comma + 1)
    return if (meta.contains("base64")) {
        runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: url
    } else url
}

private fun stable_cache_key(url: String): String {
    val prefix_len = minOf(32, url.length)
    val tail_len = minOf(16, url.length - prefix_len).coerceAtLeast(0)
    val tail_start = url.length - tail_len
    val prefix = url.substring(0, prefix_len)
    val tail = if (tail_len > 0) url.substring(tail_start) else ""
    return "pp_${url.length}_${prefix.hashCode()}_${tail.hashCode()}"
}
