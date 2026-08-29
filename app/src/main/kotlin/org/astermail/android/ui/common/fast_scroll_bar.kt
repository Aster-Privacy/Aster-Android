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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import org.astermail.android.ui.theme.local_accessibility
import kotlin.math.max
import kotlin.math.roundToInt

private val thumb_width = 6.dp
private val thumb_width_active = 10.dp
private val thumb_min_height = 44.dp
private const val min_items_for_thumb = 12

@Composable
fun fast_scroll_bar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    top_padding: Dp = 0.dp,
    bottom_padding: Dp = 0.dp,
) {
    val colors = AsterMaterial.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val haptic_enabled = local_accessibility.current.haptic_enabled

    var dragging by remember { mutableStateOf(false) }
    val drag_fraction = remember { mutableFloatStateOf(0f) }
    var locked_metrics by remember { mutableStateOf<scroll_metrics?>(null) }
    val locked_geometry = remember { floatArrayOf(0f, 0f) }

    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            val total = info.totalItemsCount
            if (visible.isEmpty() || total < min_items_for_thumb) {
                scroll_metrics(0, 0f, 0f, 0f)
            } else {
                val on_screen = visible.size.toFloat().coerceAtLeast(1f)
                val span = (total - on_screen).coerceAtLeast(0f)
                if (span <= 0f) {
                    scroll_metrics(total, 0f, 0f, 0f)
                } else {
                    val first_size = visible.first().size.toFloat().coerceAtLeast(1f)
                    val intra = (state.firstVisibleItemScrollOffset / first_size).coerceIn(0f, 1f)
                    scroll_metrics(
                        total = total,
                        span = span,
                        ratio = (on_screen / total).coerceIn(0.02f, 1f),
                        progress = ((state.firstVisibleItemIndex + intra) / span).coerceIn(0f, 1f),
                    )
                }
            }
        }
    }

    val visible_now = dragging || state.isScrollInProgress
    var recently_scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(visible_now) {
        if (visible_now) {
            recently_scrolled = true
        } else {
            kotlinx.coroutines.delay(1400)
            recently_scrolled = false
        }
    }

    val active = metrics.span > 0f && metrics.total >= min_items_for_thumb
    val target_alpha = if (active && recently_scrolled) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = target_alpha,
        animationSpec = tween(durationMillis = if (target_alpha == 1f) 90 else 320),
        label = "fast_scroll_alpha",
    )
    val width by animateFloatAsState(
        targetValue = if (dragging) thumb_width_active.value else thumb_width.value,
        animationSpec = tween(durationMillis = 120),
        label = "fast_scroll_width",
    )

    if (alpha <= 0.01f && !dragging) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = top_padding, bottom = bottom_padding)
            .width(28.dp),
    ) {
        val track_px = with(density) { maxHeight.toPx() }
        val live_thumb = with(density) {
            val ratio = metrics.ratio.coerceIn(0.02f, 1f)
            max(track_px * ratio, thumb_min_height.toPx()).coerceAtMost(max(track_px, 1f))
        }
        val thumb_px = if (dragging && locked_geometry[0] > 0f) locked_geometry[0] else live_thumb
        val travel = if (dragging && locked_geometry[1] > 0f) {
            locked_geometry[1]
        } else {
            max(track_px - thumb_px, 1f)
        }
        val progress = if (dragging) drag_fraction.floatValue else metrics.progress

        val live_progress by rememberUpdatedState(metrics.progress)
        val live_metrics by rememberUpdatedState(metrics)
        val live_travel by rememberUpdatedState(travel)
        val live_thumb_px by rememberUpdatedState(thumb_px)
        LaunchedEffect(state) {
            snapshotFlow { if (dragging) drag_fraction.floatValue else -1f }
                .collectLatest { fraction ->
                    if (fraction >= 0f) scroll_to_fraction(state, fraction, locked_metrics ?: live_metrics)
                }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            val span = live_travel
                            val thumb = live_thumb_px
                            val current = live_progress
                            val thumb_top = current * span
                            drag_fraction.floatValue = if (start.y >= thumb_top && start.y <= thumb_top + thumb) {
                                current
                            } else {
                                ((start.y - thumb / 2f).coerceIn(0f, span)) / span
                            }
                            locked_metrics = live_metrics
                            locked_geometry[0] = thumb
                            locked_geometry[1] = span
                            dragging = true
                            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            dragging = false
                            locked_metrics = null
                            locked_geometry[0] = 0f
                            locked_geometry[1] = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            locked_metrics = null
                            locked_geometry[0] = 0f
                            locked_geometry[1] = 0f
                        },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            val next = (drag_fraction.floatValue + delta / live_travel).coerceIn(0f, 1f)
                            if (next != drag_fraction.floatValue) drag_fraction.floatValue = next
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (progress * travel).roundToInt()) }
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .width(width.dp)
                    .height(with(density) { thumb_px.toDp() })
                    .alpha(if (dragging) 1f else alpha)
                    .background(
                        color = if (dragging) colors.accent_blue else colors.text_muted.copy(alpha = 0.55f),
                        shape = SquircleShape(999.dp),
                    ),
            )
        }
    }
}

private data class scroll_metrics(
    val total: Int,
    val span: Float,
    val ratio: Float,
    val progress: Float,
)

private suspend fun scroll_to_fraction(
    state: LazyListState,
    fraction: Float,
    metrics: scroll_metrics,
) {
    if (metrics.span <= 0f || metrics.total <= 0) return
    val target = fraction.coerceIn(0f, 1f) * metrics.span
    val index = target.roundToInt().coerceIn(0, max(metrics.total - 1, 0))
    state.scrollToItem(index)
}
