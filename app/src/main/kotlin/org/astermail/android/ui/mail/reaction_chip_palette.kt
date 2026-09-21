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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Immutable
data class ReactionChipPalette(
    val own_fill: Color,
    val own_text: Color,
    val other_fill: Color,
    val other_text: Color,
)

private fun solid_mix(base: Color, over: Color, amount: Float): Color =
    lerp(base.copy(alpha = 1f), over.copy(alpha = 1f), amount).copy(alpha = 1f)

fun reaction_chip_palette(
    is_dark: Boolean,
    accent: Color,
    surface: Color,
    text_secondary: Color,
): ReactionChipPalette {
    val lift = if (is_dark) Color.White else Color.Black
    return ReactionChipPalette(
        own_fill = solid_mix(surface, accent, if (is_dark) 0.34f else 0.20f),
        own_text = if (is_dark) {
            solid_mix(accent, Color.White, 0.55f)
        } else {
            solid_mix(accent, Color.Black, 0.38f)
        },
        other_fill = solid_mix(surface, lift, if (is_dark) 0.09f else 0.07f),
        other_text = text_secondary,
    )
}
