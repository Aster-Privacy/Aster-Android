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

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaneReplayTest {

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun full_record(count: Int): LaneReplayRecord = LaneReplayRecord(
        0,
        (1..count).map { LaneReplayEntry("k$it", "m$it", it.toLong()) },
    )

    @Test
    fun a_new_lane_is_fresh_and_recorded() {
        val claim = LaneReplay.claim(LaneReplay.EMPTY, "k", "m", 10)

        assertEquals(LaneFreshness.FRESH, claim.freshness)
        assertTrue(claim.changed)
        assertEquals(listOf(LaneReplayEntry("k", "m", 10)), claim.record.e)
    }

    @Test
    fun the_same_message_stays_fresh_and_another_message_is_a_replay() {
        val stored = LaneReplay.claim(LaneReplay.EMPTY, "k", "m", 10).record

        val again = LaneReplay.claim(stored, "k", "m", 10)
        val copy = LaneReplay.claim(stored, "k", "other", 10)

        assertEquals(LaneFreshness.FRESH, again.freshness)
        assertFalse(again.changed)
        assertEquals(LaneFreshness.REPLAYED, copy.freshness)
        assertFalse(copy.changed)
    }

    @Test
    fun eviction_raises_the_floor_to_the_highest_dropped_send_time() {
        val capped = LaneReplay.cap(full_record(LaneReplay.MAX_ENTRIES + 3))

        assertEquals(LaneReplay.MAX_ENTRIES, capped.e.size)
        assertEquals(3L, capped.f)
        assertEquals("k4", capped.e.first().k)
    }

    @Test
    fun an_evicted_lane_comes_back_as_a_replay() {
        val stored = LaneReplay.claim(full_record(LaneReplay.MAX_ENTRIES), "late", "m_late", 5_000).record

        assertEquals(1L, stored.f)
        assertFalse(stored.e.any { it.k == "k1" })
        assertEquals(LaneFreshness.REPLAYED, LaneReplay.claim(stored, "k1", "m1", 1).freshness)
        assertEquals(LaneFreshness.FRESH, LaneReplay.claim(stored, "k2", "m2", 2).freshness)
        assertEquals(LaneFreshness.FRESH, LaneReplay.claim(stored, "new", "m_new", 6_000).freshness)
    }

    @Test
    fun a_lane_at_or_below_the_floor_is_a_replay() {
        val stored = LaneReplayRecord(100, emptyList())

        assertEquals(LaneFreshness.REPLAYED, LaneReplay.claim(stored, "k", "m", 100).freshness)
        assertEquals(LaneFreshness.REPLAYED, LaneReplay.claim(stored, "k", "m", 99).freshness)
        assertEquals(LaneFreshness.FRESH, LaneReplay.claim(stored, "k", "m", 101).freshness)
        assertEquals(LaneFreshness.REPLAYED, LaneReplay.claim(stored, "k", "m", -1).freshness)
    }

    @Test
    fun merging_keeps_the_primary_entry_and_the_higher_floor() {
        val primary = LaneReplayRecord(2, listOf(LaneReplayEntry("k", "mine", 5)))
        val secondary = LaneReplayRecord(7, listOf(LaneReplayEntry("k", "theirs", 6), LaneReplayEntry("j", "m", 8)))

        val merged = LaneReplay.merge(primary, secondary)

        assertEquals(7L, merged.f)
        assertEquals(listOf(LaneReplayEntry("k", "mine", 5), LaneReplayEntry("j", "m", 8)), merged.e)
    }

    @Test
    fun merging_two_full_records_moves_the_floor() {
        val left = full_record(LaneReplay.MAX_ENTRIES)
        val right = LaneReplayRecord(
            0,
            (1..LaneReplay.MAX_ENTRIES).map { LaneReplayEntry("r$it", "m$it", 2_000L + it) },
        )

        val merged = LaneReplay.merge(left, right)

        assertEquals(LaneReplay.MAX_ENTRIES, merged.e.size)
        assertEquals(LaneReplay.MAX_ENTRIES.toLong(), merged.f)
    }

    @Test
    fun stored_records_drop_malformed_and_duplicate_entries() {
        val stored = JSONObject()
            .put("f", 3)
            .put(
                "e",
                JSONArray()
                    .put(JSONObject().put("k", "a").put("m", "m").put("t", 4))
                    .put(JSONObject().put("k", "a").put("m", "x").put("t", 5))
                    .put(JSONObject().put("k", "b").put("m", "m").put("t", 1.5))
                    .put(JSONObject().put("k", "").put("m", "m").put("t", 4))
                    .put(JSONObject().put("k", "c").put("m", "m").put("t", -2))
                    .put("junk"),
            )

        val record = LaneReplay.normalize(stored.toString())

        assertEquals(3L, record.f)
        assertEquals(listOf(LaneReplayEntry("a", "m", 4)), record.e)
        assertEquals(LaneReplay.EMPTY, LaneReplay.normalize("not json"))
    }

    private fun crowded_map(senders: Int, entries: Int): JSONObject {
        val map = JSONObject()
        for (sender in 0 until senders) {
            val address = "s$sender".padEnd(240, 'x') + "@example.org"
            val list = JSONArray()
            for (index in 0 until entries) {
                list.put(
                    JSONObject()
                        .put("k", "%024d".format(sender * 100_000 + index))
                        .put("m", "%024d".format(index))
                        .put("t", 1_790_000_000_000L + index * 1_000L + sender),
                )
            }
            map.put(address, JSONObject().put("f", 0).put("e", list))
        }
        return map
    }

    private fun stored_entries(map: JSONObject): Int =
        map.keys().asSequence().sumOf { LaneReplay.normalize(map.opt(it)).e.size }

    private fun stored_senders(map: JSONObject): Int = map.keys().asSequence().count { it != "*" }

    @Test
    fun many_senders_keep_the_stored_record_well_under_the_backup_limit() {
        val merged = LaneReplay.merge_maps(crowded_map(300, 220), null)

        assertTrue(stored_entries(merged) <= 4096)
        assertTrue(stored_senders(merged) <= 256)
        assertTrue(merged.toString().toByteArray(Charsets.UTF_8).size < 1_000_000)
    }

    @Test
    fun capping_never_lets_a_dropped_lane_come_back_fresh() {
        val input = crowded_map(270, 20)
        val merged = LaneReplay.merge_maps(input, null)
        val shared = LaneReplay.normalize(merged.opt("*")).f

        assertTrue(stored_entries(merged) <= 4096)
        assertTrue(stored_senders(merged) <= 256)
        for (sender in input.keys()) {
            val kept = LaneReplay.normalize(merged.opt(sender))
            val floor = maxOf(kept.f, shared)
            val kept_keys = kept.e.mapTo(HashSet()) { it.k }
            for (entry in LaneReplay.normalize(input.opt(sender)).e) {
                assertTrue(entry.k in kept_keys || entry.t <= floor)
                val claim = LaneReplay.claim(LaneReplayRecord(floor, kept.e), entry.k, "planted", entry.t)
                assertEquals(LaneFreshness.REPLAYED, claim.freshness)
            }
        }
        val again = LaneReplay.merge_maps(merged, input)
        assertEquals(merged.toString().length, LaneReplay.merge_maps(again, null).toString().length)
    }

    @Test
    fun keys_are_short_hashes_of_the_conversation_and_lane_nonce() {
        val key = LaneReplay.key("conv", "nonce")

        assertEquals(24, key.length)
        assertFalse(key == LaneReplay.key("conv", "other"))
        assertFalse(key == LaneReplay.key("other", "nonce"))
    }

    @Test
    fun a_session_start_message_is_first_the_first_time_it_is_opened() {
        val claim = LaneReplay.claim_bootstrap(LaneReplay.EMPTY, "k", "m", 10)

        assertEquals(BootstrapClaim.FIRST, claim.claim)
        assertTrue(claim.changed)
        assertEquals(listOf(LaneReplayEntry("k", "m", 10)), claim.record.e)
    }

    @Test
    fun a_session_start_message_opened_again_under_its_own_id_is_the_same_message() {
        val stored = LaneReplay.claim_bootstrap(LaneReplay.EMPTY, "k", "m", 10).record

        val claim = LaneReplay.claim_bootstrap(stored, "k", "m", 20)

        assertEquals(BootstrapClaim.SAME_MESSAGE, claim.claim)
        assertFalse(claim.changed)
    }

    @Test
    fun a_session_start_message_opened_again_under_another_id_is_replayed() {
        val stored = LaneReplay.claim_bootstrap(LaneReplay.EMPTY, "k", "m", 10).record

        val claim = LaneReplay.claim_bootstrap(stored.to_json().toString(), "k", "other", 20)

        assertEquals(BootstrapClaim.REPLAYED, claim.claim)
    }

    @Test
    fun session_start_records_are_kept_past_a_year() {
        val stored = LaneReplay.claim_bootstrap(LaneReplay.EMPTY, "k", "m", 10).record
        val later = 11 + 2 * 365L * 24 * 60 * 60 * 1000

        val claim = LaneReplay.claim_bootstrap(stored, "k", "other", later)

        assertEquals(BootstrapClaim.REPLAYED, claim.claim)
        assertEquals(listOf(LaneReplayEntry("k", "m", 10)), claim.record.e)
    }

    @Test
    fun a_full_record_raises_its_floor_and_never_calls_an_unknown_start_first_again() {
        var record: Any? = LaneReplay.EMPTY
        for (index in 0 until LaneReplay.MAX_ENTRIES) {
            val claim = LaneReplay.claim_bootstrap(record, "k$index", "m$index", 10L + index)
            assertEquals(BootstrapClaim.FIRST, claim.claim)
            record = claim.record
        }
        val overflow = LaneReplay.claim_bootstrap(record, "k-new", "m-new", 5_000)

        assertEquals(BootstrapClaim.FIRST, overflow.claim)
        assertEquals(10L, overflow.record.f)
        assertFalse(overflow.record.e.any { it.k == "k0" })

        val evicted = LaneReplay.claim_bootstrap(overflow.record.to_json().toString(), "k0", "m-again", 6_000)

        assertEquals(BootstrapClaim.UNKNOWN, evicted.claim)

        val replay_of_that = LaneReplay.claim_bootstrap(evicted.record, "k0", "m-third", 7_000)

        assertEquals(BootstrapClaim.REPLAYED, replay_of_that.claim)
    }

    @Test
    fun a_record_with_a_floor_still_recognizes_what_it_holds() {
        val stored = LaneReplayRecord(50, listOf(LaneReplayEntry("k", "m", 60)))

        assertEquals(BootstrapClaim.SAME_MESSAGE, LaneReplay.claim_bootstrap(stored, "k", "m", 70).claim)
        assertEquals(BootstrapClaim.REPLAYED, LaneReplay.claim_bootstrap(stored, "k", "x", 70).claim)
        assertEquals(BootstrapClaim.UNKNOWN, LaneReplay.claim_bootstrap(stored, "other", "x", 70).claim)
    }

    @Test
    fun the_session_start_key_covers_every_field_of_the_opening_message() {
        val base = LaneReplay.bootstrap_key("id", "eph", null, "dh", 0)

        assertEquals(base, LaneReplay.bootstrap_key("id", "eph", "", "dh", 0))
        assertTrue(base != LaneReplay.bootstrap_key("other", "eph", null, "dh", 0))
        assertTrue(base != LaneReplay.bootstrap_key("id", "other", null, "dh", 0))
        assertTrue(base != LaneReplay.bootstrap_key("id", "eph", "ct", "dh", 0))
        assertTrue(base != LaneReplay.bootstrap_key("id", "eph", null, "other", 0))
        assertTrue(base != LaneReplay.bootstrap_key("id", "eph", null, "dh", 1))
    }
}
