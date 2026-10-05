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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_reduce_motion
import kotlin.math.max
import kotlin.math.min

private val indicator_width = 4.dp
private val indicator_edge_inset = 3.dp
private val indicator_track_inset = 4.dp
private val indicator_min_thumb = 32.dp
private const val indicator_hide_delay_ms = 1000L
private const val indicator_fade_in_ms = 90
private const val indicator_fade_out_ms = 280
private const val indicator_alpha = 0.55f

internal data class scroll_thumb(val top: Float, val height: Float)

internal data class lazy_extent(val content_px: Float, val offset_px: Float)

internal fun scroll_thumb_geometry(
    viewport_px: Float,
    content_px: Float,
    offset_px: Float,
    track_px: Float,
    min_thumb_px: Float,
): scroll_thumb? {
    if (viewport_px <= 0f || track_px <= 0f || content_px <= viewport_px + 0.5f) return null
    val height = (track_px * viewport_px / content_px).coerceIn(min(min_thumb_px, track_px), track_px)
    val progress = (offset_px / (content_px - viewport_px)).coerceIn(0f, 1f)
    return scroll_thumb(top = progress * (track_px - height), height = height)
}

internal fun scroll_thumb_left(
    container_width_px: Float,
    thumb_width_px: Float,
    edge_inset_px: Float,
    rtl: Boolean,
): Float = if (rtl) edge_inset_px else container_width_px - edge_inset_px - thumb_width_px

internal class lazy_extent_estimator {
    private val keys = HashMap<Int, Any>()
    private val sizes = HashMap<Int, Int>()

    fun record(index: Int, key: Any, size_px: Int) {
        val known = keys[index]
        if (known != null && known != key) {
            keys.clear()
            sizes.clear()
        }
        keys[index] = key
        sizes[index] = size_px
    }

    fun extent(
        total_items: Int,
        first_index: Int,
        first_scroll_px: Int,
        spacing_px: Int = 0,
        padding_px: Int = 0,
    ): lazy_extent {
        if (total_items <= 0) return lazy_extent(padding_px.toFloat(), 0f)
        if (sizes.keys.any { it >= total_items }) {
            sizes.keys.removeAll { it >= total_items }
            keys.keys.removeAll { it >= total_items }
        }
        val fallback = lower_median(sizes.values).toFloat()
        var content = 0f
        var offset = 0f
        for (i in 0 until total_items) {
            val size = sizes[i]?.toFloat() ?: fallback
            content += size
            if (i < first_index) offset += size
        }
        content += spacing_px.toFloat() * (total_items - 1) + padding_px
        offset += spacing_px.toFloat() * first_index.coerceAtMost(total_items) + first_scroll_px
        return lazy_extent(content, offset)
    }

    private fun lower_median(values: Collection<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }
}

internal fun pinned_lazy_extent(
    estimate: lazy_extent,
    viewport_px: Float,
    can_scroll_backward: Boolean,
    can_scroll_forward: Boolean,
): lazy_extent {
    if (!can_scroll_backward && !can_scroll_forward) return lazy_extent(viewport_px, 0f)
    val offset = if (can_scroll_backward) max(estimate.offset_px, 1f) else 0f
    val floor = offset + viewport_px + if (can_scroll_forward) 1f else 0f
    val content = max(estimate.content_px, floor)
    return lazy_extent(content, if (can_scroll_forward) offset else content - viewport_px)
}

internal data class horizontal_track(val start: Float, val end: Float)

internal fun horizontal_track_bounds(
    container_width_px: Float,
    corner_px: Float,
    track_inset_px: Float,
    rtl: Boolean,
): horizontal_track = if (rtl) {
    horizontal_track(corner_px + track_inset_px, container_width_px - track_inset_px)
} else {
    horizontal_track(track_inset_px, container_width_px - corner_px - track_inset_px)
}

@Stable
class horizontal_pan_signal {
    var offset_px by mutableFloatStateOf(0f)
        private set
    var extent_px by mutableFloatStateOf(0f)
        private set
    var range_px by mutableFloatStateOf(0f)
        private set
    var moves by mutableIntStateOf(0)
        private set

    fun report(offset_px: Int, extent_px: Int, range_px: Int) {
        val offset = offset_px.toFloat()
        val extent = extent_px.toFloat()
        val range = range_px.toFloat()
        if (offset == this.offset_px && extent == this.extent_px && range == this.range_px) return
        this.offset_px = offset
        this.extent_px = extent
        this.range_px = range
        moves++
    }
}

val local_horizontal_pan_signal = staticCompositionLocalOf<horizontal_pan_signal?> { null }

@Composable
fun Modifier.horizontal_scroll_indicator(
    signal: horizontal_pan_signal,
    bottom_inset: Dp = 0.dp,
): Modifier {
    val color = AsterMaterial.colors.text_muted.copy(alpha = indicator_alpha)
    val reduce_motion = aster_reduce_motion()
    val live_reduce_motion by rememberUpdatedState(reduce_motion)
    val alpha = remember(signal) { Animatable(0f) }

    LaunchedEffect(signal) {
        snapshotFlow { signal.moves }.collectLatest { moves ->
            if (moves == 0) return@collectLatest
            if (live_reduce_motion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(indicator_fade_in_ms))
            delay(indicator_hide_delay_ms)
            if (live_reduce_motion) alpha.snapTo(0f) else alpha.animateTo(0f, tween(indicator_fade_out_ms))
        }
    }

    return drawWithContent {
        drawContent()
        val current_alpha = alpha.value
        if (current_alpha <= 0f) return@drawWithContent
        val thickness = indicator_width.toPx()
        val track = horizontal_track_bounds(
            container_width_px = size.width,
            corner_px = indicator_edge_inset.toPx() + thickness,
            track_inset_px = indicator_track_inset.toPx(),
            rtl = layoutDirection == LayoutDirection.Rtl,
        )
        val thumb = scroll_thumb_geometry(
            viewport_px = signal.extent_px,
            content_px = signal.range_px,
            offset_px = signal.offset_px,
            track_px = track.end - track.start,
            min_thumb_px = indicator_min_thumb.toPx(),
        ) ?: return@drawWithContent
        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = track.start + thumb.top,
                y = size.height - bottom_inset.toPx() - indicator_edge_inset.toPx() - thickness,
            ),
            size = Size(thumb.height, thickness),
            cornerRadius = CornerRadius(thickness / 2f),
            alpha = current_alpha,
        )
    }
}

@Composable
fun Modifier.vertical_scroll_indicator(
    state: LazyListState,
    bottom_inset: Dp = 0.dp,
): Modifier {
    val color = AsterMaterial.colors.text_muted.copy(alpha = indicator_alpha)
    val reduce_motion = aster_reduce_motion()
    val live_reduce_motion by rememberUpdatedState(reduce_motion)
    val alpha = remember(state) { Animatable(0f) }
    val estimator = remember(state) { lazy_extent_estimator() }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                if (live_reduce_motion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(indicator_fade_in_ms))
            } else {
                delay(indicator_hide_delay_ms)
                if (live_reduce_motion) alpha.snapTo(0f) else alpha.animateTo(0f, tween(indicator_fade_out_ms))
            }
        }
    }

    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        visible.forEach { estimator.record(it.index, it.key, it.size) }
        val current_alpha = alpha.value
        if (current_alpha <= 0f || visible.isEmpty()) return@drawWithContent
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val first = visible.first()
        val extent = pinned_lazy_extent(
            estimate = estimator.extent(
                total_items = info.totalItemsCount,
                first_index = first.index,
                first_scroll_px = -first.offset,
                spacing_px = info.mainAxisItemSpacing,
                padding_px = info.beforeContentPadding + info.afterContentPadding,
            ),
            viewport_px = viewport,
            can_scroll_backward = state.canScrollBackward,
            can_scroll_forward = state.canScrollForward,
        )
        val track_top = indicator_track_inset.toPx()
        val track = size.height - track_top - indicator_track_inset.toPx() - bottom_inset.toPx()
        val thumb = scroll_thumb_geometry(
            viewport_px = viewport,
            content_px = extent.content_px,
            offset_px = extent.offset_px,
            track_px = track,
            min_thumb_px = indicator_min_thumb.toPx(),
        ) ?: return@drawWithContent
        val width = indicator_width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = scroll_thumb_left(size.width, width, indicator_edge_inset.toPx(), layoutDirection == LayoutDirection.Rtl),
                y = track_top + thumb.top,
            ),
            size = Size(width, thumb.height),
            cornerRadius = CornerRadius(width / 2f),
            alpha = current_alpha,
        )
    }
}
