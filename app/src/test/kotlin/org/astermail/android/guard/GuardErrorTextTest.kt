//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.guard

import java.io.IOException
import java.time.Instant
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_LAST_DEVICE_CODE
import org.astermail.android.api.GUARD_PAUSED_CODE
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.GUARD_TOO_MANY_REQUESTS_CODE
import org.astermail.android.api.guard.GuardDeviceInfo
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.ui.guard.guard_removal_strands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardErrorTextTest {
    private val now = Instant.parse("2026-11-23T10:00:00Z")

    @Test
    fun long_pause_shows_the_end_time() {
        val message = guard_error_message_for(ApiError.GuardRetry(GUARD_PAUSED_CODE, 600), now)
        assertEquals(guard_error_message(R.string.guard_error_paused_until, now.plusSeconds(600)), message)
    }

    @Test
    fun short_retry_has_no_time() {
        val message = guard_error_message_for(ApiError.GuardRetry(GUARD_TOO_MANY_REQUESTS_CODE, 5), now)
        assertEquals(guard_error_message(R.string.guard_error_too_many), message)
    }

    @Test
    fun coded_errors_get_specific_text() {
        assertEquals(
            R.string.guard_error_last_device,
            guard_error_message_for(ApiError.ForbiddenError(code = GUARD_LAST_DEVICE_CODE), now)?.res,
        )
        assertEquals(
            R.string.guard_error_request_gone,
            guard_error_message_for(ApiError.Conflict("gone", code = GUARD_REQUEST_GONE_CODE), now)?.res,
        )
        assertNull(guard_error_message_for(ApiError.ForbiddenError(code = "SOMETHING_ELSE"), now))
    }

    @Test
    fun transport_errors_are_explained() {
        assertEquals(R.string.error_no_connection, guard_error_message_for(ApiError.NetworkError, now)?.res)
        assertEquals(R.string.error_no_connection, guard_error_message_for(IOException("reset"), now)?.res)
        assertEquals(R.string.guard_error_server, guard_error_message_for(ApiError.ServerError(503), now)?.res)
    }

    @Test
    fun pending_device_without_a_time_still_explains() {
        val message = guard_error_message_for(ApiError.GuardDevicePending("d1", "not-a-time", "c1"), now)
        assertEquals(guard_error_message(R.string.guard_error_device_pending_no_time), message)
    }

    @Test
    fun removing_the_only_approver_strands_the_account() {
        val one = GuardStatus(switches = 1, devices = listOf(GuardDeviceInfo(id = "a")))
        assertTrue(guard_removal_strands(one, "a"))
        val two = one.copy(devices = listOf(GuardDeviceInfo(id = "a"), GuardDeviceInfo(id = "b")))
        assertFalse(guard_removal_strands(two, "a"))
        assertFalse(guard_removal_strands(one.copy(switches = 0), "a"))
        assertFalse(guard_removal_strands(one, "missing"))
        assertFalse(guard_removal_strands(null, "a"))
    }

    @Test
    fun device_label_joins_maker_and_model() {
        assertEquals("Google Pixel 8", guard_device_label_of("Google", "Pixel 8"))
        assertEquals("Samsung SM-S918B", guard_device_label_of("samsung", "SM-S918B"))
        assertEquals("OnePlus 12", guard_device_label_of("OnePlus", "OnePlus 12"))
        assertEquals("Pixel 8", guard_device_label_of(null, "Pixel 8"))
        assertEquals(GUARD_DEVICE_LABEL_FALLBACK, guard_device_label_of(" ", null))
        assertEquals(GUARD_DEVICE_LABEL_FALLBACK, guard_device_label_of("unknown", "Unknown"))
        assertTrue(guard_device_label_of("Maker", "x".repeat(200)).length <= GUARD_DEVICE_LABEL_MAX)
    }
}
