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

package org.astermail.android.ui.settings.detail

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterEasing
import org.astermail.android.ui.theme.custom_theme_image

private val editor_page_bg = Color(0xFF0B0B0D)
private val editor_bar_bg = Color(0xFF17171B)
private val editor_border = Color(0xFF2A2A30)
private val editor_control_bg = Color(0xFF26262B)
private val editor_chrome_bg = Color(0xFF1C1C20)
private val editor_chrome_line = Color(0xFF3A3A41)
private val editor_hint = Color(0xFFA1A1AA)
private val editor_frame_shape = RoundedCornerShape(30.dp)
private const val editor_max_zoom = 4f
private const val editor_double_tap_zoom = 2.5f

private class PhotoTransform {
    var zoom by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
}

private fun cover_scale(frame: IntSize, bitmap: Bitmap): Float =
    max(frame.width.toFloat() / bitmap.width, frame.height.toFloat() / bitmap.height)

private fun clamp_translation(value: Float, content: Float, frame: Float): Float {
    val limit = ((content - frame) / 2f).coerceAtLeast(0f)
    return value.coerceIn(-limit, limit)
}

private fun PhotoTransform.apply_zoom(
    frame: IntSize,
    bitmap: Bitmap,
    target_zoom: Float,
    focal: Offset,
    pan: Offset,
) {
    val base = cover_scale(frame, bitmap)
    val next = target_zoom.coerceIn(1f, editor_max_zoom)
    val change = next / zoom
    val next_tx = (tx - focal.x) * change + focal.x + pan.x
    val next_ty = (ty - focal.y) * change + focal.y + pan.y
    zoom = next
    tx = clamp_translation(next_tx, bitmap.width * base * next, frame.width.toFloat())
    ty = clamp_translation(next_ty, bitmap.height * base * next, frame.height.toFloat())
}

private fun PhotoTransform.load_crop(frame: IntSize, bitmap: Bitmap, crop: RectF?) {
    if (crop == null || frame.width <= 0 || frame.height <= 0) {
        zoom = 1f
        tx = 0f
        ty = 0f
        return
    }
    val base = cover_scale(frame, bitmap)
    val visible_w = crop.width() * bitmap.width
    val visible_h = crop.height() * bitmap.height
    val next = max(frame.width / (base * visible_w), frame.height / (base * visible_h)).coerceIn(1f, editor_max_zoom)
    val scale = base * next
    zoom = next
    tx = clamp_translation((bitmap.width / 2f - crop.centerX() * bitmap.width) * scale, bitmap.width * scale, frame.width.toFloat())
    ty = clamp_translation((bitmap.height / 2f - crop.centerY() * bitmap.height) * scale, bitmap.height * scale, frame.height.toFloat())
}

private fun PhotoTransform.to_crop(frame: IntSize, bitmap: Bitmap): RectF {
    val scale = cover_scale(frame, bitmap) * zoom
    val visible_w = frame.width / scale
    val visible_h = frame.height / scale
    val center_x = bitmap.width / 2f - tx / scale
    val center_y = bitmap.height / 2f - ty / scale
    return RectF(
        ((center_x - visible_w / 2f) / bitmap.width).coerceIn(0f, 1f),
        ((center_y - visible_h / 2f) / bitmap.height).coerceIn(0f, 1f),
        ((center_x + visible_w / 2f) / bitmap.width).coerceIn(0f, 1f),
        ((center_y + visible_h / 2f) / bitmap.height).coerceIn(0f, 1f),
    )
}

@Composable
fun image_theme_photo_editor(
    source: Bitmap,
    initial_crop: RectF?,
    accent: Color,
    on_accent: Color,
    busy: Boolean,
    on_cancel: () -> Unit,
    on_set: (RectF) -> Unit,
) {
    val image = remember(source) { source.asImageBitmap() }
    val transform = remember(source) { PhotoTransform() }
    var frame_size by remember { mutableStateOf(IntSize.Zero) }
    var crop_loaded by remember(source) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!busy) on_cancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.rgb(0x17, 0x17, 0x1B)
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.rgb(0x0B, 0x0B, 0x0D)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(editor_page_bg)
                .testTag("image_theme_editor"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.image_theme_editor_title),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.image_theme_editor_hint),
                    color = editor_hint,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(custom_theme_image.frame_aspect, matchHeightConstraintsFirst = true)
                        .clip(editor_frame_shape)
                        .background(editor_chrome_bg)
                        .border(1.dp, editor_border, editor_frame_shape)
                        .onSizeChanged { size ->
                            if (size == frame_size) return@onSizeChanged
                            val previous = frame_size
                            frame_size = size
                            if (!crop_loaded) {
                                transform.load_crop(size, source, initial_crop)
                                crop_loaded = true
                            } else if (previous.width > 0) {
                                transform.load_crop(size, source, transform.to_crop(previous, source))
                            }
                        }
                        .pointerInput(source) {
                            detectTransformGestures { centroid, pan, zoom_change, _ ->
                                val size = frame_size
                                if (size.width <= 0) return@detectTransformGestures
                                val focal = Offset(centroid.x - size.width / 2f, centroid.y - size.height / 2f)
                                transform.apply_zoom(size, source, transform.zoom * zoom_change, focal, pan)
                            }
                        }
                        .pointerInput(source) {
                            detectTapGestures(
                                onDoubleTap = { position ->
                                    val size = frame_size
                                    if (size.width <= 0) return@detectTapGestures
                                    val start = transform.zoom
                                    val target = if (start > 1.01f) 1f else editor_double_tap_zoom
                                    val focal = Offset(position.x - size.width / 2f, position.y - size.height / 2f)
                                    scope.launch {
                                        animate(start, target, animationSpec = tween(280, easing = AsterEasing.standard_enter)) { value, _ ->
                                            transform.apply_zoom(size, source, value, focal, Offset.Zero)
                                        }
                                    }
                                },
                            )
                        }
                        .testTag("image_theme_editor_frame"),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = transform.zoom
                                scaleY = transform.zoom
                                translationX = transform.tx
                                translationY = transform.ty
                            },
                    ) {
                        val scale = max(size.width / image.width, size.height / image.height)
                        val w = image.width * scale
                        val h = image.height * scale
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                    editor_chrome_preview(accent = accent)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(editor_bar_bg)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                editor_button(
                    label = stringResource(R.string.cancel),
                    fill = editor_control_bg,
                    content = Color.White,
                    enabled = !busy,
                    busy = false,
                    on_click = on_cancel,
                    modifier = Modifier.weight(1f).testTag("image_theme_editor_cancel"),
                )
                editor_button(
                    label = stringResource(R.string.image_theme_editor_set),
                    fill = accent,
                    content = on_accent,
                    enabled = !busy && frame_size.width > 0,
                    busy = busy,
                    on_click = { on_set(transform.to_crop(frame_size, source)) },
                    modifier = Modifier.weight(1f).testTag("image_theme_editor_set"),
                )
            }
        }
    }
}

@Composable
private fun editor_chrome_preview(accent: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(34.dp)
                .clip(CircleShape)
                .background(editor_chrome_bg)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(editor_chrome_line))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(72.dp).height(6.dp).clip(CircleShape).background(editor_chrome_line))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(18.dp).clip(CircleShape).background(accent))
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(editor_chrome_bg),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (index == 0) accent else editor_chrome_line),
                    )
                }
            }
        }
    }
}

@Composable
private fun editor_button(
    label: String,
    fill: Color,
    content: Color,
    enabled: Boolean,
    busy: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .clickable(enabled = enabled, onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(
                text = label,
                color = content,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
