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

@Immutable
data class ReactionChipPalette(
    val own_fill: Color,
    val own_text: Color,
    val other_fill: Color,
    val other_text: Color,
)

private val light_reaction_chip_palette = ReactionChipPalette(
    own_fill = Color(0xFFD3E3FD),
    own_text = Color(0xFF0842A0),
    other_fill = Color(0xFFECEEF1),
    other_text = Color(0xFF444746),
)

private val dark_reaction_chip_palette = ReactionChipPalette(
    own_fill = Color(0xFF004A77),
    own_text = Color(0xFFC2E7FF),
    other_fill = Color(0xFF282A2C),
    other_text = Color(0xFFC4C7C5),
)

fun reaction_chip_palette(is_dark: Boolean): ReactionChipPalette =
    if (is_dark) dark_reaction_chip_palette else light_reaction_chip_palette
