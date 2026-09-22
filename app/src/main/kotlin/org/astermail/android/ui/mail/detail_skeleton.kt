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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_appearance
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.ui.common.page_surface

@Composable
private fun detail_skeleton_card(
    state: shimmer_appearance,
    card_color: Color,
    is_first: Boolean,
    is_last: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(shape)
            .acrylic_backdrop(colors)
            .background(card_color),
        content = content,
    )
}

@Composable
private fun detail_skeleton_sender_row(state: shimmer_appearance, shape: Shape, avatar: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_content_padding,
                end = inbox_card_content_padding,
                top = AsterSpacing.md,
                bottom = AsterSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(avatar).shimmer(state, CircleShape))
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.44f)
                    .height(14.dp)
                    .shimmer(state, shape),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(12.dp)
                    .shimmer(state, shape),
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(12.dp)
                .shimmer(state, shape),
        )
    }
}

@Composable
fun detail_skeleton(modifier: Modifier = Modifier, message_count: Int = 1) {
    val colors = AsterMaterial.colors
    val card_color = remember(colors) { inbox_card_read_color(colors) }
    val state = shimmer_state()
    val shape = RoundedCornerShape(6.dp)
    val collapsed = (message_count - 1).coerceIn(0, 3)
    val card_count = collapsed + 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
            .page_surface(colors),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.lg, end = AsterSpacing.xs)
                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(20.dp)
                    .shimmer(state, shape),
            )
        }

        repeat(collapsed) { index ->
            detail_skeleton_card(
                state = state,
                card_color = card_color,
                is_first = index == 0,
                is_last = false,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = inbox_card_content_padding,
                            vertical = AsterSpacing.md,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(36.dp).shimmer(state, CircleShape))
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(13.dp)
                                .shimmer(state, shape),
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(11.dp)
                                .shimmer(state, shape),
                        )
                    }
                }
            }
        }

        detail_skeleton_card(
            state = state,
            card_color = card_color,
            is_first = card_count == 1,
            is_last = true,
        ) {
            detail_skeleton_sender_row(state, shape, 36.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = inbox_card_content_padding)
                    .padding(bottom = AsterSpacing.lg),
            ) {
                val widths = listOf(1f, 0.95f, 1f, 0.85f, 0.92f, 0.5f)
                widths.forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(13.dp)
                            .shimmer(state, shape),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun remember_detail_skeleton_phase(thread_key: String, wanted: Boolean): SkeletonPhase =
    key(thread_key) { remember_skeleton_phase(wanted = wanted, rows_imminent = false).value }

@Composable
fun Modifier.detail_content_handoff(thread_key: String, phase: SkeletonPhase): Modifier =
    key(thread_key) { this.skeleton_handoff(phase) }

@Composable
fun detail_skeleton_layer(phase: SkeletonPhase, modifier: Modifier = Modifier, message_count: Int = 1) {
    val reduce_motion = aster_reduce_motion()
    AnimatedVisibility(
        visible = phase == SkeletonPhase.skeleton,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reduce_motion) ExitTransition.None else fadeOut(tween(skeleton_fade_out_ms)),
    ) {
        detail_skeleton(message_count = message_count)
    }
}

@Composable
fun email_body_skeleton(modifier: Modifier = Modifier) {
    val state = shimmer_state()
    val shape = RoundedCornerShape(6.dp)

    Column(modifier = modifier.graphicsLayer().padding(horizontal = 8.dp, vertical = AsterSpacing.md)) {
        val widths = listOf(1f, 0.95f, 1f, 0.85f, 1f, 0.9f, 1f, 0.7f, 1f, 0.5f)
        widths.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(13.dp)
                    .shimmer(state, shape),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
