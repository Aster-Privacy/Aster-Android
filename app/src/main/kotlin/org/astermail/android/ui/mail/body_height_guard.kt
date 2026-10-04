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

private const val MIN_MEASURABLE_WIDTH_DP = 120f

internal class body_height_guard(private val max_capped_remeasures: Int = 2) {
    private var capped_remeasures = 0

    fun should_remeasure_capped(visual_height: Int, max_height: Int): Boolean {
        if (max_height <= 0 || visual_height < max_height) return false
        if (capped_remeasures >= max_capped_remeasures) return false
        capped_remeasures++
        return true
    }
}

internal fun body_width_changed(old_width: Int, new_width: Int): Boolean =
    old_width > 0 && new_width > 0 && old_width != new_width

internal fun body_width_measurable(width_px: Int, density: Float): Boolean =
    width_px >= MIN_MEASURABLE_WIDTH_DP * density.coerceAtLeast(1f)
