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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.mail.alias_direction_all
import org.astermail.android.mail.alias_direction_received
import org.astermail.android.mail.alias_direction_sent
import org.astermail.android.ui.theme.local_accessibility

const val alias_direction_switcher_tag = "alias_direction"

private data class alias_direction_segment(
    val id: String,
    val label_res: Int,
)

@Composable
internal fun alias_direction_switcher(
    value: String,
    on_change: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val reduce_motion = local_accessibility.current.reduce_motion
    val haptics = LocalHapticFeedback.current
    val segments = listOf(
        alias_direction_segment(alias_direction_all, R.string.alias_direction_all),
        alias_direction_segment(alias_direction_received, R.string.alias_direction_received),
        alias_direction_segment(alias_direction_sent, R.string.alias_direction_sent),
    )
    val selected_index = segments.indexOfFirst { it.id == value }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .selectableGroup(),
    ) {
        val segment_width = maxWidth / segments.size
        val indicator_offset by animateDpAsState(
            targetValue = segment_width * selected_index,
            animationSpec = if (reduce_motion) {
                snap()
            } else {
                spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
            },
            label = "alias_direction_indicator",
        )
        Row(modifier = Modifier.fillMaxSize()) {
            segments.forEachIndexed { index, segment ->
                val active = index == selected_index
                val content_color by animateColorAsState(
                    targetValue = if (active) colors.accent_blue else colors.text_secondary,
                    label = "alias_direction_content",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = {
                                if (!active) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    on_change(segment.id)
                                }
                            },
                        )
                        .padding(horizontal = 8.dp)
                        .testTag("alias_direction_" + segment.id),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(segment.label_res),
                        color = content_color,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_secondary),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(indicator_offset.roundToPx(), 0) }
                .width(segment_width)
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(colors.accent_blue),
        )
    }
}
