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

package org.astermail.android.mail.ratchet

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryPeerStore : OneTimePrekeyPeerStore {
    val peers = mutableMapOf<String, Long>()

    override suspend fun one_time_prekey_grace_at(peer_tag: String): Long? = peers[peer_tag]

    override suspend fun record_one_time_prekey_grace_at(peer_tag: String, grace_at: Long) {
        peers[peer_tag] = grace_at
    }
}

class OneTimePrekeyWatchTest {

    private val recipient = "peer@astermail.org"
    private val peer = one_time_prekey_peer_tag(ByteArray(65) { 4 })
    private val start = 1_800_000_000_000L

    @Test
    fun a_peer_that_never_offered_one_time_prekeys_stays_unflagged() = runTest {
        val store = MemoryPeerStore()

        assertEquals(OneTimePrekeyVerdict.NEVER_OFFERED, judge_one_time_prekey_offer(store, peer, false, start))
        assertEquals(OneTimePrekeyVerdict.NEVER_OFFERED, judge_one_time_prekey_offer(store, peer, false, start + 1))
        assertTrue(store.peers.isEmpty())
    }

    @Test
    fun a_peer_that_published_one_time_prekeys_is_held_to_them() = runTest {
        val store = MemoryPeerStore()

        assertEquals(OneTimePrekeyVerdict.OFFERED, judge_one_time_prekey_offer(store, peer, true, start))
        assertEquals(OneTimePrekeyVerdict.GRACE, judge_one_time_prekey_offer(store, peer, false, start + 1))
        assertEquals(OneTimePrekeyVerdict.WITHHELD, judge_one_time_prekey_offer(store, peer, false, start + 2))
        assertEquals(
            OneTimePrekeyVerdict.WITHHELD,
            judge_one_time_prekey_offer(store, peer, false, start + ONE_TIME_PREKEY_GRACE_MS),
        )
        assertEquals(
            OneTimePrekeyVerdict.GRACE,
            judge_one_time_prekey_offer(store, peer, false, start + 1 + ONE_TIME_PREKEY_GRACE_MS),
        )
        assertEquals(OneTimePrekeyVerdict.OFFERED, judge_one_time_prekey_offer(store, peer, true, start + 3 + ONE_TIME_PREKEY_GRACE_MS))
    }

    @Test
    fun a_clock_that_moved_backwards_grants_a_fresh_grace_window() = runTest {
        val store = MemoryPeerStore()
        judge_one_time_prekey_offer(store, peer, true, start)
        judge_one_time_prekey_offer(store, peer, false, start)

        assertEquals(OneTimePrekeyVerdict.GRACE, judge_one_time_prekey_offer(store, peer, false, start - 10))
        assertEquals(OneTimePrekeyVerdict.WITHHELD, judge_one_time_prekey_offer(store, peer, false, start - 5))
    }

    @Test
    fun peers_are_tracked_separately() = runTest {
        val store = MemoryPeerStore()
        val other = one_time_prekey_peer_tag(ByteArray(65) { 5 })
        judge_one_time_prekey_offer(store, peer, true, start)

        assertEquals(OneTimePrekeyVerdict.NEVER_OFFERED, judge_one_time_prekey_offer(store, other, false, start))
        assertEquals(32, peer.length)
    }

    @Test
    fun a_withheld_one_time_prekey_seals_with_the_signed_prekey_and_never_pgp() = runTest {
        val attempts = ArrayList<Boolean>()
        var pgp_calls = 0
        val sealed = runCatching {
            seal_internal_body(
                recipients = listOf(recipient),
                allow_non_post_quantum = true,
                seal_ratchet = { without_one_time: Boolean ->
                    attempts.add(without_one_time)
                    if (!without_one_time) throw OneTimePrekeyWithheldException(recipient)
                    "signed prekey only"
                },
                seal_pgp = {
                    pgp_calls++
                    "pgp"
                },
            )
        }.getOrNull()

        assertEquals("signed prekey only", sealed)
        assertEquals(listOf(false, false, true), attempts)
        assertEquals(0, pgp_calls)
    }

    @Test
    fun a_withheld_one_time_prekey_never_asks_for_the_post_quantum_confirmation() = runTest {
        val sealed = runCatching {
            seal_internal_body(
                recipients = listOf(recipient),
                allow_non_post_quantum = false,
                seal_ratchet = { without_one_time: Boolean ->
                    if (!without_one_time) throw OneTimePrekeyWithheldException(recipient)
                    "signed prekey only"
                },
                seal_pgp = { "pgp" },
            )
        }

        assertEquals("signed prekey only", sealed.getOrNull())
    }

    @Test
    fun only_a_missing_signed_prekey_bundle_falls_back_to_pgp() = runTest {
        val sealed = runCatching {
            seal_internal_body(
                recipients = listOf(recipient),
                allow_non_post_quantum = true,
                seal_ratchet = { _: Boolean -> throw UnverifiedPrekeyBundleException(recipient) },
                seal_pgp = { "pgp" },
            )
        }.getOrNull()

        assertEquals("pgp", sealed)
    }

    @Test
    fun a_withheld_one_time_prekey_requests_a_fresh_bundle_once_before_falling_back() = runTest {
        var ratchet_calls = 0
        var pgp_calls = 0
        val sealed = runCatching {
            seal_internal_body(
                recipients = listOf(recipient),
                allow_non_post_quantum = true,
                seal_ratchet = { _: Boolean ->
                    ratchet_calls++
                    if (ratchet_calls == 1) throw OneTimePrekeyWithheldException(recipient)
                    "ratchet"
                },
                seal_pgp = {
                    pgp_calls++
                    "pgp"
                },
            )
        }.getOrNull()

        assertEquals("ratchet", sealed)
        assertEquals(2, ratchet_calls)
        assertEquals(0, pgp_calls)
    }

    @Test
    fun a_missing_signed_prekey_bundle_asks_for_the_post_quantum_confirmation_first() = runTest {
        var pgp_calls = 0
        val thrown = runCatching {
            seal_internal_body(
                recipients = listOf(recipient),
                allow_non_post_quantum = false,
                seal_ratchet = { _: Boolean -> throw UnverifiedPrekeyBundleException(recipient) },
                seal_pgp = {
                    pgp_calls++
                    "pgp"
                },
            )
        }.exceptionOrNull()

        assertTrue(thrown is PostQuantumUnavailableException)
        assertEquals(0, pgp_calls)
    }
}
