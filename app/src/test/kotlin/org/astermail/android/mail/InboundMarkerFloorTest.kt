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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundMarkerFloorTest {

    private class memory_store : InboundMarkerFloorStore {
        val saved = mutableMapOf<String, Long>()
        val seen = mutableMapOf<String, Map<String, Long>>()
        override fun floor(account: String): Long? = saved[account]
        override fun save(account: String, floor_ms: Long): Boolean {
            saved[account] = floor_ms
            return true
        }
        override fun first_seen(account: String): Map<String, Long>? = seen[account]
        override fun save_first_seen(account: String, seen: Map<String, Long>): Boolean {
            this.seen[account] = seen
            return true
        }
    }

    private val floor_at = 1_000_000L
    private val after_window = floor_at + InboundMarkerFloor.GRACE_MS * 4

    private fun bound_store(): memory_store {
        val store = memory_store()
        store.saved["a"] = floor_at
        InboundMarkerFloor.bind({ "a" }, store)
        return store
    }

    @Test
    fun an_envelope_refused_once_stays_refused_when_its_date_is_moved_back() {
        bound_store()
        assertFalse(InboundMarkerFloor.accepts_legacy(after_window, now_ms = after_window, encrypted_b64 = "env-x"))
        assertFalse(InboundMarkerFloor.accepts_legacy(floor_at - 1, now_ms = after_window + 1, encrypted_b64 = "env-x"))
    }

    @Test
    fun an_envelope_opened_inside_the_window_stays_readable_when_its_date_moves_forward() {
        bound_store()
        assertTrue(InboundMarkerFloor.accepts_legacy(floor_at + 10, now_ms = floor_at + 20, encrypted_b64 = "env-y"))
        assertTrue(InboundMarkerFloor.accepts_legacy(after_window, now_ms = after_window, encrypted_b64 = "env-y"))
    }

    @Test
    fun a_new_envelope_after_the_window_needs_a_date_before_the_floor() {
        bound_store()
        assertFalse(InboundMarkerFloor.accepts_legacy(floor_at + 1, now_ms = after_window, encrypted_b64 = "env-z"))
        assertTrue(InboundMarkerFloor.accepts_legacy(floor_at - 1, now_ms = after_window, encrypted_b64 = "env-old"))
    }

    @Test
    fun first_seen_times_survive_a_restart() {
        val store = bound_store()
        assertFalse(InboundMarkerFloor.accepts_legacy(after_window, now_ms = after_window, encrypted_b64 = "env-x"))
        InboundMarkerFloor.bind({ "a" }, store)
        assertFalse(InboundMarkerFloor.accepts_legacy(floor_at - 1, now_ms = after_window + 1, encrypted_b64 = "env-x"))
        assertFalse(store.seen["a"].orEmpty().keys.any { it.contains("env-x") })
    }

    @Test
    fun owner_bound_envelopes_do_not_record_a_first_sighting() {
        val store = bound_store()
        val bound = java.util.Base64.getEncoder().encodeToString(
            byteArrayOf(InboundEnvelopeDecryptor.ECDH_BOUND_MARKER) + ByteArray(80) { it.toByte() },
        )
        val pq_bound = java.util.Base64.getEncoder().encodeToString(
            byteArrayOf(InboundEnvelopeDecryptor.PQ_HYBRID_BOUND_MARKER) + ByteArray(80) { (it + 1).toByte() },
        )
        InboundMarkerFloor.accepts_legacy(floor_at - 1, now_ms = after_window, encrypted_b64 = bound)
        InboundMarkerFloor.accepts_legacy(floor_at - 1, now_ms = after_window, encrypted_b64 = pq_bound)
        assertTrue(store.seen["a"].orEmpty().isEmpty())

        val legacy = java.util.Base64.getEncoder().encodeToString(
            byteArrayOf(InboundEnvelopeDecryptor.ECDH_MARKER) + ByteArray(80) { (it + 2).toByte() },
        )
        InboundMarkerFloor.accepts_legacy(floor_at - 1, now_ms = after_window, encrypted_b64 = legacy)
        assertEquals(1, store.seen["a"].orEmpty().size)
    }

    @After
    fun tear_down() {
        InboundMarkerFloor.reset()
    }

    @Test
    fun legacy_markers_open_until_a_bound_envelope_is_seen() {
        assertTrue(InboundMarkerFloor.legacy_allowed(null, 5_000L, 10_000L))
        assertTrue(InboundMarkerFloor.legacy_allowed(null, null, 10_000L))
    }

    @Test
    fun legacy_markers_received_after_the_floor_are_refused() {
        val floor = 1_000_000L
        val late = floor + InboundMarkerFloor.GRACE_MS
        assertTrue(InboundMarkerFloor.legacy_allowed(floor, floor - 1, late * 2))
        assertTrue(InboundMarkerFloor.legacy_allowed(floor, late - 1, late * 2))
        assertFalse(InboundMarkerFloor.legacy_allowed(floor, late, late * 2))
        assertFalse(InboundMarkerFloor.legacy_allowed(floor, null, late))
    }

    @Test
    fun the_first_bound_envelope_sets_a_persisted_floor_per_account() {
        val store = memory_store()
        var account: String? = "a"
        InboundMarkerFloor.bind({ account }, store)

        assertTrue(InboundMarkerFloor.accepts_legacy(null, now_ms = 50_000_000L))
        InboundMarkerFloor.note_bound(now_ms = 1_000L)
        InboundMarkerFloor.note_bound(now_ms = 9_000L)
        assertEquals(1_000L, store.saved["a"])

        val far = 1_000L + InboundMarkerFloor.GRACE_MS
        assertFalse(InboundMarkerFloor.accepts_legacy(null, now_ms = far))
        assertTrue(InboundMarkerFloor.accepts_legacy(500L, now_ms = far))

        account = "b"
        assertTrue(InboundMarkerFloor.accepts_legacy(null, now_ms = far))
        assertNull(store.saved["b"])
    }

    @Test
    fun the_floor_survives_a_restart_through_the_store() {
        val store = memory_store()
        store.saved["a"] = 1_000L
        InboundMarkerFloor.bind({ "a" }, store)

        assertFalse(InboundMarkerFloor.accepts_legacy(null, now_ms = 1_000L + InboundMarkerFloor.GRACE_MS))
    }

    @Test
    fun no_account_keeps_legacy_markers_open() {
        InboundMarkerFloor.bind({ null }, memory_store())
        InboundMarkerFloor.note_bound(now_ms = 1_000L)
        assertTrue(InboundMarkerFloor.accepts_legacy(null, now_ms = Long.MAX_VALUE / 2))
    }
}
