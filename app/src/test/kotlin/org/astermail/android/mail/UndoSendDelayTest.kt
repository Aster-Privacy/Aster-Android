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

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoSendDelayTest {
    @Test
    fun disabled_undo_send_resolves_to_no_window() {
        assertEquals(0, resolve_undo_send_seconds(false, 20))
    }

    @Test
    fun zero_seconds_resolves_to_no_window() {
        assertEquals(0, resolve_undo_send_seconds(true, 0))
    }

    @Test
    fun missing_seconds_falls_back_to_the_default() {
        assertEquals(UNDO_SEND_DEFAULT_SECONDS, resolve_undo_send_seconds(null, null))
    }

    @Test
    fun oversized_seconds_clamp_to_the_maximum() {
        assertEquals(UNDO_SEND_MAX_SECONDS, resolve_undo_send_seconds(true, 600))
    }

    @Test
    fun in_range_seconds_pass_through() {
        assertEquals(20, resolve_undo_send_seconds(true, 20))
    }

    @Test
    fun clamp_replaces_below_range_values_with_the_default() {
        assertEquals(UNDO_SEND_DEFAULT_SECONDS, clamp_undo_send_seconds(0))
    }
}
