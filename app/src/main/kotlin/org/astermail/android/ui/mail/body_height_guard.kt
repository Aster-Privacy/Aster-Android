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

internal class body_growth_guard(
    private val ratio_tolerance: Float = 0.02f,
    private val max_repeats: Int = 2,
) {
    private var last_ratio = 0f
    private var repeats = 0
    private var anchor = 0
    private var locked = false

    fun reset() {
        last_ratio = 0f
        repeats = 0
        anchor = 0
        locked = false
    }

    fun settle(current: Int, reported: Int): Int {
        if (current <= 0 || reported <= current) return reported
        if (locked) return anchor
        val ratio = reported.toFloat() / current.toFloat()
        if (last_ratio > 0f && kotlin.math.abs(ratio - last_ratio) <= ratio_tolerance) {
            repeats++
        } else {
            repeats = 0
            anchor = current
        }
        last_ratio = ratio
        if (repeats >= max_repeats) {
            locked = true
            return anchor
        }
        return reported
    }
}

internal fun body_overflows_sideways(scroll_range: Int, scroll_extent: Int): Boolean =
    scroll_extent > 0 && scroll_range > scroll_extent + 1

internal fun body_height_viewport_filled(
    content_css: Int,
    viewport_css: Int,
    scroll_range: Int,
    scroll_extent: Int,
): Boolean {
    if (content_css <= 0 || viewport_css <= 0) return false
    if (!body_overflows_sideways(scroll_range, scroll_extent)) return false
    val filled = viewport_css.toLong() * scroll_range / scroll_extent
    return content_css <= filled + maxOf(2L, filled / 50L)
}

internal fun body_width_changed(old_width: Int, new_width: Int): Boolean =
    old_width > 0 && new_width > 0 && old_width != new_width

internal fun body_width_measurable(width_px: Int, density: Float): Boolean =
    width_px >= MIN_MEASURABLE_WIDTH_DP * density.coerceAtLeast(1f)
