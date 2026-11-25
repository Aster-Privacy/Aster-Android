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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsScreenReadyTest {

    @Test
    fun `screen waits for every async row before it renders`() {
        assertFalse(notifications_screen_ready(true, false, true, false))
        assertFalse(notifications_screen_ready(true, true, false, false))
        assertFalse(notifications_screen_ready(true, true, true, true))
        assertFalse(notifications_screen_ready(false, true, true, false))
    }

    @Test
    fun `screen renders once preferences, rows and plan are settled`() {
        assertTrue(notifications_screen_ready(true, true, true, false))
    }
}
