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
import org.junit.Assert.assertNull
import org.junit.Test

class fast_scroll_test {
    private val delta = 0.0001f

    @Test
    fun fraction_is_thumb_top_over_travel() {
        assertEquals(0f, fast_scroll_thumb_fraction(0f, 1000f, 100f), delta)
        assertEquals(0.5f, fast_scroll_thumb_fraction(450f, 1000f, 100f), delta)
        assertEquals(1f, fast_scroll_thumb_fraction(900f, 1000f, 100f), delta)
    }

    @Test
    fun fraction_clamps_outside_the_track() {
        assertEquals(0f, fast_scroll_thumb_fraction(-50f, 1000f, 100f), delta)
        assertEquals(1f, fast_scroll_thumb_fraction(5000f, 1000f, 100f), delta)
    }

    @Test
    fun fraction_is_zero_without_travel() {
        assertEquals(0f, fast_scroll_thumb_fraction(10f, 100f, 100f), delta)
        assertEquals(0f, fast_scroll_thumb_fraction(Float.NaN, 1000f, 100f), delta)
    }

    @Test
    fun index_maps_linearly_over_the_count() {
        assertEquals(0, fast_scroll_target_index(0f, 100_000))
        assertEquals(50_000, fast_scroll_target_index(0.5f, 100_001))
        assertEquals(99_999, fast_scroll_target_index(1f, 100_000))
    }

    @Test
    fun index_stops_where_the_last_page_fills_the_screen() {
        assertEquals(12_000 - 9, fast_scroll_target_index(1f, 12_000, visible_items = 9))
        assertEquals(0, fast_scroll_target_index(1f, 5, visible_items = 9))
    }

    @Test
    fun index_handles_empty_and_invalid_input() {
        assertEquals(0, fast_scroll_target_index(0.7f, 0))
        assertEquals(0, fast_scroll_target_index(0.7f, 1))
        assertEquals(0, fast_scroll_target_index(Float.NaN, 500))
        assertEquals(499, fast_scroll_target_index(3f, 500))
        assertEquals(0, fast_scroll_target_index(-3f, 500))
    }

    @Test
    fun index_is_monotonic_in_the_fraction() {
        var last = -1
        for (step in 0..1000) {
            val index = fast_scroll_target_index(step / 1000f, 12_345, visible_items = 8)
            assert(index >= last)
            last = index
        }
    }

    @Test
    fun coalescer_keeps_only_the_latest_target_per_frame() {
        val coalescer = fast_scroll_coalescer()
        coalescer.offer(10)
        coalescer.offer(400)
        coalescer.offer(9_000)
        assertEquals(9_000, coalescer.take())
        assertNull(coalescer.take())
    }

    @Test
    fun coalescer_skips_a_repeat_of_the_applied_target() {
        val coalescer = fast_scroll_coalescer()
        coalescer.offer(42)
        assertEquals(42, coalescer.take())
        coalescer.offer(42)
        assertNull(coalescer.take())
        coalescer.offer(43)
        assertEquals(43, coalescer.take())
    }

    @Test
    fun offset_map_locates_a_fractional_position_inside_a_row() {
        val estimator = lazy_extent_estimator()
        repeat(5) { estimator.record(it, "k$it", 100) }
        val map = estimator.snapshot(total_items = 50_000)
        assertEquals(lazy_position(0, 0), map.locate(0.0))
        assertEquals(lazy_position(3, 40), map.locate(340.0))
        assertEquals(lazy_position(25_000, 50), map.locate(2_500_050.0))
        assertEquals(5_000_000.0, map.content_px(), 0.001)
    }

    @Test
    fun offset_map_uses_measured_rows_and_spacing() {
        val estimator = lazy_extent_estimator()
        estimator.record(0, "header", 300)
        estimator.record(1, "a", 100)
        estimator.record(2, "b", 100)
        val map = estimator.snapshot(total_items = 10, spacing_px = 2)
        assertEquals(302.0, map.offset_of(1), 0.001)
        assertEquals(lazy_position(1, 10), map.locate(312.0))
        assertEquals(lazy_position(9, 0), map.locate(map.offset_of(9)))
    }

    @Test
    fun offset_map_round_trips_every_row_start() {
        val estimator = lazy_extent_estimator()
        listOf(300, 120, 4000, 80, 120).forEachIndexed { i, size -> estimator.record(i * 7, "k$i", size) }
        val map = estimator.snapshot(total_items = 40)
        for (index in 0 until 40) {
            assertEquals(index, map.locate(map.offset_of(index) + 0.25).index)
        }
    }

    @Test
    fun drag_offset_starts_where_the_list_is_and_reaches_both_ends() {
        assertEquals(1_200.0, fast_scroll_drag_offset(300f, 300f, 900f, 1_200.0, 9_000.0), 0.001)
        assertEquals(0.0, fast_scroll_drag_offset(0f, 300f, 900f, 1_200.0, 9_000.0), 0.001)
        assertEquals(9_000.0, fast_scroll_drag_offset(900f, 300f, 900f, 1_200.0, 9_000.0), 0.001)
        assertEquals(600.0, fast_scroll_drag_offset(150f, 300f, 900f, 1_200.0, 9_000.0), 0.001)
    }

    @Test
    fun drag_offset_moves_smoothly_with_every_pixel() {
        var last = -1.0
        for (step in 0..9000) {
            val offset = fast_scroll_drag_offset(step / 10f, 412f, 900f, 51_234.0, 4_000_000.0)
            assert(offset >= last)
            assert(offset - last < 4_000_000.0 / 900.0 * 0.2)
            last = offset
        }
    }

    @Test
    fun target_px_flags_the_bottom_so_the_last_row_is_reached() {
        assertEquals(fast_scroll_end_target, fast_scroll_target_px(9_000.0, 9_000.0))
        assertEquals(4_500, fast_scroll_target_px(4_500.2, 9_000.0))
        assertEquals(0, fast_scroll_target_px(-5.0, 9_000.0))
    }

    @Test
    fun coalescer_is_empty_before_an_offer_and_after_reset() {
        val coalescer = fast_scroll_coalescer()
        assertNull(coalescer.take())
        coalescer.offer(-1)
        assertNull(coalescer.take())
        coalescer.offer(7)
        coalescer.reset()
        assertNull(coalescer.take())
        coalescer.offer(7)
        assertEquals(7, coalescer.take())
    }
}
