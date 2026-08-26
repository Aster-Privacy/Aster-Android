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

package org.astermail.android.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InlineImageLimitsTest {

    @Test
    fun accepts_first_small_image() {
        assertNull(
            inline_image_rejection(
                existing_count = 0,
                existing_bytes = 0L,
                new_bytes = 1_000L,
                max_count = 50,
                max_single_bytes = 25_000_000L,
                max_total_bytes = 25_000_000L,
            ),
        )
    }

    @Test
    fun rejects_when_count_is_at_the_limit() {
        assertEquals(
            InlineImageRejection.TOO_MANY,
            inline_image_rejection(
                existing_count = 50,
                existing_bytes = 0L,
                new_bytes = 1L,
                max_count = 50,
                max_single_bytes = 25_000_000L,
                max_total_bytes = 25_000_000L,
            ),
        )
    }

    @Test
    fun rejects_single_image_over_the_per_file_limit() {
        assertEquals(
            InlineImageRejection.TOO_LARGE,
            inline_image_rejection(
                existing_count = 0,
                existing_bytes = 0L,
                new_bytes = 25_000_001L,
                max_count = 50,
                max_single_bytes = 25_000_000L,
                max_total_bytes = 100_000_000L,
            ),
        )
    }

    @Test
    fun rejects_when_the_running_total_would_be_exceeded() {
        assertEquals(
            InlineImageRejection.TOTAL_TOO_LARGE,
            inline_image_rejection(
                existing_count = 3,
                existing_bytes = 24_000_000L,
                new_bytes = 2_000_000L,
                max_count = 50,
                max_single_bytes = 25_000_000L,
                max_total_bytes = 25_000_000L,
            ),
        )
    }

    @Test
    fun accepts_an_image_that_exactly_fills_the_total() {
        assertNull(
            inline_image_rejection(
                existing_count = 1,
                existing_bytes = 20_000_000L,
                new_bytes = 5_000_000L,
                max_count = 50,
                max_single_bytes = 25_000_000L,
                max_total_bytes = 25_000_000L,
            ),
        )
    }

    @Test
    fun counts_regular_attachments_toward_the_inline_budget() {
        assertEquals(
            InlineImageRejection.TOO_MANY,
            inline_image_rejection(
                existing_count = 50,
                existing_bytes = 10L,
                new_bytes = 10L,
                max_count = 50,
                max_single_bytes = 25_000_000L,
                max_total_bytes = 25_000_000L,
            ),
        )
    }
}
