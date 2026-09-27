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
    fun phase_wraps_every_period() {
        assertEquals(0f, shimmer_phase_at(0L), 0f)
        assertEquals(0.5f, shimmer_phase_at(800L), 1e-6f)
        assertEquals(0f, shimmer_phase_at(1600L), 0f)
        assertEquals(0.25f, shimmer_phase_at(1600L * 7 + 400L), 1e-6f)
    }

    @Test
    fun band_sweeps_from_fully_left_to_fully_right_of_the_screen() {
        val sweep = 1000f
        val band = sweep * 0.6f
        assertEquals(-band, shimmer_band_left(0f, 0f, sweep, 0f), 1e-3f)
        assertEquals(sweep, shimmer_band_left(0.999999f, 0f, sweep, 0f), 1f)
    }

    @Test
    fun band_position_is_relative_to_the_bone_origin() {
        val on_screen = shimmer_band_left(0.4f, 0f, 1000f, 0f)
        assertEquals(on_screen - 250f, shimmer_band_left(0.4f, 0f, 1000f, 250f), 1e-3f)
    }

    @Test
    fun phase_shift_wraps_negative_phases() {
        assertEquals(
            shimmer_band_left(0.9f, 0f, 1000f, 0f),
            shimmer_band_left(0.1f, 0.2f, 1000f, 0f),
            1e-3f,
        )
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
