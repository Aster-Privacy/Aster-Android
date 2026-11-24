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

package org.astermail.android.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryAddressEligibilityCacheTest {
    private val snapshot = primary_address_eligibility_snapshot(
        eligible = true,
        lock_reason = null,
        current_address = "someone@astermail.org",
        next_change_available_at = null,
        renames_allowed = 1,
    )

    @Test
    fun empty_memo_returns_nothing() {
        assertNull(primary_address_eligibility_memo().read("user-1"))
    }

    @Test
    fun memo_returns_snapshot_for_same_account() {
        val memo = primary_address_eligibility_memo()
        memo.write("user-1", snapshot)
        assertEquals(snapshot, memo.read("user-1"))
    }

    @Test
    fun memo_hides_snapshot_from_another_account() {
        val memo = primary_address_eligibility_memo()
        memo.write("user-1", snapshot)
        assertNull(memo.read("user-2"))
    }

    @Test
    fun memo_ignores_blank_account_keys() {
        val memo = primary_address_eligibility_memo()
        memo.write("", snapshot)
        memo.write(null, snapshot)
        assertNull(memo.read(""))
        assertNull(memo.read(null))
        memo.write("user-1", snapshot)
        assertNull(memo.read(null))
    }

    @Test
    fun clear_forgets_snapshot() {
        val memo = primary_address_eligibility_memo()
        memo.write("user-1", snapshot)
        memo.clear()
        assertNull(memo.read("user-1"))
    }

    @Test
    fun newer_write_replaces_older_snapshot() {
        val memo = primary_address_eligibility_memo()
        memo.write("user-1", snapshot)
        val locked = snapshot.copy(eligible = false, lock_reason = primary_address_reason_cooldown)
        memo.write("user-1", locked)
        assertEquals(locked, memo.read("user-1"))
    }

    @Test
    fun with_eligibility_marks_state_loaded() {
        val failed = PrimaryAddressUiState(eligibility_failed = true, eligibility_checked = true)
        val seeded = failed.with_eligibility(snapshot)
        assertTrue(seeded.eligibility_loaded)
        assertFalse(seeded.eligibility_failed)
        assertTrue(seeded.eligible)
        assertEquals("someone@astermail.org", seeded.current_address)
        assertEquals(1, seeded.renames_allowed)
    }

    @Test
    fun with_eligibility_keeps_lock_details() {
        val locked = snapshot.copy(
            eligible = false,
            lock_reason = primary_address_reason_plan,
            next_change_available_at = "2026-12-01T00:00:00Z",
        )
        val seeded = PrimaryAddressUiState().with_eligibility(locked)
        assertFalse(seeded.eligible)
        assertEquals(primary_address_reason_plan, seeded.lock_reason)
        assertEquals("2026-12-01T00:00:00Z", seeded.next_change_available_at)
    }

    @Test
    fun with_null_snapshot_leaves_state_unloaded() {
        val initial = PrimaryAddressUiState()
        val seeded = initial.with_eligibility(null)
        assertSame(initial, seeded)
        assertFalse(seeded.eligibility_loaded)
    }

    @Test
    fun with_eligibility_keeps_flow_progress() {
        val in_flow = PrimaryAddressUiState(step = PrimaryAddressStep.PICK, local_part = "newname")
        val seeded = in_flow.with_eligibility(snapshot)
        assertEquals(PrimaryAddressStep.PICK, seeded.step)
        assertEquals("newname", seeded.local_part)
    }
}
