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

import org.astermail.android.storage.ScheduledUntaggedSnapshot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledUntaggedSnapshotTest {

    private val now_ms = 1_794_960_000_000L

    private fun shared(at: String, vararg ids: String): JSONObject =
        JSONObject().put("v", 1).put("at", at).put("ids", JSONArray(ids.toList()))

    @Test
    fun a_new_snapshot_uses_the_shared_format() {
        val vault = JSONObject()
        val candidate = ScheduledUntaggedSnapshot.taken_at(setOf("b", "a"), now_ms)

        assertTrue(with_scheduled_untagged_snapshot(vault, candidate))

        val written = vault.getJSONObject(SCHEDULED_UNTAGGED_FIELD)
        assertEquals(1, written.getInt("v"))
        assertEquals("2026-11-18T00:00:00.000Z", written.getString("at"))
        assertEquals(listOf("a", "b"), (0 until 2).map { written.getJSONArray("ids").getString(it) })
        assertFalse(vault.has(LEGACY_SCHEDULED_UNTAGGED_FIELD))
    }

    @Test
    fun a_snapshot_from_another_client_is_never_replaced() {
        val web = shared("2026-11-05T00:00:00.000Z", "s0")
        val vault = JSONObject().put(SCHEDULED_UNTAGGED_FIELD, web)

        assertFalse(with_scheduled_untagged_snapshot(vault, ScheduledUntaggedSnapshot.taken_at(setOf("s0", "s1"), now_ms)))

        assertSame(web, vault.get(SCHEDULED_UNTAGGED_FIELD))
        assertEquals(setOf("s0"), vault_scheduled_untagged(vault, now_ms)?.ids)
    }

    @Test
    fun an_older_phone_list_moves_to_the_shared_format_without_losing_ids() {
        val legacy = JSONArray(listOf("b", "a"))
        val vault = JSONObject().put(LEGACY_SCHEDULED_UNTAGGED_FIELD, legacy)

        assertEquals(ScheduledUntaggedSnapshot("2026-11-18T00:00:00.000Z", setOf("b", "a")), vault_scheduled_untagged(vault, now_ms))
        assertTrue(with_scheduled_untagged_snapshot(vault, ScheduledUntaggedSnapshot.taken_at(setOf("a", "b", "c"), now_ms)))

        assertEquals(setOf("a", "b"), vault_scheduled_untagged(vault, now_ms)?.ids)
        assertSame(legacy, vault.get(LEGACY_SCHEDULED_UNTAGGED_FIELD))
    }

    @Test
    fun both_formats_together_keep_only_ids_in_both() {
        val vault = JSONObject()
            .put(SCHEDULED_UNTAGGED_FIELD, shared("2026-11-05T00:00:00Z", "a", "b", "x"))
            .put(LEGACY_SCHEDULED_UNTAGGED_FIELD, JSONArray(listOf("a", "b")))

        val snapshot = vault_scheduled_untagged(vault, now_ms)

        assertEquals("2026-11-05T00:00:00Z", snapshot?.at)
        assertEquals(setOf("a", "b"), snapshot?.ids)
    }

    @Test
    fun snapshots_outside_the_shared_format_are_ignored() {
        assertNull(ScheduledUntaggedSnapshot.from_json(shared("Nov 1 2026", "a")))
        assertNull(ScheduledUntaggedSnapshot.from_json(shared("2026-11-01T00:00:00+01:00", "a")))
        assertNull(ScheduledUntaggedSnapshot.from_json(shared("2026-11-01T00:00:00Z", "a").put("v", 2)))
        assertNull(ScheduledUntaggedSnapshot.from_json(shared("2026-11-01T00:00:00Z", "a", "")))
        assertNull(ScheduledUntaggedSnapshot.from_json(shared("2026-11-01T00:00:00Z").put("ids", JSONArray(listOf(1)))))
        assertEquals(ScheduledUntaggedSnapshot.EMPTY, vault_scheduled_untagged(JSONObject().put(SCHEDULED_UNTAGGED_FIELD, "a"), now_ms))
    }

    @Test
    fun the_cutoff_matches_the_web_app() {
        val snapshot = ScheduledUntaggedSnapshot("2026-11-05T00:00:00.000Z", setOf("a"))

        assertFalse(snapshot.allows("a", null))
        assertTrue(snapshot.allows("a", "2026-11-05T00:00:00.000999Z"))
        assertTrue(snapshot.allows("a", "2026-11-05T01:00:00+01:00"))
        assertFalse(snapshot.allows("a", "2026-11-05T00:00:00.001Z"))
        assertFalse(snapshot.allows("a", "yesterday"))
        assertFalse(snapshot.allows("b", null))
    }

    @Test
    fun the_earliest_snapshot_wins_by_instant() {
        val whole = shared("2026-11-01T00:00:00Z", "a")
        val fraction = shared("2026-11-01T00:00:00.500Z", "a")

        assertSame(whole, earliest_scheduled_untagged(fraction, whole))
        assertSame(whole, earliest_scheduled_untagged(whole, fraction))
        assertEquals("broken", earliest_scheduled_untagged("broken", fraction))
        assertSame(fraction, earliest_scheduled_untagged(null, fraction))
        assertEquals("broken", earliest_scheduled_untagged("broken", null))
        assertNull(earliest_scheduled_untagged(null, null))
    }
}
