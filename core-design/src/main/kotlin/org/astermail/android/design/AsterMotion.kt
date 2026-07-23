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

object AsterEasing {
    val standard_in_out = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val standard_enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val standard_exit = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val emphasized_in_out = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val emphasized_enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val emphasized_exit = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val tap_down = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val tap_up = CubicBezierEasing(0f, 0f, 0.58f, 1f)
}

object AsterDuration {
    const val short_1 = 50
    const val short_2 = 100
    const val short_3 = 120
    const val short_4 = 180
    const val medium_1 = 200
    const val medium_2 = 250
    const val medium_3 = 300
    const val medium_4 = 350
    const val long_1 = 400
    const val long_2 = 450

    const val emphasized_enter = 400
    const val emphasized_exit = 250
    const val emphasized_enter_delay = 60
    const val pop_exit = 180
    const val pop_enter = 250

    const val tap_down = 120
    const val tap_up = 180
}
