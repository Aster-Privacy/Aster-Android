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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.shimmer_brush
import org.astermail.android.ui.theme.local_accessibility

@Composable
fun inbox_skeleton(
    modifier: Modifier = Modifier,
    list_density: String? = null,
    row_count: Int = 10,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        items(row_count) { index ->
            inbox_skeleton_row(
                list_density = list_density,
                is_first = index == 0,
                is_last = index == row_count - 1,
            )
        }
    }
}

@Composable
fun inbox_skeleton_row(
    list_density: String? = null,
    is_first: Boolean = false,
    is_last: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val reduce_motion = local_accessibility.current.reduce_motion
    val brush = shimmer_brush(animated = !reduce_motion)
    val metrics = remember(list_density) { inbox_row_metrics(list_density) }
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val card_color = remember(colors) { inbox_card_read_color(colors) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                .clip(CircleShape)
                .background(brush),
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
                )
            }
            Spacer(Modifier.height(metrics.line_gap + 4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush),
            )
            Spacer(Modifier.height(metrics.line_gap + 3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush),
            )
        }
    }
}
