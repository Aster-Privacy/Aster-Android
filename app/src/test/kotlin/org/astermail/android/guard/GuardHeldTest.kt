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

import java.time.Instant
import org.astermail.android.api.ApiError
import org.astermail.android.api.guard.GuardPendingChange
import org.astermail.android.ui.guard.to_row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardHeldTest {
    @Test
    fun held_error_becomes_a_notice() {
        val notice = ApiError.GuardHeld("chg-1", "password_change", "2026-11-16T10:00:00Z").to_notice()
        assertEquals("chg-1", notice.id)
        assertEquals(guard_hold_kind.password_change, notice.kind)
        assertEquals(Instant.parse("2026-11-16T10:00:00Z"), notice.apply_after)
    }

    @Test
    fun bad_or_blank_times_parse_to_null() {
        assertNull(guard_parse_instant(null))
        assertNull(guard_parse_instant(""))
        assertNull(guard_parse_instant("tomorrow"))
    }

    @Test
    fun every_known_kind_round_trips() {
        guard_hold_kind.entries.filter { it != guard_hold_kind.unknown }.forEach {
            assertEquals(it, guard_hold_kind.from_wire(it.wire))
        }
    }

    @Test
    fun only_recovery_kinds_are_vetoed_and_never_skipped() {
        guard_hold_kind.entries.forEach {
            if (it.can_veto) assertFalse(it.skippable)
        }
        assertTrue(guard_hold_kind.account_recovery.can_veto)
        assertFalse(guard_hold_kind.pgp_export.can_veto)
        assertTrue(guard_hold_kind.pgp_export.skippable)
        assertFalse(guard_hold_kind.unknown.skippable)
    }

    @Test
    fun pending_change_maps_to_row() {
        val row = GuardPendingChange("id-1", "data_export", "2026-11-16T10:00:00Z", "bad").to_row()
        assertEquals(guard_hold_kind.data_export, row.kind)
        assertEquals(Instant.parse("2026-11-16T10:00:00Z"), row.apply_after)
        assertNull(row.created_at)
        assertTrue(row.server_skippable)
        assertTrue(row.server_cancellable)
    }

    @Test
    fun rescue_code_rotation_is_its_own_kind() {
        assertEquals(guard_hold_kind.rescue_code_rotation, guard_hold_kind.from_wire("rescue_code_rotation"))
        assertTrue(guard_hold_kind.rescue_code_rotation.skippable)
        assertFalse(guard_hold_kind.rescue_code_rotation.can_veto)
    }

    @Test
    fun server_flags_carry_into_the_row() {
        val row = GuardPendingChange(
            "id-2", "forwarding_change", "2026-11-16T10:00:00Z", "2026-11-15T10:00:00Z",
            skippable = false, cancellable = false,
        ).to_row()
        assertFalse(row.server_skippable)
        assertFalse(row.server_cancellable)
    }
}
