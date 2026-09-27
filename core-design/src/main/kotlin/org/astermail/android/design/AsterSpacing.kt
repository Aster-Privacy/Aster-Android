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

import androidx.compose.ui.unit.dp

object AsterSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val row_min_height = AsterIslandSpacing.row_min_height
    val row_pad_x = AsterIslandSpacing.row_pad_x
    val row_pad_y = AsterIslandSpacing.row_pad_y
    val section_gap = AsterIslandSpacing.section_gap
    val island_gap = AsterIslandSpacing.island_gap
    val row_gap = AsterIslandSpacing.stack_gap
}

object AsterRadius {
    val island = AsterIslandRadius.island
    val island_lg = AsterIslandRadius.island_lg
    val control = AsterIslandRadius.control
    val item = AsterIslandRadius.item
    val pill = AsterIslandRadius.pill
    val sm = item
    val md = control
    val lg = control
    val xl = control
    val xxl = island_lg
    val panel = AsterIslandRadius.panel
    val field = AsterIslandRadius.field
}
