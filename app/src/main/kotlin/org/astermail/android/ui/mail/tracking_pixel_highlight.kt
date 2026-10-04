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

package org.astermail.android.ui.mail

internal object TrackingPixelDot {
    const val SIZE_PX = 6.0

    const val FILL = "#059669"

    const val INNER_RING = "rgba(255,255,255,0.9)"

    const val OUTER_RING = "rgba(0,0,0,0.35)"

    const val INNER_RING_PX = 1.0

    const val OUTER_RING_PX = 2.0

    fun box_shadow(): String =
        "0 0 0 ${INNER_RING_PX.toInt()}px $INNER_RING,0 0 0 ${OUTER_RING_PX.toInt()}px $OUTER_RING"

    fun contrast_ratio(foreground: String, background: String): Double? {
        val a = relative_luminance(foreground) ?: return null
        val b = relative_luminance(background) ?: return null
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}

internal data class TrackingPixelHighlight(val list_open: Boolean = false, val drawn: Int? = null) {
    val status_count: Int?
        get() = drawn?.takeIf { list_open && it > 0 }

    fun open(): TrackingPixelHighlight = TrackingPixelHighlight(list_open = true)

    fun close(): TrackingPixelHighlight = TrackingPixelHighlight()

    fun counted(markers: Int): TrackingPixelHighlight = if (list_open) copy(drawn = markers) else this
}
