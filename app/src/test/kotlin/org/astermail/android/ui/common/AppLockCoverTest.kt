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

package org.astermail.android.ui.common

import org.astermail.android.security.AppLockPresenceRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockCoverTest {

    @Test
    fun covers_before_secure_storage_is_ready_when_locked() {
        assertTrue(app_lock_cover_visible(is_locked = true, reveal_started = false, storage_ready = false, signed_in = false))
    }

    @Test
    fun covers_signed_in_user_after_storage_is_ready() {
        assertTrue(app_lock_cover_visible(is_locked = true, reveal_started = false, storage_ready = true, signed_in = true))
    }

    @Test
    fun hides_for_signed_out_user_once_storage_is_ready() {
        assertFalse(app_lock_cover_visible(is_locked = true, reveal_started = false, storage_ready = true, signed_in = false))
    }

    @Test
    fun hides_when_not_locked() {
        assertFalse(app_lock_cover_visible(is_locked = false, reveal_started = false, storage_ready = false, signed_in = true))
        assertFalse(app_lock_cover_visible(is_locked = false, reveal_started = false, storage_ready = true, signed_in = true))
    }

    @Test
    fun hides_once_reveal_starts() {
        assertFalse(app_lock_cover_visible(is_locked = true, reveal_started = true, storage_ready = true, signed_in = true))
        assertFalse(app_lock_cover_visible(is_locked = true, reveal_started = true, storage_ready = false, signed_in = true))
    }

    @Test
    fun presence_marker_alone_always_requires_lock() {
        assertTrue(AppLockPresenceRule.requires_lock(AppLockPresenceRule.resolve(has_pin_hash = false, marker_present = true)))
        assertTrue(AppLockPresenceRule.requires_lock(AppLockPresenceRule.resolve(has_pin_hash = true, marker_present = true)))
    }
}
