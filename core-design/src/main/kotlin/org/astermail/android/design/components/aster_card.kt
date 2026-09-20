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

package org.astermail.android.design.components

import org.astermail.android.design.SquircleShape

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterMaterial
import androidx.compose.ui.graphics.Color
import org.astermail.android.design.acrylic
import org.astermail.android.design.island_surface_color

@Composable
fun AsterCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(16.dp)
    val acrylic_on = colors.is_glass && org.astermail.android.design.local_acrylic.current != null
    val card_colors = CardDefaults.cardColors(
        containerColor = if (acrylic_on) Color.Transparent else island_surface_color(colors),
        contentColor = colors.text_primary,
    )
    val border: BorderStroke? = null
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.98f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
            label = "card_scale",
        )
        Card(
            onClick = onClick,
            interactionSource = interaction,
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (acrylic_on) Modifier.acrylic(colors, shape) else Modifier),
            shape = shape,
            colors = card_colors,
            border = border,
            elevation = elevation,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.then(if (acrylic_on) Modifier.acrylic(colors, shape) else Modifier),
            shape = shape,
            colors = card_colors,
            border = border,
            elevation = elevation,
            content = content,
        )
    }
}
