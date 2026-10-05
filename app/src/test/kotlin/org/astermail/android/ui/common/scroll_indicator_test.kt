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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class scroll_indicator_test {
    private val delta = 0.01f

    private fun thumb(content: Float, offset: Float, min_thumb: Float = 32f) = scroll_thumb_geometry(
        viewport_px = 1000f,
        content_px = content,
        offset_px = offset,
        track_px = 1000f,
        min_thumb_px = min_thumb,
    )

    @Test
    fun thumb_height_is_the_visible_fraction() {
        assertEquals(250f, thumb(4000f, 0f)!!.height, delta)
        assertEquals(500f, thumb(2000f, 0f)!!.height, delta)
    }

    @Test
    fun thumb_top_follows_the_offset() {
        assertEquals(0f, thumb(4000f, 0f)!!.top, delta)
        assertEquals(375f, thumb(4000f, 1500f)!!.top, delta)
        assertEquals(750f, thumb(4000f, 3000f)!!.top, delta)
    }

    @Test
    fun thumb_stays_inside_the_track_when_offset_overshoots() {
        assertEquals(0f, thumb(4000f, -200f)!!.top, delta)
        assertEquals(750f, thumb(4000f, 9000f)!!.top, delta)
    }

    @Test
    fun very_long_content_keeps_the_minimum_thumb() {
        val t = thumb(1_000_000f, 999_000f)!!
        assertEquals(32f, t.height, delta)
        assertEquals(968f, t.top, delta)
    }

    @Test
    fun minimum_thumb_never_exceeds_the_track() {
        val t = scroll_thumb_geometry(1000f, 100_000f, 0f, track_px = 20f, min_thumb_px = 32f)!!
        assertEquals(20f, t.height, delta)
        assertEquals(0f, t.top, delta)
    }

    @Test
    fun content_that_fits_shows_no_thumb() {
        assertNull(thumb(1000f, 0f))
        assertNull(thumb(600f, 0f))
        assertNull(scroll_thumb_geometry(0f, 4000f, 0f, 1000f, 32f))
        assertNull(scroll_thumb_geometry(1000f, 4000f, 0f, 0f, 32f))
    }

    @Test
    fun thumb_sits_on_the_trailing_edge() {
        assertEquals(393f, scroll_thumb_left(400f, 4f, 3f, rtl = false), delta)
        assertEquals(3f, scroll_thumb_left(400f, 4f, 3f, rtl = true), delta)
    }

    @Test
    fun estimator_sums_measured_items() {
        val estimator = lazy_extent_estimator()
        estimator.record(0, "header", 300)
        estimator.record(1, "a", 2000)
        estimator.record(2, "spacer", 150)
        val extent = estimator.extent(total_items = 3, first_index = 1, first_scroll_px = 500)
        assertEquals(2450f, extent.content_px, delta)
        assertEquals(800f, extent.offset_px, delta)
    }

    @Test
    fun estimator_fills_unseen_items_with_the_typical_size_not_the_tallest() {
        val estimator = lazy_extent_estimator()
        estimator.record(5, "m5", 120)
        estimator.record(6, "m6", 120)
        estimator.record(7, "m7", 4000)
        val extent = estimator.extent(total_items = 8, first_index = 5, first_scroll_px = 40)
        assertEquals(5 * 120f + 120f + 120f + 4000f, extent.content_px, delta)
        assertEquals(5 * 120f + 40f, extent.offset_px, delta)
    }

    @Test
    fun estimator_is_stable_while_scrolling_through_measured_items() {
        val estimator = lazy_extent_estimator()
        listOf(300, 120, 120, 3000, 150).forEachIndexed { i, size -> estimator.record(i, "k$i", size) }
        val before = estimator.extent(total_items = 5, first_index = 3, first_scroll_px = 0)
        estimator.record(3, "k3", 3000)
        estimator.record(4, "k4", 150)
        val after = estimator.extent(total_items = 5, first_index = 3, first_scroll_px = 900)
        assertEquals(before.content_px, after.content_px, delta)
        assertEquals(before.offset_px + 900f, after.offset_px, delta)
    }

    @Test
    fun estimator_forgets_sizes_when_items_shift() {
        val estimator = lazy_extent_estimator()
        estimator.record(0, "header", 300)
        estimator.record(1, "a", 5000)
        estimator.record(1, "b", 100)
        val extent = estimator.extent(total_items = 2, first_index = 0, first_scroll_px = 0)
        assertEquals(200f, extent.content_px, delta)
    }

    @Test
    fun pinned_extent_reaches_the_ends() {
        val estimate = lazy_extent(content_px = 5000f, offset_px = 4200f)
        val at_end = pinned_lazy_extent(estimate, 1000f, can_scroll_backward = true, can_scroll_forward = false)
        assertEquals(at_end.content_px - 1000f, at_end.offset_px, delta)
        val at_top = pinned_lazy_extent(estimate, 1000f, can_scroll_backward = false, can_scroll_forward = true)
        assertEquals(0f, at_top.offset_px, delta)
        val fits = pinned_lazy_extent(estimate, 1000f, can_scroll_backward = false, can_scroll_forward = false)
        assertNull(scroll_thumb_geometry(1000f, fits.content_px, fits.offset_px, 1000f, 32f))
    }

    @Test
    fun pinned_extent_keeps_a_scrollable_list_visible_even_when_underestimated() {
        val estimate = lazy_extent(content_px = 900f, offset_px = 300f)
        val pinned = pinned_lazy_extent(estimate, 1000f, can_scroll_backward = true, can_scroll_forward = true)
        assertTrue(pinned.content_px > 1000f)
        assertNotNull(scroll_thumb_geometry(1000f, pinned.content_px, pinned.offset_px, 1000f, 32f))
    }

    @Test
    fun horizontal_track_leaves_the_vertical_thumb_corner_free() {
        val ltr = horizontal_track_bounds(1000f, corner_px = 14f, track_inset_px = 8f, rtl = false)
        assertEquals(8f, ltr.start, delta)
        assertEquals(978f, ltr.end, delta)
        val rtl = horizontal_track_bounds(1000f, corner_px = 14f, track_inset_px = 8f, rtl = true)
        assertEquals(22f, rtl.start, delta)
        assertEquals(992f, rtl.end, delta)
    }

    @Test
    fun a_zoomed_page_gets_a_horizontal_thumb_and_a_fitted_one_does_not() {
        val track = horizontal_track_bounds(1000f, corner_px = 14f, track_inset_px = 8f, rtl = false)
        val length = track.end - track.start
        val zoomed = scroll_thumb_geometry(1000f, 2500f, 750f, length, 32f)!!
        assertEquals(length * 0.4f, zoomed.height, delta)
        assertEquals((length - zoomed.height) * 0.5f, zoomed.top, delta)
        assertNull(scroll_thumb_geometry(1000f, 1000f, 0f, length, 32f))
    }

    @Test
    fun pan_signal_counts_only_real_moves() {
        val signal = horizontal_pan_signal()
        signal.report(0, 1080, 2700)
        signal.report(0, 1080, 2700)
        assertEquals(1, signal.moves)
        signal.report(540, 1080, 2700)
        assertEquals(2, signal.moves)
        assertEquals(540f, signal.offset_px, delta)
        assertEquals(1080f, signal.extent_px, delta)
        assertEquals(2700f, signal.range_px, delta)
    }
}
