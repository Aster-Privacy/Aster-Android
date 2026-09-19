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
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_appearance
import org.astermail.android.design.components.shimmer_line
import org.astermail.android.design.components.shimmer_state

const val inbox_skeleton_tag = "inbox_skeleton"
const val inbox_skeleton_row_tag = "inbox_skeleton_row"

internal const val skeleton_sweep_lag = 0.06f
internal const val skeleton_defer_ms = 150L
internal const val skeleton_min_visible_ms = 300L
internal const val skeleton_fade_out_ms = 150
internal const val skeleton_handoff_ms = 150
internal const val skeleton_await_rows_ms = 120L

internal fun skeleton_visible_after(has_data: Boolean, pending: Boolean, pending_for_ms: Long): Boolean =
    !has_data && pending && pending_for_ms >= skeleton_defer_ms

enum class SkeletonPhase { blank, skeleton, content }

data class SkeletonStep(val target: SkeletonPhase, val after_ms: Long)

internal fun initial_skeleton_phase(wanted: Boolean, rows_imminent: Boolean): SkeletonPhase = when {
    !wanted -> SkeletonPhase.content
    rows_imminent -> SkeletonPhase.blank
    else -> SkeletonPhase.skeleton
}

internal fun plan_skeleton_step(
    current: SkeletonPhase,
    wanted: Boolean,
    rows_imminent: Boolean,
    skeleton_shown_for_ms: Long,
): SkeletonStep = when {
    wanted && current == SkeletonPhase.skeleton -> SkeletonStep(SkeletonPhase.skeleton, 0L)
    wanted && (rows_imminent || current == SkeletonPhase.content) ->
        SkeletonStep(SkeletonPhase.skeleton, skeleton_await_rows_ms)
    wanted -> SkeletonStep(SkeletonPhase.skeleton, 0L)
    current == SkeletonPhase.skeleton ->
        SkeletonStep(SkeletonPhase.content, (skeleton_min_visible_ms - skeleton_shown_for_ms).coerceAtLeast(0L))
    else -> SkeletonStep(SkeletonPhase.content, 0L)
}

@Composable
fun remember_skeleton_phase(wanted: Boolean, rows_imminent: Boolean): State<SkeletonPhase> {
    val phase = remember { mutableStateOf(initial_skeleton_phase(wanted, rows_imminent)) }
    val shown_at = remember {
        longArrayOf(if (phase.value == SkeletonPhase.skeleton) android.os.SystemClock.uptimeMillis() else 0L)
    }
    LaunchedEffect(wanted, rows_imminent) {
        val shown_for = if (phase.value == SkeletonPhase.skeleton) {
            android.os.SystemClock.uptimeMillis() - shown_at[0]
        } else {
            0L
        }
        val step = plan_skeleton_step(phase.value, wanted, rows_imminent, shown_for)
        if (step.after_ms > 0L) delay(step.after_ms)
        if (phase.value == step.target) return@LaunchedEffect
        if (step.target == SkeletonPhase.skeleton) shown_at[0] = android.os.SystemClock.uptimeMillis()
        phase.value = step.target
    }
    return phase
}

@Composable
fun Modifier.skeleton_handoff(phase: SkeletonPhase): Modifier {
    val reduce_motion = aster_reduce_motion()
    val alpha = remember { Animatable(if (phase == SkeletonPhase.skeleton) 0f else 1f) }
    LaunchedEffect(phase, reduce_motion) {
        when (phase) {
            SkeletonPhase.skeleton -> alpha.snapTo(0f)
            SkeletonPhase.blank -> Unit
            SkeletonPhase.content -> if (alpha.value < 1f) {
                if (reduce_motion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(skeleton_handoff_ms))
            }
        }
    }
    return this.graphicsLayer { this.alpha = alpha.value }
}

@Composable
fun inbox_skeleton(
    modifier: Modifier = Modifier,
    list_density: String? = null,
    row_count: Int = 10,
    show_avatar: Boolean = true,
    show_preview: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val state = shimmer_state()
    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
            .background(colors.bg_primary)
            .testTag(inbox_skeleton_tag),
    ) {
        repeat(row_count) { index ->
            inbox_skeleton_row(
                state = state,
                list_density = list_density,
                is_first = index == 0,
                is_last = index == row_count - 1,
                show_avatar = show_avatar,
                show_preview = show_preview,
            )
        }
    }
}

@Composable
fun inbox_skeleton_layer(
    phase: SkeletonPhase,
    modifier: Modifier = Modifier,
    list_density: String? = null,
    show_avatar: Boolean = true,
    show_preview: Boolean = true,
) {
    val reduce_motion = aster_reduce_motion()
    AnimatedVisibility(
        visible = phase == SkeletonPhase.skeleton,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reduce_motion) ExitTransition.None else fadeOut(tween(skeleton_fade_out_ms)),
    ) {
        val loading_label = stringResource(R.string.loading)
        inbox_skeleton(
            list_density = list_density,
            show_avatar = show_avatar,
            show_preview = show_preview,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = loading_label
            },
        )
    }
}

@Composable
private fun skeleton_text_line(
    style: TextStyle,
    state: shimmer_appearance,
    modifier: Modifier = Modifier,
) {
    Text(
        text = " ",
        style = style,
        maxLines = 1,
        modifier = modifier.shimmer_line(state),
    )
}

@Composable
fun inbox_skeleton_row(
    state: shimmer_appearance = shimmer_state(),
    list_density: String? = null,
    is_first: Boolean = false,
    is_last: Boolean = true,
    show_avatar: Boolean = true,
    show_preview: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val metrics = remember(list_density) { inbox_row_metrics(list_density) }
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val card_color = remember(colors) { inbox_card_read_color(colors) }
    val sender_style = inbox_sender_text_style()
    val time_style = inbox_time_text_style()
    val subject_style = inbox_subject_text_style()
    val preview_style = inbox_preview_text_style()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(inbox_skeleton_row_tag)
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(shape)
            .background(card_color),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
                .defaultMinSize(minHeight = metrics.min_height)
                .padding(
                    start = inbox_card_content_padding,
                    end = inbox_card_content_padding,
                    top = metrics.vertical_padding,
                    bottom = metrics.vertical_padding,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            if (show_avatar) {
                Box(
                    modifier = Modifier
                        .size(metrics.avatar_size)
                        .shimmer(state, CircleShape),
                )
                Spacer(Modifier.width(AsterSpacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        skeleton_text_line(sender_style, state, Modifier.fillMaxWidth(0.42f))
                    }
                    skeleton_text_line(
                        time_style,
                        state,
                        Modifier
                            .padding(start = AsterSpacing.sm)
                            .width(36.dp),
                    )
                }
                Spacer(Modifier.height(metrics.line_gap))
                if (show_preview) {
                    skeleton_text_line(subject_style, state, Modifier.fillMaxWidth(0.68f))
                    Spacer(Modifier.height(metrics.line_gap))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (show_preview) {
                            skeleton_text_line(preview_style, state, Modifier.fillMaxWidth(0.9f))
                        } else {
                            skeleton_text_line(subject_style, state, Modifier.fillMaxWidth(0.68f))
                        }
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Box(modifier = Modifier.size(inbox_star_slot_size))
                }
            }
        }
    }
}
