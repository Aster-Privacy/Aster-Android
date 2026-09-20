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

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.in_overlay_window
import org.astermail.android.ui.theme.draw_theme_window_slice
import org.astermail.android.ui.theme.remember_active_theme_bitmap
import org.astermail.android.ui.theme.remember_active_theme_blur
import org.astermail.android.ui.theme.theme_veil_alpha

private const val glass_bar_tint_strength = 0.86f

private fun chrome_solid_color(colors: AsterSemanticColors): Color =
    colors.glass_surface(colors.bg_card).copy(alpha = 1f)

@Composable
fun Modifier.glass_chrome(
    colors: AsterSemanticColors,
    alpha: () -> Float = { 1f },
    fade_bottom: Dp = 0.dp,
    fade_top: Dp = 0.dp,
    solid: Color = chrome_solid_color(colors),
): Modifier {
    val overlay = in_overlay_window()
    if (overlay || !colors.is_glass) return this.drawBehind { drawRect(color = solid, alpha = alpha().coerceIn(0f, 1f)) }
    if (!colors.is_translucent) return this.drawBehind { drawRect(color = solid, alpha = alpha().coerceIn(0f, 1f)) }
    val blur = remember_active_theme_blur()
        ?: return this.drawBehind { drawRect(color = solid, alpha = alpha().coerceIn(0f, 1f)) }
    val container = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    val veil = colors.bg_primary
    val tint = colors.glass_surface(colors.bg_card)
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .clipToBounds()
        .drawBehind {
            val fade = alpha().coerceIn(0f, 1f)
            if (fade <= 0f) return@drawBehind
            val window = Size(
                container.width.toFloat(),
                container.height.toFloat(),
            )
            val bottom_px = fade_bottom.toPx().coerceAtMost(size.height)
            val top_px = fade_top.toPx().coerceAtMost(size.height - bottom_px)
            val masked = bottom_px > 0.5f || top_px > 0.5f
            val layered = masked || fade < 0.999f
            if (layered) {
                drawIntoCanvas {
                    it.saveLayer(
                        androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                        androidx.compose.ui.graphics.Paint().apply { this.alpha = fade },
                    )
                }
            }
            drawRect(color = solid)
            draw_theme_window_slice(blur, window, origin)
            drawRect(color = veil.copy(alpha = theme_veil_alpha))
            drawRect(color = tint.copy(alpha = tint.alpha * 0.7f))
            if (masked) {
                val stops = mutableListOf<Pair<Float, Color>>()
                stops += 0f to Color.Transparent.copy(alpha = if (top_px > 0.5f) 0f else 1f)
                if (top_px > 0.5f) stops += (top_px / size.height) to Color.Black
                if (bottom_px > 0.5f) stops += (1f - bottom_px / size.height) to Color.Black
                stops += 1f to (if (bottom_px > 0.5f) Color.Transparent else Color.Black)
                drawRect(
                    brush = Brush.verticalGradient(colorStops = stops.map { it.first to it.second }.toTypedArray()),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (layered) drawContext.canvas.restore()
        }
}

@Composable
fun Modifier.glass_bar(colors: AsterSemanticColors): Modifier {
    val overlay = in_overlay_window()
    if (!colors.is_glass) return this.background(colors.bg_primary)
    val solid = chrome_solid_color(colors)
    if (overlay) return this.background(solid)
    if (!colors.is_translucent) return this.background(solid)
    val blur = remember_active_theme_blur() ?: return this.background(solid)
    val container = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    val veil = colors.bg_primary
    val tint = colors.glass_surface(colors.bg_card)
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .clipToBounds()
        .drawBehind {
            drawRect(color = solid)
            val window = Size(container.width.toFloat(), container.height.toFloat())
            draw_theme_window_slice(blur, window, origin)
            drawRect(color = veil.copy(alpha = theme_veil_alpha))
            drawRect(color = tint.copy(alpha = tint.alpha * glass_bar_tint_strength))
        }
}

@Composable
fun Modifier.page_punch(colors: AsterSemanticColors): Modifier {
    val overlay = in_overlay_window()
    if (overlay || !colors.is_glass) return this.background(colors.bg_primary)
    val photo = remember_active_theme_bitmap() ?: return this.background(colors.bg_primary)
    val container = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    val veil = colors.bg_primary
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .clipToBounds()
        .drawBehind {
            drawRect(color = veil)
            val window = Size(container.width.toFloat(), container.height.toFloat())
            draw_theme_window_slice(photo, window, origin)
            drawRect(color = veil.copy(alpha = theme_veil_alpha))
        }
}

@Composable
fun Modifier.theme_page_fill(colors: AsterSemanticColors): Modifier {
    if (!colors.is_glass) return this.background(colors.solid_bg)
    val photo = remember_active_theme_bitmap() ?: return this.background(colors.solid_bg)
    val veil = colors.bg_primary
    return this.drawBehind {
        drawRect(color = veil)
        draw_theme_window_slice(photo, size, Offset.Zero)
        drawRect(color = veil.copy(alpha = theme_veil_alpha))
    }
}
