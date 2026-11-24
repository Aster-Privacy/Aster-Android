// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val scroll_fade_edge_height = 20.dp

internal fun scroll_fade_strength(scrolled_px: Float, edge_px: Float): Float {
    if (edge_px <= 0f) return if (scrolled_px > 0f) 1f else 0f
    return (scrolled_px / edge_px).coerceIn(0f, 1f)
}

fun Modifier.top_fade_edge(
    height: Dp = scroll_fade_edge_height,
    strength: (edge_px: Float) -> Float,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge_px = height.toPx().coerceAtMost(size.height)
        if (edge_px <= 0f) return@drawWithContent
        val amount = strength(edge_px).coerceIn(0f, 1f)
        if (amount <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 1f - amount),
                1f to Color.Black,
                startY = 0f,
                endY = edge_px,
            ),
            size = Size(size.width, edge_px),
            blendMode = BlendMode.DstIn,
        )
    }

fun Modifier.scroll_top_fade(
    state: ScrollState,
    height: Dp = scroll_fade_edge_height,
): Modifier = top_fade_edge(height) { edge_px ->
    scroll_fade_strength(state.value.toFloat(), edge_px)
}

fun Modifier.lazy_top_fade(
    state: LazyListState,
    height: Dp = scroll_fade_edge_height,
): Modifier = top_fade_edge(height) { edge_px ->
    if (state.firstVisibleItemIndex > 0) {
        1f
    } else {
        scroll_fade_strength(state.firstVisibleItemScrollOffset.toFloat(), edge_px)
    }
}

fun Modifier.bottom_scrim_fade(
    color: Color,
    height: Dp = scroll_fade_edge_height,
    alpha: () -> Float,
): Modifier = drawWithContent {
    drawContent()
    val amount = alpha().coerceIn(0f, 1f)
    if (amount <= 0f) return@drawWithContent
    val edge_px = height.toPx()
    if (edge_px <= 0f) return@drawWithContent
    drawRect(
        brush = Brush.verticalGradient(
            0f to color,
            1f to color.copy(alpha = 0f),
            startY = size.height,
            endY = size.height + edge_px,
        ),
        topLeft = Offset(0f, size.height),
        size = Size(size.width, edge_px),
        alpha = amount,
    )
}

@Composable
fun Modifier.vertical_scroll_with_fade(state: ScrollState = rememberScrollState()): Modifier =
    scroll_top_fade(state).vertical_scroll_with_indicator(state)
