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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

fun island_mix(colors: AsterSemanticColors): AsterIslandMix =
    if (colors.is_dark) AsterIslandMix.dark else AsterIslandMix.light

fun srgb_mix(base: Color, toward: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red + (toward.red - base.red) * t,
        green = base.green + (toward.green - base.green) * t,
        blue = base.blue + (toward.blue - base.blue) * t,
        alpha = base.alpha + (toward.alpha - base.alpha) * t,
    )
}

fun island_surface_color(colors: AsterSemanticColors): Color = when {
    colors.is_glass -> colors.glass_surface(colors.bg_card)
    colors.is_dark -> srgb_mix(colors.bg_primary, Color.White, AsterIslandMix.dark.island_fill)
    else -> srgb_mix(colors.bg_primary, Color.Black, AsterIslandMix.light.island_fill)
}

fun island_hover_color(colors: AsterSemanticColors): Color =
    colors.text_primary.copy(alpha = island_mix(colors).island_hover)

fun island_press_color(colors: AsterSemanticColors): Color =
    colors.text_primary.copy(alpha = island_mix(colors).island_press)

fun island_divider_color(colors: AsterSemanticColors): Color =
    colors.text_primary.copy(alpha = island_mix(colors).island_divider)

fun field_surface_color(colors: AsterSemanticColors): Color =
    srgb_mix(colors.bg_primary, colors.text_primary, island_mix(colors).field)

fun control_surface_color(colors: AsterSemanticColors): Color = when {
    colors.is_glass -> colors.secondary_control_bg.copy(alpha = 1f)
    else -> lerp(colors.bg_primary, colors.text_primary, if (colors.is_dark) 0.16f else 0.06f)
}

fun tonal_surface_color(colors: AsterSemanticColors, tone: Color): Color =
    lerp(island_surface_color(colors), tone, if (colors.is_dark) 0.22f else 0.12f)

fun disabled_surface_color(colors: AsterSemanticColors): Color =
    lerp(colors.bg_primary, colors.text_primary, 0.12f)
