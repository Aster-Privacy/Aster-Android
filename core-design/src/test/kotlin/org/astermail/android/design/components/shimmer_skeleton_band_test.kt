//
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.design.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class shimmer_skeleton_band_test {

    @Test
    fun phase_sweeps_then_rests_every_period() {
        assertEquals(0f, shimmer_phase_at(0L), 0f)
        assertEquals(0.5f, shimmer_phase_at(shimmer_sweep_ms / 2), 1e-3f)
        assertEquals(1f, shimmer_phase_at(shimmer_sweep_ms), 0f)
        assertEquals(1f, shimmer_phase_at(shimmer_sweep_ms + shimmer_rest_ms / 2), 0f)
        assertEquals(0f, shimmer_phase_at(shimmer_period_ms), 0f)
        assertEquals(0.5f, shimmer_phase_at(shimmer_period_ms * 7 + shimmer_sweep_ms / 2), 1e-3f)
    }

    @Test
    fun sweep_lasts_between_one_and_a_half_seconds_with_a_short_rest() {
        assertTrue(shimmer_sweep_ms in 1200L..1500L)
        assertTrue(shimmer_rest_ms in 200L..600L)
    }

    @Test
    fun band_sweeps_from_fully_left_to_fully_right_of_the_screen() {
        val sweep = 1000f
        val band = sweep * shimmer_band_fraction
        assertEquals(-band, shimmer_band_left(0f, sweep, 0f), 1e-3f)
        assertEquals(sweep, shimmer_band_left(1f, sweep, 0f), 1e-3f)
    }

    @Test
    fun band_rests_off_screen_between_passes() {
        val sweep = 1000f
        val band = sweep * shimmer_band_fraction
        val resting = shimmer_band_left(shimmer_phase_at(shimmer_sweep_ms + 10L), sweep, 0f)
        assertFalse(shimmer_band_hits(resting, band, 0f, sweep))
    }

    @Test
    fun band_position_is_relative_to_the_bone_origin() {
        val on_screen = shimmer_band_left(0.4f, 1000f, 0f)
        assertEquals(on_screen - 250f, shimmer_band_left(0.4f, 1000f, 250f), 1e-3f)
    }

    @Test
    fun sweep_span_covers_the_tilted_band_across_the_whole_screen() {
        assertEquals(1000f + 2000f * shimmer_tilt, shimmer_sweep_span(1000f, 2000f), 1e-3f)
    }

    @Test
    fun band_gradient_runs_along_the_tilt_and_spans_the_band_width() {
        val end = shimmer_band_end(500f)
        assertEquals(500f, end.x + end.y * shimmer_tilt, 1e-2f)
        assertEquals(shimmer_tilt, end.y / end.x, 1e-4f)
    }

    @Test
    fun band_hits_only_when_it_overlaps_the_bone() {
        assertTrue(shimmer_band_hits(band_left = -100f, band = 600f, left = 0f, right = 200f))
        assertTrue(shimmer_band_hits(band_left = 150f, band = 600f, left = 0f, right = 200f))
        assertFalse(shimmer_band_hits(band_left = -600f, band = 600f, left = 0f, right = 200f))
        assertFalse(shimmer_band_hits(band_left = 200f, band = 600f, left = 0f, right = 200f))
        assertFalse(shimmer_band_hits(band_left = 0f, band = 0f, left = 0f, right = 200f))
    }
}
