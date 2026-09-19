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

package org.astermail.android.ui.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowTapGuardTest {

    private val slop = 12f

    @Test
    fun a_still_tap_opens_the_row() {
        assertTrue(tap_opens_row(0f, 0f, slop, refresh_engaged = false))
        assertTrue(tap_opens_row(3f, -4f, slop, refresh_engaged = false))
    }

    @Test
    fun a_pull_down_never_opens_the_row() {
        assertFalse(tap_opens_row(0f, 40f, slop, refresh_engaged = false))
    }

    @Test
    fun an_engaged_refresh_never_opens_the_row() {
        assertFalse(tap_opens_row(0f, 0f, slop, refresh_engaged = true))
    }

    @Test
    fun a_scroll_upward_never_opens_the_row() {
        assertFalse(tap_opens_row(0f, -40f, slop, refresh_engaged = false))
    }

    @Test
    fun a_horizontal_drag_never_opens_the_row() {
        assertFalse(tap_opens_row(40f, 0f, slop, refresh_engaged = false))
    }
}
