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

package org.astermail.android.mail

import kotlinx.coroutines.runBlocking
import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class RateLimitWaitTest {

    @Test
    fun finds_a_wrapped_rate_limit() {
        val limited = ApiError.RateLimited()
        assertSame(limited, rate_limited_cause(IllegalStateException("wrapped", limited)))
        assertNull(rate_limited_cause(ApiError.NetworkError))
        assertNull(rate_limited_cause(null))
    }

    @Test
    fun waits_until_the_hinted_reset() {
        val limited = ApiError.RateLimited(resets_at = "2026-01-01T00:00:30Z")
        val now_ms = java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        assertEquals(30_000L, rate_limit_wait_ms(limited, 1, now_ms))
    }

    @Test
    fun backs_off_without_a_usable_hint() {
        assertEquals(2_000L, rate_limit_wait_ms(ApiError.RateLimited(), 1, 0L))
        assertEquals(8_000L, rate_limit_wait_ms(ApiError.RateLimited(), 3, 0L))
        assertEquals(2_000L, rate_limit_wait_ms(ApiError.RateLimited(resets_at = "soon"), 1, 0L))
        val passed = ApiError.RateLimited(resets_at = "2026-01-01T00:00:00Z")
        val later_ms = java.time.Instant.parse("2026-01-01T00:05:00Z").toEpochMilli()
        assertEquals(4_000L, rate_limit_wait_ms(passed, 2, later_ms))
    }

    @Test
    fun caps_a_long_wait() {
        val limited = ApiError.RateLimited(resets_at = "2026-01-01T01:00:00Z")
        val now_ms = java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        assertEquals(65_000L, rate_limit_wait_ms(limited, 1, now_ms))
        assertEquals(65_000L, rate_limit_wait_ms(ApiError.RateLimited(), 30, 0L))
    }

    @Test
    fun sends_the_request_again_after_waiting() = runBlocking {
        val waits = mutableListOf<Long>()
        var calls = 0
        val result = with_rate_limit_wait(now_ms = { 0L }, wait = { waits.add(it) }) {
            calls++
            if (calls < 3) throw ApiError.RateLimited()
            "done"
        }
        assertEquals("done", result)
        assertEquals(3, calls)
        assertEquals(listOf(2_000L, 4_000L), waits)
    }

    @Test
    fun gives_up_after_the_last_attempt() = runBlocking {
        var calls = 0
        try {
            with_rate_limit_wait<Unit>(attempts = 3, now_ms = { 0L }, wait = {}) {
                calls++
                throw ApiError.RateLimited()
            }
            fail("expected a rate limit error")
        } catch (error: ApiError.RateLimited) {
            assertEquals(3, calls)
        }
    }

    @Test
    fun does_not_repeat_other_failures() = runBlocking {
        var calls = 0
        try {
            with_rate_limit_wait<Unit>(now_ms = { 0L }, wait = {}) {
                calls++
                throw ApiError.NetworkError
            }
            fail("expected a network error")
        } catch (error: ApiError.NetworkError) {
            assertEquals(1, calls)
        }
    }
}
