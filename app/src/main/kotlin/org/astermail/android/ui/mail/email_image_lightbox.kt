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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import org.astermail.android.R

private const val LIGHTBOX_MIN_SCALE = 1f
private const val LIGHTBOX_MAX_SCALE = 6f
private const val LIGHTBOX_MAX_DATA_URI_CHARS = 16 * 1024 * 1024

private fun decode_data_uri_bitmap(src: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (!src.startsWith("data:image/", ignoreCase = true)) return null
    if (src.length > LIGHTBOX_MAX_DATA_URI_CHARS) return null
    val comma = src.indexOf(',')
    if (comma <= 0) return null
    val header = src.substring(0, comma)
    if (!header.contains(";base64", ignoreCase = true)) return null
    return try {
        val bytes = android.util.Base64.decode(src.substring(comma + 1), android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun email_image_lightbox(
    src: String,
    auth_header: String?,
    on_dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val inline_bitmap = remember(src) { decode_data_uri_bitmap(src) }
    val request = remember(src, auth_header) {
        ImageRequest.Builder(context)
            .data(src)
            .apply { if (!auth_header.isNullOrBlank()) addHeader("Authorization", auth_header) }
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    val state = painter.state

    var scale by remember { mutableFloatStateOf(1f) }
    var offset_x by remember { mutableFloatStateOf(0f) }
    var offset_y by remember { mutableFloatStateOf(0f) }
    var double_tap_target by remember { mutableStateOf(1f) }
    val animated_scale by animateFloatAsState(targetValue = double_tap_target, label = "lightbox_zoom")

    Dialog(
        onDismissRequest = on_dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(src) {
                    detectTapGestures(
                        onTap = { on_dismiss() },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                double_tap_target = 1f
                                offset_x = 0f
                                offset_y = 0f
                            } else {
                                scale = 2.5f
                                double_tap_target = 2.5f
                            }
                        },
                    )
                }
                .pointerInput(src) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(LIGHTBOX_MIN_SCALE, LIGHTBOX_MAX_SCALE)
                        scale = next
                        double_tap_target = next
                        if (next > 1.01f) {
                            val bound_x = size.width * (next - 1f) / 2f
                            val bound_y = size.height * (next - 1f) / 2f
                            offset_x = (offset_x + pan.x * next).coerceIn(-bound_x, bound_x)
                            offset_y = (offset_y + pan.y * next).coerceIn(-bound_y, bound_y)
                        } else {
                            offset_x = 0f
                            offset_y = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val image_modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .graphicsLayer(
                    scaleX = animated_scale,
                    scaleY = animated_scale,
                    translationX = offset_x,
                    translationY = offset_y,
                )

            if (inline_bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = inline_bitmap,
                    contentDescription = stringResource(R.string.image_lightbox_title),
                    contentScale = ContentScale.Fit,
                    modifier = image_modifier,
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = stringResource(R.string.image_lightbox_title),
                    contentScale = ContentScale.Fit,
                    modifier = image_modifier,
                )
                when (state) {
                    is AsyncImagePainter.State.Loading -> CircularProgressIndicator(color = Color.White)
                    is AsyncImagePainter.State.Error -> Text(
                        text = stringResource(R.string.image_lightbox_failed),
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                    else -> Unit
                }
            }

            IconButton(
                onClick = on_dismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
