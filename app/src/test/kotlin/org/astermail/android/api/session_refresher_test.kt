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

package org.astermail.android.api

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionRefresher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class session_refresher_test {

    private class rotating_backend {
        val calls = AtomicInteger(0)
        val replays = AtomicInteger(0)
        private var live_token: String = "refresh_0"
        private var counter = 0

        suspend fun refresh(presented: String?): Pair<RefreshOutcome, String?> {
            calls.incrementAndGet()
            delay(20)
            synchronized(this) {
                if (presented == null || presented != live_token) {
                    replays.incrementAndGet()
                    return RefreshOutcome.AuthFailed to null
                }
                counter += 1
                live_token = "refresh_$counter"
                return RefreshOutcome.Success to live_token
            }
        }
    }

    private fun build(backend: rotating_backend): Pair<SessionRefresher, () -> String?> {
        var stored: String? = "refresh_0"
        val refresher = SessionRefresher(
            read_refresh_token = { stored },
            perform_refresh = { current ->
                val (outcome, rotated) = backend.refresh(current)
                if (outcome == RefreshOutcome.Success) stored = rotated
                outcome
            },
        )
        return refresher to { stored }
    }

    @Test
    fun concurrent_refreshes_hit_the_server_once() = runBlocking {
        val backend = rotating_backend()
        val (refresher, read_stored) = build(backend)

        val outcomes = (1..8).map { async { refresher.refresh() } }.awaitAll()

        assertEquals(1, backend.calls.get())
        assertEquals(0, backend.replays.get())
        assertTrue(outcomes.all { it == RefreshOutcome.Success })
        assertEquals("refresh_1", read_stored())
    }

    @Test
    fun a_stale_caller_never_replays_a_rotated_token() = runBlocking {
        val backend = rotating_backend()
        val (refresher, _) = build(backend)

        assertEquals(RefreshOutcome.Success, refresher.refresh())
        assertEquals(1, backend.calls.get())

        val outcomes = (1..4).map { async { refresher.refresh() } }.awaitAll()

        assertTrue(outcomes.all { it == RefreshOutcome.Success })
        assertEquals(0, backend.replays.get())
    }

    @Test
    fun a_definitively_dead_token_reports_auth_failure_once() = runBlocking {
        var stored: String? = "revoked"
        val calls = AtomicInteger(0)
        val notifications = AtomicInteger(0)
        val refresher = SessionRefresher(
            read_refresh_token = { stored },
            perform_refresh = {
                calls.incrementAndGet()
                delay(10)
                RefreshOutcome.AuthFailed
            },
        )
        refresher.on_auth_failure { presented ->
            assertEquals("revoked", presented)
            notifications.incrementAndGet()
        }

        val outcomes = (1..3).map { async { refresher.refresh() } }.awaitAll()

        assertTrue(outcomes.all { it == RefreshOutcome.AuthFailed })
        assertEquals(3, calls.get())
        assertEquals(3, notifications.get())
        assertEquals("revoked", stored)
    }
}
