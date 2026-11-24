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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
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
        refresh_typical()
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

    fun snapshot(total_items: Int, spacing_px: Int = 0): lazy_offset_map {
        refresh_typical()
        val total = total_items.coerceAtLeast(0)
        val indices = sizes.keys.filter { it in 0 until total }.sorted()
        val known_index = IntArray(indices.size)
        val known_prefix = DoubleArray(indices.size + 1)
        for (i in indices.indices) {
            known_index[i] = indices[i]
            known_prefix[i + 1] = known_prefix[i] + (sizes[indices[i]] ?: 0)
        }
        return lazy_offset_map(
            known_index = known_index,
            known_prefix = known_prefix,
            fallback = typical.toDouble(),
            spacing = spacing_px.toDouble(),
            total_items = total,
        )
    }

    private fun refresh_typical() {
        if (!typical_stale) return
        typical = lower_median(seen.values)
        typical_stale = false
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

internal data class lazy_position(val index: Int, val offset_px: Int)

internal class lazy_offset_map(
    private val known_index: IntArray,
    private val known_prefix: DoubleArray,
    private val fallback: Double,
    private val spacing: Double,
    val total_items: Int,
) {
    private fun known_below(index: Int): Int {
        var low = 0
        var high = known_index.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (known_index[mid] < index) low = mid + 1 else high = mid
        }
        return low
    }

    fun offset_of(index: Int): Double {
        val i = index.coerceIn(0, total_items)
        val count = known_below(i)
        return known_prefix[count] + fallback * (i - count) + spacing * i
    }

    fun content_px(padding_px: Int = 0): Double {
        if (total_items <= 0) return padding_px.toDouble()
        return offset_of(total_items) - spacing + padding_px
    }

    fun locate(offset_px: Double): lazy_position {
        if (total_items <= 0 || offset_px.isNaN()) return lazy_position(0, 0)
        val target = offset_px.coerceAtLeast(0.0)
        var low = 0
        var high = total_items - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (offset_of(mid) <= target) low = mid else high = mid - 1
        }
        val intra = (target - offset_of(low)).coerceAtLeast(0.0)
        return lazy_position(low, min(intra, Int.MAX_VALUE.toDouble() / 2).roundToInt())
    }
}

internal fun fast_scroll_drag_offset(
    top_px: Float,
    start_top_px: Float,
    travel_px: Float,
    start_offset_px: Double,
    range_px: Double,
): Double {
    if (range_px <= 0.0) return 0.0
    val start = start_offset_px.coerceIn(0.0, range_px)
    if (travel_px <= 0f || top_px.isNaN()) return start
    val top = top_px.coerceIn(0f, travel_px).toDouble()
    val start_top = start_top_px.coerceIn(0f, travel_px).toDouble()
    return if (top <= start_top) {
        if (start_top <= 0.0) start else start * (top / start_top)
    } else {
        val span = travel_px - start_top
        if (span <= 0.0) range_px else start + (range_px - start) * ((top - start_top) / span)
    }
}

internal const val fast_scroll_end_target = Int.MAX_VALUE

internal fun fast_scroll_target_px(offset_px: Double, range_px: Double): Int {
    if (offset_px.isNaN()) return 0
    if (range_px > 0.0 && offset_px >= range_px - 0.5) return fast_scroll_end_target
    return offset_px.coerceIn(0.0, (fast_scroll_end_target - 1).toDouble()).roundToInt()
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
    if (local_accessibility.current.hide_scrollbars) return this
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
    if (local_accessibility.current.hide_scrollbars) return this
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

internal fun fast_scroll_thumb_fraction(thumb_top_px: Float, track_px: Float, thumb_px: Float): Float {
    val travel = track_px - thumb_px
    if (travel <= 0f || thumb_top_px.isNaN()) return 0f
    return (thumb_top_px / travel).coerceIn(0f, 1f)
}

internal fun fast_scroll_target_index(fraction: Float, total_items: Int, visible_items: Int = 1): Int {
    if (total_items <= 1 || fraction.isNaN()) return 0
    val last_first = (total_items - visible_items.coerceAtLeast(1)).coerceAtLeast(0)
    return (fraction.coerceIn(0f, 1f) * last_first).roundToInt().coerceIn(0, last_first)
}

internal class fast_scroll_coalescer {
    private var pending = -1
    private var applied = -1

    fun offer(index: Int) {
        if (index >= 0) pending = index
    }

    fun take(): Int? {
        val next = pending
        if (next < 0 || next == applied) return null
        applied = next
        return next
    }

    fun reset() {
        pending = -1
        applied = -1
    }
}

@Stable
class fast_scroll_drag_signal {
    var dragging by mutableStateOf(false)
        internal set
}

val local_fast_scroll_drag = staticCompositionLocalOf<fast_scroll_drag_signal?> { null }

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

private class fast_scroll_plan(
    val map: lazy_offset_map,
    val start_top: Float,
    val travel: Float,
    val start_offset: Double,
    val range: Double,
    val end_overscroll_px: Int,
)

private const val release_settle_ms = 220

@Composable
fun vertical_scroll_bar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    top_inset: Dp = 0.dp,
    bottom_inset: Dp = 0.dp,
    drag_signal: fast_scroll_drag_signal? = null,
) {
    if (local_accessibility.current.hide_scrollbars) return
    val colors = AsterMaterial.colors
    val idle_color = colors.text_muted.copy(alpha = indicator_alpha)
    val active_color = colors.accent_blue
    val haptics = remember_haptic()
    val haptic_enabled = local_accessibility.current.haptic_enabled
    val scope = rememberCoroutineScope()
    val min_thumb_px = with(LocalDensity.current) { bar_min_thumb.toPx() }
    val reduce_motion = aster_reduce_motion()
    val live_reduce_motion by rememberUpdatedState(reduce_motion)
    val estimator = remember(state) { lazy_extent_estimator() }
    val geometry = remember(state) { FloatArray(6) }
    val grab = remember(state) { FloatArray(1) }
    val plan_holder = remember(state) { arrayOfNulls<fast_scroll_plan>(1) }
    val coalescer = remember(state) { fast_scroll_coalescer() }
    val finger_top = remember(state) { mutableFloatStateOf(0f) }
    val correction = remember(state) { Animatable(0f) }
    var dragging by remember(state) { mutableStateOf(false) }
    var thumb_pinned by remember(state) { mutableStateOf(false) }
    val live_signal by rememberUpdatedState(drag_signal)
    val alpha = remember_bar_alpha(state) { state.isScrollInProgress || dragging || thumb_pinned }
    val shown by remember(state, alpha) { derivedStateOf { alpha.value > 0f } }

    fun apply_target(target: Int) {
        val plan = plan_holder[0] ?: return
        val total = state.layoutInfo.totalItemsCount
        if (total <= 0) return
        if (target == fast_scroll_end_target) {
            state.requestScrollToItem(total - 1, plan.end_overscroll_px)
            return
        }
        val position = plan.map.locate(target.toDouble())
        if (position.index >= total) {
            state.requestScrollToItem(total - 1, plan.end_overscroll_px)
        } else {
            state.requestScrollToItem(position.index, position.offset_px)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo }.collect { info ->
            if (dragging) return@collect
            val visible = info.visibleItemsInfo
            for (i in visible.indices) {
                val item = visible[i]
                estimator.record(item.index, item.key, item.size)
            }
        }
    }

    LaunchedEffect(state, dragging) {
        live_signal?.dragging = dragging
        if (dragging) {
            while (isActive) {
                withFrameNanos { }
                val target = coalescer.take() ?: continue
                apply_target(target)
            }
            return@LaunchedEffect
        }
        if (!thumb_pinned) return@LaunchedEffect
        coalescer.take()?.let { apply_target(it) }
        withFrameNanos { }
        withFrameNanos { }
        val live = lazy_scroll_thumb(
            state = state,
            estimator = estimator,
            track_px = geometry[2],
            min_thumb_px = min_thumb_px,
            geometry = FloatArray(6),
        )
        val gap = if (live == null) 0f else finger_top.floatValue - live.top
        correction.snapTo(if (live_reduce_motion) 0f else gap)
        thumb_pinned = false
        if (correction.value != 0f) correction.animateTo(0f, tween(release_settle_ms))
    }

    DisposableEffect(state) {
        onDispose { live_signal?.dragging = false }
    }

    if (!shown && !dragging && !thumb_pinned) return

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
                    val track_px = constraints.maxHeight - track_top * 2f
                    val follow_finger = (dragging || thumb_pinned) && geometry[1] > 0f
                    val thumb = if (follow_finger) {
                        val height = min(geometry[1], max(track_px, 0f))
                        scroll_thumb(
                            top = finger_top.floatValue.coerceIn(0f, max(track_px - height, 0f)),
                            height = height,
                        )
                    } else {
                        lazy_scroll_thumb(
                            state = state,
                            estimator = estimator,
                            track_px = track_px,
                            min_thumb_px = min_thumb_px,
                            geometry = geometry,
                        )?.let { base ->
                            val shift = correction.value
                            if (shift == 0f) {
                                base
                            } else {
                                base.copy(top = (base.top + shift).coerceIn(0f, max(track_px - base.height, 0f)))
                            }
                        }
                    }
                    if (thumb == null) geometry[1] = 0f
                    geometry[2] = track_px
                    val placed_y = (track_top + (thumb?.top ?: 0f)).roundToInt()
                    geometry[5] = placed_y - track_top
                    val placeable = measurable.measure(
                        Constraints.fixed(constraints.maxWidth, thumb?.height?.roundToInt() ?: 0),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, placed_y)
                    }
                }
                .pointerInput(state) {
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            val info = state.layoutInfo
                            val first = info.visibleItemsInfo.firstOrNull()
                            val map = estimator.snapshot(info.totalItemsCount, info.mainAxisItemSpacing)
                            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toDouble()
                            val start_offset = if (first == null) 0.0 else max(map.offset_of(first.index) - first.offset, 0.0)
                            val content = map.content_px(info.beforeContentPadding + info.afterContentPadding)
                            val start_top = geometry[5]
                            plan_holder[0] = fast_scroll_plan(
                                map = map,
                                start_top = start_top,
                                travel = max(geometry[2] - geometry[1], 0f),
                                start_offset = start_offset,
                                range = max(content - viewport, start_offset),
                                end_overscroll_px = (viewport * 4).toInt().coerceIn(4096, 1 shl 22),
                            )
                            grab[0] = start.y
                            finger_top.floatValue = start_top
                            coalescer.reset()
                            thumb_pinned = false
                            dragging = true
                            scope.launch {
                                correction.snapTo(0f)
                                state.stopScroll(MutatePriority.UserInput)
                            }
                            if (haptic_enabled) haptics(aster_haptic.tick)
                        },
                        onDragEnd = {
                            thumb_pinned = true
                            dragging = false
                        },
                        onDragCancel = {
                            thumb_pinned = true
                            dragging = false
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val plan = plan_holder[0]
                            if (plan != null) {
                                val top = (geometry[5] + change.position.y - grab[0]).coerceIn(0f, plan.travel)
                                finger_top.floatValue = top
                                val offset = fast_scroll_drag_offset(
                                    top_px = top,
                                    start_top_px = plan.start_top,
                                    travel_px = plan.travel,
                                    start_offset_px = plan.start_offset,
                                    range_px = plan.range,
                                )
                                coalescer.offer(fast_scroll_target_px(offset, plan.range))
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
                        alpha = if (dragging || thumb_pinned) 1f else alpha.value,
                    )
                },
        )
    }
}
