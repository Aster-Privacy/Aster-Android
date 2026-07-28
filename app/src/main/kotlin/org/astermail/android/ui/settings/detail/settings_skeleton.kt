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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.shimmer_brush

@Composable
internal fun remember_load_settled(is_loading: Boolean): Boolean {
    var saw_loading by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(is_loading) {
        if (is_loading) saw_loading = true else if (saw_loading) settled = true
    }
    LaunchedEffect(Unit) {
        delay(1500)
        settled = true
    }
    return settled
}

@Composable
internal fun skeleton_block(
    brush: Brush,
    width: Dp,
    height: Dp,
    corner: Dp = 6.dp,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(brush, SquircleShape(corner)),
    )
}

@Composable
internal fun skeleton_block_fill(
    brush: Brush,
    height: Dp,
    corner: Dp = 6.dp,
    fraction: Float = 1f,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .background(brush, SquircleShape(corner)),
    )
}

@Composable
internal fun skeleton_list_row(
    brush: Brush,
    leading_circle: Boolean = false,
    title_fraction: Float = 0.52f,
    subtitle_fraction: Float = 0.34f,
    trailing_width: Dp = 0.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading_circle) {
            Box(modifier = Modifier.size(34.dp).background(brush, CircleShape))
            Spacer(Modifier.width(AsterSpacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            skeleton_block_fill(brush, 13.dp, fraction = title_fraction)
            Spacer(Modifier.height(7.dp))
            skeleton_block_fill(brush, 10.dp, fraction = subtitle_fraction)
        }
        if (trailing_width > 0.dp) {
            Spacer(Modifier.width(AsterSpacing.md))
            skeleton_block(brush, trailing_width, 26.dp, corner = 13.dp)
        }
    }
}

@Composable
internal fun skeleton_card_list(
    rows: Int = 3,
    leading_circle: Boolean = false,
    trailing_width: Dp = 0.dp,
) {
    val brush = shimmer_brush()
    Box(modifier = Modifier.clearAndSetSemantics {}) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            repeat(rows) { idx ->
                skeleton_list_row(
                    brush = brush,
                    leading_circle = leading_circle,
                    title_fraction = if (idx % 2 == 0) 0.55f else 0.42f,
                    subtitle_fraction = if (idx % 2 == 0) 0.33f else 0.4f,
                    trailing_width = trailing_width,
                )
                if (idx < rows - 1) AsterDivider()
            }
        }
    }
}

@Composable
internal fun skeleton_hero_card(lines: Int = 2, bar: Boolean = false) {
    val brush = shimmer_brush()
    Box(modifier = Modifier.clearAndSetSemantics {}) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                skeleton_block_fill(brush, 18.dp, fraction = 0.6f)
                repeat(lines) {
                    Spacer(Modifier.height(9.dp))
                    skeleton_block_fill(brush, 12.dp, fraction = if (it % 2 == 0) 0.45f else 0.72f)
                }
                if (bar) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    skeleton_block_fill(brush, 14.dp, corner = 7.dp)
                }
            }
        }
    }
}

@Composable
internal fun skeleton_section_label() {
    val brush = shimmer_brush()
    Box(
        modifier = Modifier
            .clearAndSetSemantics {}
            .padding(top = AsterSpacing.md, bottom = AsterSpacing.xs),
    ) {
        skeleton_block(brush, 96.dp, 10.dp)
    }
}
