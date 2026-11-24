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

package org.astermail.android.ui.settings.detail

import org.astermail.android.security.AppLockTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockRowsVisibleTest {

    @Test
    fun shows_app_lock_when_vanguard_is_on() {
        assertTrue(app_lock_rows_visible(vanguard_enabled = true, lock_configured = false))
    }

    @Test
    fun hides_app_lock_when_vanguard_is_off_and_no_lock_is_set() {
        assertFalse(app_lock_rows_visible(vanguard_enabled = false, lock_configured = false))
        assertFalse(app_lock_rows_visible(vanguard_enabled = null, lock_configured = false))
    }

    @Test
    fun keeps_an_existing_lock_reachable_after_vanguard_turns_off() {
        assertTrue(app_lock_rows_visible(vanguard_enabled = false, lock_configured = true))
        assertTrue(app_lock_rows_visible(vanguard_enabled = null, lock_configured = true))
    }

    @Test
    fun offers_the_timeouts_the_feature_promises() {
        val labels = AppLockTimeout.entries.map { app_lock_timeout_label(it) }
        assertEquals(AppLockTimeout.entries.size, labels.toSet().size)
        assertTrue(AppLockTimeout.entries.containsAll(
            listOf(
                AppLockTimeout.IMMEDIATELY,
                AppLockTimeout.ONE_MINUTE,
                AppLockTimeout.FIVE_MINUTES,
                AppLockTimeout.FIFTEEN_MINUTES,
            ),
        ))
    }
}
