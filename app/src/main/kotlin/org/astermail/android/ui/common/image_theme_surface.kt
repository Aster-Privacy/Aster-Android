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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.SquircleShape

fun glass_page_wash(colors: AsterSemanticColors): Float = 0f

fun Modifier.page_surface(colors: AsterSemanticColors): Modifier = when {
    !colors.is_glass -> this.background(colors.bg_primary)
    glass_page_wash(colors) <= 0.01f -> this
    else -> this.background(colors.bg_primary.copy(alpha = glass_page_wash(colors)))
}

fun Modifier.chrome_surface(colors: AsterSemanticColors): Modifier =
    if (colors.is_glass) this else this.background(colors.bg_primary)

fun chrome_fill(colors: AsterSemanticColors): Color =
    if (colors.is_glass) colors.glass_surface(colors.bg_card) else colors.bg_primary

fun Modifier.image_theme_panel(
    colors: AsterSemanticColors,
    horizontal: Dp = 20.dp,
    vertical: Dp = 16.dp,
    radius: Dp = 16.dp,
): Modifier = if (colors.is_glass) {
    this
        .clip(SquircleShape(radius))
        .background(colors.glass_surface(colors.bg_card))
        .padding(horizontal = horizontal, vertical = vertical)
} else {
    this
}
