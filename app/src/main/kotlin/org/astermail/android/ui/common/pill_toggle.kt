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

package org.astermail.android.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
import org.astermail.android.design.field_surface_color

private val pill_toggle_height = 40.dp
private val pill_toggle_shape = AsterShapes.control

@Composable
fun pill_toggle(
    labels: List<String>,
    selected_index: Int,
    on_select: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val active_index = selected_index.coerceIn(0, (labels.size - 1).coerceAtLeast(0))

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(pill_toggle_height)
            .acrylic(colors, pill_toggle_shape, field_surface_color(colors))
            .padding(3.dp),
    ) {
        val pill_width = ((maxWidth - 6.dp) / labels.size.coerceAtLeast(1)).coerceAtLeast(0.dp)
        val pill_offset by animateDpAsState(
            targetValue = pill_width * active_index,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "pill_toggle_offset",
        )

        Box(
            modifier = Modifier
                .offset(x = pill_offset)
                .width(pill_width)
                .fillMaxHeight()
                .background(colors.accent_blue, SquircleShape(13.dp)),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val active = index == active_index
                val label_color by animateColorAsState(
                    targetValue = if (active) colors.on_accent else colors.text_muted,
                    animationSpec = tween(durationMillis = AsterDuration.short_4, easing = AsterEasing.standard_enter),
                    label = "pill_toggle_label_color",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.foundation.LocalIndication.current,
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { on_select(index) },
                        )
                        .semantics { selected = active }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = label_color,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
