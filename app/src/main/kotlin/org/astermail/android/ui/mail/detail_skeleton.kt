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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_state

private const val detail_body_line_start_index = 2

@Composable
fun detail_skeleton(modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val state = shimmer_state()
    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .padding(AsterSpacing.lg),
    ) {
        Box(
            modifier = Modifier
                .skeleton_reveal(0)
                .fillMaxWidth(0.6f)
                .height(20.dp)
                .shimmer(state, shape),
        )
        Spacer(Modifier.height(AsterSpacing.lg))

        Row(
            modifier = Modifier.skeleton_reveal(1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shimmer(state, CircleShape, skeleton_sweep_lag),
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(14.dp)
                        .shimmer(state, shape, skeleton_sweep_lag),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .shimmer(state, shape, skeleton_sweep_lag),
                )
            }
        }
        Spacer(Modifier.height(AsterSpacing.xl))

        repeat(6) {
            val index = detail_body_line_start_index + it
            Box(
                modifier = Modifier
                    .skeleton_reveal(index)
                    .fillMaxWidth(if (it == 5) 0.4f else 1f)
                    .height(13.dp)
                    .shimmer(state, shape, index * skeleton_sweep_lag),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun detail_skeleton_overlay(visible: Boolean, modifier: Modifier = Modifier) {
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
        detail_skeleton()
    }
}

@Composable
fun email_body_skeleton(modifier: Modifier = Modifier) {
    val state = shimmer_state()
    val shape = RoundedCornerShape(6.dp)

    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = AsterSpacing.md)) {
        val widths = listOf(1f, 0.95f, 1f, 0.85f, 1f, 0.9f, 1f, 0.7f, 1f, 0.5f)
        widths.forEachIndexed { index, fraction ->
            Box(
                modifier = Modifier
                    .skeleton_reveal(index)
                    .fillMaxWidth(fraction)
                    .height(13.dp)
                    .shimmer(state, shape, index * skeleton_sweep_lag),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
