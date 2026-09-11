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

package org.astermail.android.api.domains

import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class retry_after_header_test {

    @Test
    fun reads_delay_seconds() {
        assertEquals(60L, parse_retry_after_header("60"))
        assertEquals(7L, parse_retry_after_header(" 7 "))
    }

    @Test
    fun rejects_missing_and_garbage_values() {
        assertNull(parse_retry_after_header(null))
        assertNull(parse_retry_after_header(""))
        assertNull(parse_retry_after_header("soon"))
        assertNull(parse_retry_after_header("0"))
        assertNull(parse_retry_after_header("-5"))
        assertNull(parse_retry_after_header("+5"))
        assertNull(parse_retry_after_header("1.5"))
        assertNull(parse_retry_after_header("9999999999999"))
        assertNull(parse_retry_after_header("Fri, 11 Sep 2026 18:15:00 GMT"))
    }

    @Test
    fun header_fills_in_missing_details() {
        val error = with_retry_after(ApiError.RateLimited(code = DOMAIN_SEARCH_RATE_LIMITED_CODE), "45")
        assertEquals("45", error.details[RETRY_AFTER_SECS_KEY])
    }

    @Test
    fun usable_details_win_over_the_header() {
        val error = with_retry_after(
            ApiError.RateLimited(details = mapOf(RETRY_AFTER_SECS_KEY to "30")),
            "90",
        )
        assertEquals("30", error.details[RETRY_AFTER_SECS_KEY])
    }

    @Test
    fun garbage_details_fall_back_to_the_header() {
        val error = with_retry_after(
            ApiError.RateLimited(details = mapOf(RETRY_AFTER_SECS_KEY to "soon")),
            "90",
        )
        assertEquals("90", error.details[RETRY_AFTER_SECS_KEY])
    }

    @Test
    fun garbage_header_leaves_the_error_unchanged() {
        val original = ApiError.RateLimited(code = DOMAIN_SEARCH_RATE_LIMITED_CODE)
        assertEquals(original, with_retry_after(original, "later"))
    }
}
