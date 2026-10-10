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
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_haptic
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.remember_haptic
import org.astermail.android.ui.theme.local_accessibility
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val indicator_width = 4.dp
private val indicator_edge_inset = 3.dp
private val indicator_track_inset = 4.dp
private val indicator_min_thumb = 32.dp
private const val indicator_hide_delay_ms = 1000L
private const val indicator_fade_in_ms = 90
private const val indicator_fade_out_ms = 280
private const val indicator_alpha = 0.55f
private val bar_touch_width = 28.dp
private val bar_width = 4.dp
private val bar_width_active = 8.dp
private val bar_min_thumb = 44.dp
private const val bar_hide_delay_ms = 1400L

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

internal fun thumb_drag_to_content_delta(
    thumb_delta_px: Float,
    track_px: Float,
    thumb_px: Float,
    content_px: Float,
    viewport_px: Float,
): Float {
    val travel = track_px - thumb_px
    val range = content_px - viewport_px
    if (travel <= 0f || range <= 0f) return 0f
    return thumb_delta_px * range / travel
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
    private val seen = HashMap<Any, Int>()
    private var typical = 0
    private var typical_stale = false

    fun record(index: Int, key: Any, size_px: Int) {
        val known = keys[index]
        if (known != null && known != key) {
            keys.clear()
            sizes.clear()
        }
        keys[index] = key
        sizes[index] = size_px
        if (seen.size >= seen_limit && !seen.containsKey(key)) seen.clear()
        if (seen.put(key, size_px) != size_px) typical_stale = true
    }

    fun extent(
        total_items: Int,
        first_index: Int,
        first_scroll_px: Int,
        spacing_px: Int = 0,
        padding_px: Int = 0,
    ): lazy_extent {
        if (total_items <= 0) return lazy_extent(padding_px.toFloat(), 0f)
        if (typical_stale) {
            typical = lower_median(seen.values)
            typical_stale = false
        }
        val before = first_index.coerceIn(0, total_items)
        var known = 0f
        var known_count = 0
        var known_before = 0f
        var known_before_count = 0
        for ((index, size) in sizes) {
            if (index >= total_items) continue
            known += size
            known_count++
            if (index < before) {
                known_before += size
                known_before_count++
            }
        }
        val fallback = typical.toFloat()
        val content = known + fallback * (total_items - known_count) +
            spacing_px.toFloat() * (total_items - 1) + padding_px
        val offset = known_before + fallback * (before - known_before_count) +
            spacing_px.toFloat() * before + first_scroll_px
        return lazy_extent(content, offset)
    }

    private fun lower_median(values: Collection<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    private companion object {
        const val seen_limit = 4096
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
private fun remember_bar_alpha(key: Any, is_active: () -> Boolean): Animatable<Float, AnimationVector1D> {
    val reduce_motion = aster_reduce_motion()
    val live_reduce_motion by rememberUpdatedState(reduce_motion)
    val live_active by rememberUpdatedState(is_active)
    val alpha = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        snapshotFlow { live_active() }.collectLatest { active ->
            if (active) {
                if (live_reduce_motion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(indicator_fade_in_ms))
            } else {
                delay(bar_hide_delay_ms)
                if (live_reduce_motion) alpha.snapTo(0f) else alpha.animateTo(0f, tween(indicator_fade_out_ms))
            }
        }
    }
    return alpha
}

@Composable
fun Modifier.vertical_scroll_indicator(
    state: ScrollState,
    top_inset: Dp = 0.dp,
    bottom_inset: Dp = 0.dp,
): Modifier {
    val color = AsterMaterial.colors.text_muted.copy(alpha = indicator_alpha)
    val alpha = remember_bar_alpha(state) { state.isScrollInProgress }
    return drawWithContent {
        drawContent()
        val current_alpha = alpha.value
        if (current_alpha <= 0f) return@drawWithContent
        val track_top = top_inset.toPx() + indicator_track_inset.toPx()
        val thumb = scroll_thumb_geometry(
            viewport_px = size.height,
            content_px = size.height + state.maxValue,
            offset_px = state.value.toFloat(),
            track_px = size.height - track_top - bottom_inset.toPx() - indicator_track_inset.toPx(),
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

@Composable
fun Modifier.vertical_scroll_with_indicator(state: ScrollState = rememberScrollState()): Modifier =
    vertical_scroll_indicator(state).verticalScroll(state)

private fun lazy_scroll_thumb(
    state: LazyListState,
    estimator: lazy_extent_estimator,
    track_px: Float,
    min_thumb_px: Float,
    geometry: FloatArray,
): scroll_thumb? {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    visible.forEach { estimator.record(it.index, it.key, it.size) }
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
    val thumb = scroll_thumb_geometry(
        viewport_px = viewport,
        content_px = extent.content_px,
        offset_px = extent.offset_px,
        track_px = track_px,
        min_thumb_px = min_thumb_px,
    ) ?: return null
    geometry[0] = thumb.top
    geometry[1] = thumb.height
    geometry[2] = track_px
    geometry[3] = extent.content_px
    geometry[4] = viewport
    return thumb
}

@Composable
fun vertical_scroll_bar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    top_inset: Dp = 0.dp,
    bottom_inset: Dp = 0.dp,
) {
    val colors = AsterMaterial.colors
    val idle_color = colors.text_muted.copy(alpha = indicator_alpha)
    val active_color = colors.accent_blue
    val haptics = remember_haptic()
    val haptic_enabled = local_accessibility.current.haptic_enabled
    val scope = rememberCoroutineScope()
    val estimator = remember(state) { lazy_extent_estimator() }
    val geometry = remember(state) { FloatArray(5) }
    val drag = remember(state) { FloatArray(2) }
    var dragging by remember(state) { mutableStateOf(false) }
    val alpha = remember_bar_alpha(state) { state.isScrollInProgress || dragging }
    val shown by remember(state, alpha) { derivedStateOf { alpha.value > 0f } }

    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { Triple(it.index, it.key, it.size) } }
            .collect { items -> items.forEach { estimator.record(it.first, it.second, it.third) } }
    }

    if (!shown && !dragging) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = top_inset, bottom = bottom_inset)
            .width(bar_touch_width),
    ) {
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val track_top = indicator_track_inset.toPx()
                    val thumb = lazy_scroll_thumb(
                        state = state,
                        estimator = estimator,
                        track_px = constraints.maxHeight - track_top * 2f,
                        min_thumb_px = bar_min_thumb.toPx(),
                        geometry = geometry,
                    )
                    drag[1] = 0f
                    if (thumb == null) geometry[1] = 0f
                    val placeable = measurable.measure(
                        Constraints.fixed(constraints.maxWidth, thumb?.height?.roundToInt() ?: 0),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, (track_top + (thumb?.top ?: 0f)).roundToInt())
                    }
                }
                .pointerInput(state) {
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            drag[0] = start.y
                            drag[1] = 0f
                            dragging = true
                            scope.launch { state.stopScroll(MutatePriority.UserInput) }
                            if (haptic_enabled) haptics(aster_haptic.tick)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val thumb_delta = change.position.y - drag[0] - drag[1]
                            val content_delta = thumb_drag_to_content_delta(
                                thumb_delta_px = thumb_delta,
                                track_px = geometry[2],
                                thumb_px = geometry[1],
                                content_px = geometry[3],
                                viewport_px = geometry[4],
                            )
                            if (content_delta != 0f) {
                                val consumed = state.dispatchRawDelta(content_delta)
                                drag[1] += thumb_delta * (consumed / content_delta)
                            }
                        },
                    )
                }
                .drawBehind {
                    if (size.height <= 0f) return@drawBehind
                    val width = (if (dragging) bar_width_active else bar_width).toPx()
                    drawRoundRect(
                        color = if (dragging) active_color else idle_color,
                        topLeft = Offset(
                            x = scroll_thumb_left(size.width, width, indicator_edge_inset.toPx(), layoutDirection == LayoutDirection.Rtl),
                            y = 0f,
                        ),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(width / 2f),
                        alpha = if (dragging) 1f else alpha.value,
                    )
                },
        )
    }
}
