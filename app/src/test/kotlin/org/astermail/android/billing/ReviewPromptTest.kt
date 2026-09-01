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

package org.astermail.android.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `counts sends only until the prompt becomes eligible`() {
        assertTrue(ReviewPrompt.counts_send(done = false, eligible_at = 0L))
        assertFalse(ReviewPrompt.counts_send(done = false, eligible_at = now))
        assertFalse(ReviewPrompt.counts_send(done = true, eligible_at = 0L))
    }

    @Test
    fun `becomes eligible at the required send count`() {
        assertFalse(ReviewPrompt.becomes_eligible(ReviewPrompt.REQUIRED_SENDS - 1))
        assertTrue(ReviewPrompt.becomes_eligible(ReviewPrompt.REQUIRED_SENDS))
    }

    @Test
    fun `waits out the delay before asking`() {
        assertFalse(ReviewPrompt.is_eligible(done = false, eligible_at = now, now_ms = now))
        assertFalse(
            ReviewPrompt.is_eligible(
                done = false,
                eligible_at = now,
                now_ms = now + ReviewPrompt.DELAY_MS - 1L,
            ),
        )
        assertTrue(
            ReviewPrompt.is_eligible(
                done = false,
                eligible_at = now,
                now_ms = now + ReviewPrompt.DELAY_MS,
            ),
        )
    }

    @Test
    fun `never asks again once done`() {
        assertFalse(
            ReviewPrompt.is_eligible(
                done = true,
                eligible_at = now,
                now_ms = now + ReviewPrompt.DELAY_MS * 100L,
            ),
        )
    }

    @Test
    fun `never asks without an eligibility stamp`() {
        assertFalse(ReviewPrompt.is_eligible(done = false, eligible_at = 0L, now_ms = now))
    }
}
