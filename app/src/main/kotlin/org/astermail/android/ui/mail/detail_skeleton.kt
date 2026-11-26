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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_appearance
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.design.components.skeleton_paragraph
import org.astermail.android.design.components.skeleton_reveal
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.ui.common.page_surface

@Composable
private fun detail_skeleton_card(
    state: shimmer_appearance,
    card_color: Color,
    is_first: Boolean,
    is_last: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(shape)
            .acrylic_backdrop(colors)
            .background(card_color)
            .background(email_glass_backing(colors, white_page = false)),
        content = content,
    )
}

@Immutable
data class detail_skeleton_header(
    val subject: String,
    val sender_name: String? = null,
    val sender_email: String? = null,
)

@Composable
private fun detail_skeleton_sender_row(
    state: shimmer_appearance,
    shape: Shape,
    avatar: Dp,
    header: detail_skeleton_header?,
) {
    val colors = AsterMaterial.colors
    val sender_name = header?.sender_name?.takeIf { it.isNotBlank() }
    val sender_email = header?.sender_email.orEmpty()
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
        if (sender_name != null) {
            SenderAvatar(email = sender_email, name = sender_name, size = avatar)
        } else {
            Box(modifier = Modifier.size(avatar).shimmer(state, CircleShape))
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            if (sender_name != null) {
                Text(
                    text = sender_name,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.44f)
                        .height(14.dp)
                        .shimmer(state, shape),
                )
            }
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
fun detail_skeleton(
    modifier: Modifier = Modifier,
    message_count: Int = 1,
    header: detail_skeleton_header? = null,
) {
    val colors = AsterMaterial.colors
    val card_color = remember(colors) { inbox_card_read_color(colors) }
    val state = shimmer_state()
    val shape = RoundedCornerShape(6.dp)
    val collapsed = (message_count - 1).coerceIn(0, 3)
    val card_count = collapsed + 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
            .page_surface(colors),
    ) {
      Column(
          modifier = Modifier
              .fillMaxSize()
              .navigationBarsPadding()
              .then(if (header == null) Modifier.skeleton_reveal() else Modifier),
      ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.lg, end = AsterSpacing.xs)
                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.md),
        ) {
            val subject = header?.subject?.takeIf { it.isNotBlank() }
            if (subject != null) {
                Text(
                    text = subject,
                    color = colors.text_primary,
                    fontSize = detail_subject_text_size,
                    lineHeight = detail_subject_line_height,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(20.dp)
                        .shimmer(state, shape),
                )
            }
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
            modifier = Modifier.weight(1f, fill = false),
        ) {
            detail_skeleton_sender_row(state, shape, if (header?.sender_name.isNullOrBlank()) 36.dp else 40.dp, header)
            skeleton_paragraph(
                state = state,
                max_lines = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = inbox_card_content_padding)
                    .padding(top = AsterSpacing.xs, bottom = AsterSpacing.lg),
            )
        }
      }
    }
}

internal fun detail_skeleton_phase_of(wanted: Boolean): SkeletonPhase =
    if (wanted) SkeletonPhase.skeleton else SkeletonPhase.content

@Composable
fun remember_detail_skeleton_phase(thread_key: String, wanted: Boolean): SkeletonPhase =
    key(thread_key) { detail_skeleton_phase_of(wanted) }

internal fun detail_content_alpha(revealed: Boolean): Float = if (revealed) 1f else 0f

private const val detail_reveal_fade_ms = 180

@Composable
fun remember_detail_content_alpha(thread_key: String, revealed: Boolean): Animatable<Float, AnimationVector1D> {
    val alpha = remember(thread_key) { Animatable(detail_content_alpha(revealed)) }
    LaunchedEffect(thread_key, revealed) {
        if (revealed) {
            alpha.animateTo(1f, tween(detail_reveal_fade_ms, easing = LinearOutSlowInEasing))
        } else {
            alpha.snapTo(0f)
        }
    }
    return alpha
}

fun Modifier.detail_content_handoff(alpha: () -> Float): Modifier =
    this.graphicsLayer { this.alpha = alpha() }

@Composable
fun detail_skeleton_layer(
    thread_key: String,
    phase: SkeletonPhase,
    modifier: Modifier = Modifier,
    message_count: Int = 1,
    header: detail_skeleton_header? = null,
) {
    val wanted = phase == SkeletonPhase.skeleton
    val alpha = remember(thread_key) { Animatable(if (wanted) 1f else 0f) }
    LaunchedEffect(thread_key, wanted) {
        if (wanted) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(0f, tween(detail_reveal_fade_ms, easing = LinearOutSlowInEasing))
        }
    }
    val visible by remember(alpha) { derivedStateOf { alpha.value > 0f } }
    if (visible) {
        Box(modifier = modifier.graphicsLayer { this.alpha = alpha.value }) {
            detail_skeleton(message_count = message_count, header = header)
        }
    }
}

@Composable
fun email_body_skeleton(modifier: Modifier = Modifier) {
    val state = shimmer_state()
    skeleton_paragraph(
        state = state,
        max_lines = email_body_skeleton_max_lines,
        modifier = modifier
            .skeleton_reveal()
            .padding(horizontal = 8.dp)
            .padding(top = AsterSpacing.md, bottom = AsterSpacing.sm),
    )
}

@Composable
fun deferred_body_skeleton(modifier: Modifier = Modifier) {
    var due by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BODY_SKELETON_DELAY_MS)
        due = true
    }
    if (due) {
        email_body_skeleton(modifier = modifier)
    } else {
        Box(modifier = modifier)
    }
}

private const val email_body_skeleton_max_lines = 30
