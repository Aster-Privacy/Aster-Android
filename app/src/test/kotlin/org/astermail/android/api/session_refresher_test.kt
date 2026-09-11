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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionRefresher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class session_refresher_test {

    private val inline_scope = CoroutineScope(Dispatchers.Unconfined)

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
            notify_scope = inline_scope,
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
        val stored: String? = "revoked"
        val calls = AtomicInteger(0)
        val notifications = AtomicInteger(0)
        val refresher = SessionRefresher(
            read_refresh_token = { stored },
            perform_refresh = {
                calls.incrementAndGet()
                delay(10)
                RefreshOutcome.AuthFailed
            },
            notify_scope = inline_scope,
        )
        refresher.on_auth_failure { presented ->
            assertEquals("revoked", presented)
            notifications.incrementAndGet()
        }

        val outcomes = (1..3).map { async { refresher.refresh() } }.awaitAll()

        assertTrue(outcomes.all { it == RefreshOutcome.AuthFailed })
        assertEquals(1, calls.get())
        assertEquals(1, notifications.get())
    }

    @Test
    fun a_dead_token_never_reaches_the_server_again() = runBlocking {
        var stored: String? = "revoked"
        val calls = AtomicInteger(0)
        val notifications = AtomicInteger(0)
        val refresher = SessionRefresher(
            read_refresh_token = { stored },
            perform_refresh = { current ->
                calls.incrementAndGet()
                if (current == "revoked") RefreshOutcome.AuthFailed else RefreshOutcome.Success
            },
            notify_scope = inline_scope,
        )
        refresher.on_auth_failure { notifications.incrementAndGet() }

        repeat(200) { assertEquals(RefreshOutcome.AuthFailed, refresher.refresh()) }
        assertEquals(1, calls.get())
        assertEquals(1, notifications.get())

        stored = "fresh_sign_in"
        assertEquals(RefreshOutcome.Success, refresher.refresh())
        assertEquals(2, calls.get())
    }

    @Test
    fun a_missing_token_fails_without_a_request() = runBlocking {
        val calls = AtomicInteger(0)
        val notifications = AtomicInteger(0)
        val refresher = SessionRefresher(
            read_refresh_token = { null },
            perform_refresh = {
                calls.incrementAndGet()
                RefreshOutcome.Success
            },
            notify_scope = inline_scope,
        )
        refresher.on_auth_failure { notifications.incrementAndGet() }

        repeat(5) { assertEquals(RefreshOutcome.AuthFailed, refresher.refresh()) }
        assertEquals(0, calls.get())
        assertEquals(0, notifications.get())
    }

    @Test
    fun transient_failures_back_off() = runBlocking {
        var now = 0L
        val calls = AtomicInteger(0)
        var next = RefreshOutcome.Transient
        val refresher = SessionRefresher(
            read_refresh_token = { "live" },
            perform_refresh = {
                calls.incrementAndGet()
                next
            },
            notify_scope = inline_scope,
            now_ms = { now },
        )

        assertEquals(RefreshOutcome.Transient, refresher.refresh())
        assertEquals(RefreshOutcome.Transient, refresher.refresh())
        assertEquals(1, calls.get())

        now += SessionRefresher.TRANSIENT_BACKOFF_BASE_MS
        assertEquals(RefreshOutcome.Transient, refresher.refresh())
        assertEquals(2, calls.get())

        now += SessionRefresher.TRANSIENT_BACKOFF_BASE_MS
        assertEquals(RefreshOutcome.Transient, refresher.refresh())
        assertEquals(2, calls.get())

        now += SessionRefresher.TRANSIENT_BACKOFF_BASE_MS
        next = RefreshOutcome.Success
        assertEquals(RefreshOutcome.Success, refresher.refresh())
        assertEquals(3, calls.get())

        assertEquals(RefreshOutcome.Success, refresher.refresh())
        assertEquals(4, calls.get())
    }

    @Test
    fun a_listener_that_signs_out_cannot_deadlock_the_caller() = runBlocking {
        var stored: String? = "revoked"
        val refresher = SessionRefresher(
            read_refresh_token = { stored },
            perform_refresh = { RefreshOutcome.AuthFailed },
        )
        val signed_out = CompletableDeferred<Unit>()
        refresher.on_auth_failure {
            refresher.refresh()
            stored = null
            signed_out.complete(Unit)
        }

        withTimeout(2_000) {
            assertEquals(RefreshOutcome.AuthFailed, refresher.refresh())
            signed_out.await()
        }
    }

    @Test
    fun a_successful_rotation_reports_the_presented_token() = runBlocking {
        val backend = rotating_backend()
        val (refresher, _) = build(backend)
        val seen = mutableListOf<String?>()
        refresher.on_refreshed { presented -> seen.add(presented) }

        refresher.refresh()
        refresher.refresh()

        assertEquals(listOf<String?>("refresh_0", "refresh_1"), seen)
    }
}
