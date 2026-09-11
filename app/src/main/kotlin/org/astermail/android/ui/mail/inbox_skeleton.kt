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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_appearance
import org.astermail.android.design.components.shimmer_state

const val inbox_skeleton_tag = "inbox_skeleton"
const val inbox_skeleton_row_tag = "inbox_skeleton_row"

internal const val skeleton_reveal_step_ms = 35
internal const val skeleton_reveal_duration_ms = 200
internal const val skeleton_reveal_cap = 12
internal const val skeleton_sweep_lag = 0.06f
internal const val skeleton_defer_ms = 150L
internal const val skeleton_min_visible_ms = 450L
internal const val skeleton_fade_out_ms = 200
internal val skeleton_reveal_rise = 6.dp

@Composable
internal fun Modifier.skeleton_reveal(index: Int, enabled: Boolean = true): Modifier {
    val reduce_motion = aster_reduce_motion()
    if (!enabled || reduce_motion) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(skeleton_reveal_cap) * skeleton_reveal_step_ms).toLong())
        progress.animateTo(1f, tween(skeleton_reveal_duration_ms))
    }
    val rise_px = with(LocalDensity.current) { skeleton_reveal_rise.toPx() }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise_px
    }
}

@Composable
fun inbox_skeleton(
    modifier: Modifier = Modifier,
    list_density: String? = null,
    row_count: Int = 10,
    reveal: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val state = shimmer_state()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .testTag(inbox_skeleton_tag),
        userScrollEnabled = false,
    ) {
        items(row_count) { index ->
            inbox_skeleton_row(
                state = state,
                list_density = list_density,
                is_first = index == 0,
                is_last = index == row_count - 1,
                reveal_index = if (reveal) index else null,
            )
        }
    }
}

@Composable
fun inbox_skeleton_overlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    list_density: String? = null,
) {
    var shown by remember { mutableStateOf(false) }
    var shown_at by remember { mutableLongStateOf(0L) }
    LaunchedEffect(visible) {
        if (visible) {
            if (shown) return@LaunchedEffect
            delay(skeleton_defer_ms)
            shown_at = android.os.SystemClock.uptimeMillis()
            shown = true
        } else if (shown) {
            val elapsed = android.os.SystemClock.uptimeMillis() - shown_at
            if (elapsed < skeleton_min_visible_ms) delay(skeleton_min_visible_ms - elapsed)
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = fadeOut(tween(skeleton_fade_out_ms)),
    ) {
        inbox_skeleton(list_density = list_density)
    }
}

@Composable
fun inbox_skeleton_row(
    state: shimmer_appearance = shimmer_state(),
    list_density: String? = null,
    is_first: Boolean = false,
    is_last: Boolean = true,
    reveal_index: Int? = null,
) {
    val colors = AsterMaterial.colors
    val metrics = remember(list_density) { inbox_row_metrics(list_density) }
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val card_color = remember(colors) { inbox_card_read_color(colors) }
    val phase_shift = (reveal_index ?: 0) * skeleton_sweep_lag
    val line_shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(inbox_skeleton_row_tag)
            .skeleton_reveal(index = reveal_index ?: 0, enabled = reveal_index != null)
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(shape)
            .background(card_color)
            .defaultMinSize(minHeight = metrics.min_height)
            .padding(
                start = inbox_card_content_padding,
                end = inbox_card_content_padding,
                top = metrics.vertical_padding,
                bottom = metrics.vertical_padding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(metrics.avatar_size)
                .shimmer(state, CircleShape, phase_shift),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .shimmer(state, line_shape, phase_shift),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .shimmer(state, line_shape, phase_shift),
                )
            }
            Spacer(Modifier.height(metrics.line_gap + 4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(13.dp)
                    .shimmer(state, line_shape, phase_shift),
            )
            Spacer(Modifier.height(metrics.line_gap + 3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(12.dp)
                    .shimmer(state, line_shape, phase_shift),
            )
        }
    }
}
