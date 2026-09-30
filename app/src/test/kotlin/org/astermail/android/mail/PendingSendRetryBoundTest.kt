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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSendRetryBoundTest {

    @Test
    fun an_early_attempt_is_retried() {
        assertEquals(PendingSendOutcome.RETRY, bounded_retry_outcome(0))
        assertEquals(PendingSendOutcome.RETRY, bounded_retry_outcome(1))
    }

    @Test
    fun the_last_attempt_before_the_bound_is_still_retried() {
        assertEquals(PendingSendOutcome.RETRY, bounded_retry_outcome(SEND_RETRY_MAX_ATTEMPTS - 1))
    }

    @Test
    fun an_exhausted_attempt_is_deferred_instead_of_retried_forever() {
        assertEquals(PendingSendOutcome.DEFERRED, bounded_retry_outcome(SEND_RETRY_MAX_ATTEMPTS))
        assertEquals(PendingSendOutcome.DEFERRED, bounded_retry_outcome(SEND_RETRY_MAX_ATTEMPTS + 40))
    }

    @Test
    fun a_send_that_loses_its_connection_waits_for_the_network() {
        assertTrue(should_wait_for_network(java.net.UnknownHostException("offline"), false, 0L, 60_000L))
        assertTrue(should_wait_for_network(java.net.ConnectException("refused"), false, 0L, 60_000L))
        assertTrue(should_wait_for_network(org.astermail.android.api.ApiError.NetworkError, false, 0L, 60_000L))
    }

    @Test
    fun a_rejected_or_stale_send_does_not_wait() {
        assertFalse(should_wait_for_network(java.net.UnknownHostException("offline"), true, 0L, 60_000L))
        assertFalse(should_wait_for_network(IllegalStateException("rejected"), false, 0L, 60_000L))
        assertFalse(should_wait_for_network(java.net.SocketTimeoutException("read timed out"), false, 0L, 60_000L))
        assertFalse(should_wait_for_network(org.astermail.android.api.ApiError.ServerError(503), false, 0L, 60_000L))
        assertFalse(should_wait_for_network(org.astermail.android.api.ApiError.RateLimited(), false, 0L, 60_000L))
        assertFalse(should_wait_for_network(java.net.UnknownHostException("offline"), false, 0L, SEND_OFFLINE_MAX_AGE_MS))
    }
}
