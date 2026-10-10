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
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import org.astermail.android.ui.common.remember_zoom_state
import org.astermail.android.ui.common.vertical_scroll_bar
import org.astermail.android.ui.common.zoom_layer
import org.astermail.android.ui.common.zoomable

internal const val ATTACHMENT_IMAGE_MAX_DIMENSION = 4096
internal const val TEXT_VIEWER_DEFAULT_FONT_SP = 13f
internal const val TEXT_VIEWER_MIN_FONT_SP = 9f
internal const val TEXT_VIEWER_MAX_FONT_SP = 32f
internal const val TEXT_VIEWER_MAX_LINE_CHARS = 4000

private val text_attachment_types = setOf(
    "application/json",
    "application/ld+json",
    "application/xml",
    "application/x-yaml",
    "application/yaml",
    "application/toml",
    "application/javascript",
    "application/x-javascript",
    "application/x-sh",
    "application/sql",
    "application/x-ndjson",
    "application/pgp-keys",
    "application/pgp-signature",
    "application/x-pem-file",
)

private val text_attachment_extensions = setOf(
    "txt", "text", "md", "markdown", "log", "csv", "tsv", "json", "ndjson", "xml", "yml", "yaml", "toml",
    "ini", "conf", "cfg", "properties", "env", "sh", "bat", "ps1", "sql", "asc", "pem", "crt", "key", "pub",
    "eml", "ics", "vcf", "srt", "vtt", "diff", "patch", "html", "htm", "css", "js", "ts", "kt", "java", "py",
    "rs", "go", "c", "h", "cpp", "rb", "php", "swift",
)

internal fun is_text_attachment(content_type: String, filename: String): Boolean {
    val ct = content_type.substringBefore(';').trim().lowercase()
    if (ct.startsWith("text/")) return true
    if (ct in text_attachment_types || ct.endsWith("+json") || ct.endsWith("+xml")) return true
    val generic = ct.isEmpty() || ct == "application/octet-stream" || ct == "binary/octet-stream"
    if (!generic) return false
    val extension = filename.trim().substringAfterLast('.', "").lowercase()
    return extension.isNotEmpty() && extension in text_attachment_extensions
}

internal fun image_sample_size(width: Int, height: Int, max_dimension: Int = ATTACHMENT_IMAGE_MAX_DIMENSION): Int {
    var sample = 1
    val largest = maxOf(width, height)
    while (largest / sample > max_dimension) sample *= 2
    return sample
}

internal fun text_viewer_lines(bytes: ByteArray, max_line_chars: Int = TEXT_VIEWER_MAX_LINE_CHARS): List<String> {
    val text = String(bytes, Charsets.UTF_8).removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    val lines = ArrayList<String>()
    for (line in text.split('\n')) {
        if (line.length <= max_line_chars) {
            lines.add(line)
        } else {
            var start = 0
            while (start < line.length) {
                val end = minOf(start + max_line_chars, line.length)
                lines.add(line.substring(start, end))
                start = end
            }
        }
    }
    while (lines.size > 1 && lines.last().isEmpty()) lines.removeAt(lines.size - 1)
    return lines
}

internal fun text_viewer_font_size(current_sp: Float, zoom: Float): Float =
    (current_sp * zoom).coerceIn(TEXT_VIEWER_MIN_FONT_SP, TEXT_VIEWER_MAX_FONT_SP)

private sealed interface image_decode_state {
    data object loading : image_decode_state
    data object failed : image_decode_state
    class ready(val bitmap: Bitmap) : image_decode_state
}

@Composable
internal fun image_attachment_viewer(bytes: ByteArray, description: String) {
    val decoded by produceState<image_decode_state>(initialValue = image_decode_state.loading, bytes) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = image_sample_size(bounds.outWidth, bounds.outHeight)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }.getOrNull()?.let { image_decode_state.ready(it) } ?: image_decode_state.failed
        }
    }
    val zoom = remember_zoom_state(bytes)
    when (val current = decoded) {
        image_decode_state.loading -> CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(36.dp),
        )
        image_decode_state.failed -> Text(
            text = stringResource(R.string.cannot_decode_image),
            color = Color.White.copy(alpha = 0.7f),
        )
        is image_decode_state.ready -> {
            val bitmap = current.bitmap
            DisposableEffect(bitmap) {
                onDispose { runCatching { bitmap.recycle() } }
            }
            LaunchedEffect(bitmap) {
                zoom.content_aspect_ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("attachment_image_viewer")
                    .zoomable(zoom),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .zoom_layer(zoom)
                        .testTag("attachment_image"),
                )
            }
        }
    }
}

@Composable
internal fun text_attachment_viewer(bytes: ByteArray) {
    val colors = AsterMaterial.colors
    val lines by produceState<List<String>?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.Default) { text_viewer_lines(bytes) }
    }
    val loaded = lines
    if (loaded == null) {
        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        return
    }
    var font_sp by rememberSaveable { mutableFloatStateOf(TEXT_VIEWER_DEFAULT_FONT_SP) }
    val list_state = rememberLazyListState()
    val horizontal = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .clip(SquircleShape(12.dp))
            .background(colors.bg_card)
            .testTag("attachment_text_viewer")
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            font_sp = text_viewer_font_size(font_sp, event.calculateZoom())
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        SelectionContainer {
            LazyColumn(
                state = list_state,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontal),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                itemsIndexed(loaded) { _, line ->
                    Text(
                        text = line.ifEmpty { " " },
                        color = colors.text_primary,
                        fontSize = font_sp.sp,
                        lineHeight = (font_sp * 1.45f).sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
        vertical_scroll_bar(state = list_state, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd))
    }
}
