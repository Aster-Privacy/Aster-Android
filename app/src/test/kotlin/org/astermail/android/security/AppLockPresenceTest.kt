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

package org.astermail.android.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPresenceTest {

    @Test
    fun stored_pin_means_configured() {
        assertEquals(AppLockPresence.CONFIGURED, AppLockPresenceRule.resolve(has_pin_hash = true, marker_present = true))
        assertEquals(AppLockPresence.CONFIGURED, AppLockPresenceRule.resolve(has_pin_hash = true, marker_present = false))
    }

    @Test
    fun marker_without_pin_keeps_the_app_locked() {
        val presence = AppLockPresenceRule.resolve(has_pin_hash = false, marker_present = true)
        assertEquals(AppLockPresence.UNVERIFIABLE, presence)
        assertTrue(AppLockPresenceRule.requires_lock(presence))
    }

    @Test
    fun nothing_stored_means_no_lock() {
        val presence = AppLockPresenceRule.resolve(has_pin_hash = false, marker_present = false)
        assertEquals(AppLockPresence.ABSENT, presence)
        assertFalse(AppLockPresenceRule.requires_lock(presence))
    }

    @Test
    fun existing_pin_from_older_versions_gets_a_marker() {
        assertTrue(AppLockPresenceRule.should_write_marker(has_pin_hash = true, marker_present = false))
        assertFalse(AppLockPresenceRule.should_write_marker(has_pin_hash = true, marker_present = true))
        assertFalse(AppLockPresenceRule.should_write_marker(has_pin_hash = false, marker_present = false))
    }

    @Test
    fun unverifiable_lock_is_forgotten_only_after_sign_out() {
        assertFalse(AppLockPresenceRule.may_forget_marker(AppLockPresence.UNVERIFIABLE, signed_in = true))
        assertTrue(AppLockPresenceRule.may_forget_marker(AppLockPresence.UNVERIFIABLE, signed_in = false))
        assertFalse(AppLockPresenceRule.may_forget_marker(AppLockPresence.CONFIGURED, signed_in = false))
        assertFalse(AppLockPresenceRule.may_forget_marker(AppLockPresence.ABSENT, signed_in = false))
    }
}
