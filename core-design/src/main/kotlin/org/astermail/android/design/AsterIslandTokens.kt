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

package org.astermail.android.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

object AsterIslandTokens {
    const val source_hash = "3eb7c011ff67"
}

object AsterIslandRadius {
    val control = 16.dp
    val field = 16.dp
    val island = 16.dp
    val island_lg = 18.dp
    val floating = 16.dp
    val panel = 12.dp
    val item = 8.dp
    val inner = 6.dp
    val pill = 999.dp
}

object AsterIslandSpacing {
    val island_gap = 8.dp
    val stack_gap = 3.dp
    val section_gap = 24.dp
    val row_min_height = 56.dp
    val row_pad_x = 16.dp
    val row_pad_y = 12.dp
    val floating_pad = 6.dp
}

object AsterIslandMotion {
    val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

data class AsterIslandMix(
    val island_fill: Float,
    val island_hover: Float,
    val island_press: Float,
    val island_divider: Float,
    val hover: Float,
    val selected: Float,
    val field: Float,
    val field_hover: Float,
) {
    companion object {
        val light = AsterIslandMix(
            island_fill = 0.04f,
            island_hover = 0.045f,
            island_press = 0.08f,
            island_divider = 0.07f,
            hover = 0.06f,
            selected = 0.08f,
            field = 0.07f,
            field_hover = 0.11f,
        )
        val dark = AsterIslandMix(
            island_fill = 0.055f,
            island_hover = 0.05f,
            island_press = 0.09f,
            island_divider = 0.07f,
            hover = 0.06f,
            selected = 0.1f,
            field = 0.07f,
            field_hover = 0.11f,
        )
    }
}
