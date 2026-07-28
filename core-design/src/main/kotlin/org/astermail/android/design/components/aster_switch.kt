// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterMaterial

private val track_width = 44.dp
private val track_height = 26.dp
private val thumb_size = 20.dp
private val thumb_inset = 3.dp
private val touch_width = 48.dp
private val touch_height = 32.dp

@Composable
fun aster_switch_off_track(): Color {
    val colors = AsterMaterial.colors
    return if (colors.is_dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.13f)
}

@Composable
fun AsterSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val off_track = aster_switch_off_track()
    val track_color by animateColorAsState(
        targetValue = if (checked) colors.accent_blue else off_track,
        animationSpec = tween(durationMillis = 160),
        label = "aster_switch_track",
    )
    val border_color by animateColorAsState(
        targetValue = if (checked) Color.Transparent else colors.border_primary,
        animationSpec = tween(durationMillis = 160),
        label = "aster_switch_border",
    )
    val thumb_offset by animateDpAsState(
        targetValue = if (checked) track_width - thumb_size - thumb_inset else thumb_inset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "aster_switch_thumb",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(touch_width, touch_height)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interaction,
                        indication = null,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.45f)
                .size(track_width, track_height)
                .clip(CircleShape)
                .background(track_color)
                .border(1.dp, border_color, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumb_offset)
                    .size(thumb_size)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
