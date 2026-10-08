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

package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarPipelineTest {

    @Test
    fun sample_size_keeps_shortest_side_at_or_above_target() {
        assertEquals(4, avatar_sample_size(4000, 3000))
        assertEquals(2, avatar_sample_size(1080, 2340))
        assertEquals(1, avatar_sample_size(512, 512))
        assertEquals(1, avatar_sample_size(100, 80))
        assertEquals(1, avatar_sample_size(0, 10))
    }

    @Test
    fun decode_size_scales_shortest_side_to_target() {
        assertArrayEquals(intArrayOf(1024, 512), avatar_decode_size(2048, 1024))
        assertArrayEquals(intArrayOf(512, 1024), avatar_decode_size(1024, 2048))
    }

    @Test
    fun decode_size_never_upscales() {
        assertArrayEquals(intArrayOf(300, 200), avatar_decode_size(300, 200))
        assertArrayEquals(intArrayOf(512, 900), avatar_decode_size(512, 900))
    }

    @Test
    fun square_crop_is_centered() {
        assertArrayEquals(intArrayOf(0, 630, 1080), avatar_square_crop(1080, 2340))
        assertArrayEquals(intArrayOf(50, 0, 300), avatar_square_crop(400, 300))
        assertArrayEquals(intArrayOf(0, 0, 256), avatar_square_crop(256, 256))
    }

    @Test
    fun output_size_is_capped_and_never_upscaled() {
        assertEquals(AVATAR_OUTPUT_SIZE, avatar_output_size(1080))
        assertEquals(300, avatar_output_size(300))
        assertEquals(1, avatar_output_size(0))
    }

    @Test
    fun quality_steps_down_to_floor() {
        val steps = generateSequence(AVATAR_QUALITY) { avatar_next_quality(it) }.toList()
        assertEquals(listOf(88, 78, 68, 58, 48), steps)
        assertNull(avatar_next_quality(AVATAR_MIN_QUALITY))
    }
}
