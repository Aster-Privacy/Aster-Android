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

package org.astermail.android.ui.settings.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_appearance
import org.astermail.android.design.components.shimmer_state

private const val pulse_period_ms = 1600
private const val pulse_min_alpha = 0.55f

@Composable
internal fun pulse_skeleton(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(shimmer_appearance) -> Unit,
) {
    val tone = shimmer_state(animated = false)
    val reduce_motion = aster_reduce_motion()
    val transition = rememberInfiniteTransition(label = "pulse_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = pulse_min_alpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulse_period_ms, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    Column(
        modifier = modifier
            .clearAndSetSemantics {}
            .graphicsLayer { alpha = if (reduce_motion) 1f else pulse },
    ) {
        content(tone)
    }
}

@Composable
internal fun pulse_bone(
    tone: shimmer_appearance,
    width: Dp?,
    height: Dp,
    shape: Shape = SquircleShape(6.dp),
    modifier: Modifier = Modifier,
) {
    val sized = if (width == null) modifier.fillMaxWidth() else modifier.width(width)
    Box(modifier = sized.height(height).shimmer(tone, shape))
}

@Composable
internal fun domain_count_pulse_bone() {
    pulse_skeleton { tone ->
        pulse_bone(tone, width = 84.dp, height = 13.dp)
    }
}

@Composable
internal fun domain_cards_pulse_skeleton(rows: Int = 2) {
    val name_widths = listOf(150.dp, 118.dp, 170.dp)
    pulse_skeleton(modifier = Modifier.fillMaxWidth()) { tone ->
        repeat(rows) { index ->
            if (index > 0) Spacer(Modifier.height(AsterSpacing.md))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AsterSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pulse_bone(tone, width = 20.dp, height = 20.dp)
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        pulse_bone(tone, width = name_widths[index % name_widths.size], height = 15.dp)
                        Spacer(Modifier.height(6.dp))
                        pulse_bone(tone, width = 64.dp, height = 18.dp, shape = CircleShape)
                    }
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        pulse_bone(tone, width = 20.dp, height = 20.dp, shape = CircleShape)
                    }
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        pulse_bone(tone, width = 20.dp, height = 20.dp, shape = CircleShape)
                    }
                }
            }
        }
    }
}

@Composable
internal fun domain_results_pulse_skeleton(rows: Int = 5) {
    val name_widths = listOf(150.dp, 190.dp, 230.dp)
    pulse_skeleton(modifier = Modifier.fillMaxWidth()) { tone ->
        repeat(rows) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pulse_bone(tone, width = 16.dp, height = 16.dp, shape = CircleShape)
                Spacer(Modifier.width(AsterSpacing.md))
                Box(modifier = Modifier.weight(1f)) {
                    pulse_bone(tone, width = name_widths[index % name_widths.size], height = 14.dp, shape = CircleShape)
                }
                pulse_bone(tone, width = 64.dp, height = 14.dp, shape = CircleShape)
            }
        }
    }
}
