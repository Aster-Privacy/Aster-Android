// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomableTest {
    private val delta = 0.01f
    private val container = Size(1000f, 2000f)

    private fun step(
        scale: Float,
        offset: Offset = Offset.Zero,
        centroid: Offset = Offset(500f, 1000f),
        pan: Offset = Offset.Zero,
        zoom: Float = 1f,
        content: Size = container,
    ) = zoom_transform(scale, offset, content, container, ZOOM_DEFAULT_MAX_SCALE, centroid, pan, zoom)

    @Test
    fun pinching_at_the_center_keeps_the_image_centered() {
        val result = step(scale = 1f, zoom = 2f)
        assertEquals(2f, result.scale, delta)
        assertEquals(0f, result.offset.x, delta)
        assertEquals(0f, result.offset.y, delta)
    }

    @Test
    fun pinching_at_a_corner_keeps_that_corner_under_the_fingers() {
        val result = step(scale = 1f, centroid = Offset.Zero, zoom = 2f)
        assertEquals(500f, result.offset.x, delta)
        assertEquals(1000f, result.offset.y, delta)
        assertEquals(0f, result.leftover.x, delta)
    }

    @Test
    fun dragging_past_the_edge_reports_the_unused_distance() {
        val result = step(scale = 2f, offset = Offset(500f, 1000f), pan = Offset(100f, -40f))
        assertEquals(500f, result.offset.x, delta)
        assertEquals(960f, result.offset.y, delta)
        assertEquals(100f, result.leftover.x, delta)
        assertEquals(0f, result.leftover.y, delta)
    }

    @Test
    fun an_unzoomed_image_does_not_move() {
        val result = step(scale = 1f, pan = Offset(80f, 60f))
        assertEquals(Offset.Zero, result.offset)
        assertEquals(80f, result.leftover.x, delta)
        assertEquals(60f, result.leftover.y, delta)
    }

    @Test
    fun scale_stays_between_the_limits() {
        assertEquals(ZOOM_DEFAULT_MAX_SCALE, step(scale = ZOOM_DEFAULT_MAX_SCALE, zoom = 2f).scale, delta)
        assertEquals(ZOOM_MIN_SCALE, step(scale = 1.2f, zoom = 0.2f).scale, delta)
    }

    @Test
    fun zooming_out_pulls_the_image_back_inside_the_frame() {
        val result = step(scale = 2f, offset = Offset(500f, 1000f), zoom = 0.5f)
        assertEquals(1f, result.scale, delta)
        assertEquals(0f, result.offset.x, delta)
        assertEquals(0f, result.offset.y, delta)
    }

    @Test
    fun a_short_image_only_pans_sideways() {
        val bounds = zoom_pan_bounds(Size(1000f, 500f), container, 2f)
        assertEquals(500f, bounds.x, delta)
        assertEquals(0f, bounds.y, delta)
    }

    @Test
    fun unknown_content_size_falls_back_to_the_frame() {
        val bounds = zoom_pan_bounds(Size.Zero, container, 3f)
        assertEquals(1000f, bounds.x, delta)
        assertEquals(2000f, bounds.y, delta)
    }

    @Test
    fun fitted_size_follows_the_aspect_ratio() {
        assertEquals(Size(1000f, 500f), zoom_fitted_size(container, 2f))
        assertEquals(Size(500f, 2000f), zoom_fitted_size(container, 0.25f))
        assertEquals(Size.Zero, zoom_fitted_size(container, 0f))
    }
}
