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
package org.astermail.android.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginV3SeenTest {

    @Test
    fun an_unreadable_pin_store_requires_v3_when_the_server_offers_it() {
        assertTrue(login_v3_seen(pins_readable = false, pinned = false, offered = true))
    }

    @Test
    fun an_unreadable_pin_store_does_not_lock_out_an_account_the_server_has_not_moved_to_v3() {
        assertFalse(login_v3_seen(pins_readable = false, pinned = false, offered = false))
        LoginV3.require_offered(login_v3_seen(pins_readable = false, pinned = false, offered = false), offered = false)
    }

    @Test
    fun a_readable_store_reports_its_own_pins() {
        assertTrue(login_v3_seen(pins_readable = true, pinned = true, offered = true))
        assertTrue(login_v3_seen(pins_readable = true, pinned = true, offered = false))
        assertFalse(login_v3_seen(pins_readable = true, pinned = false, offered = true))
    }
}
