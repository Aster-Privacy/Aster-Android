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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun zero_or_disabled_undo_send_is_inactive() {
        assertFalse(is_undo_send_active(true, 0))
        assertFalse(is_undo_send_active(false, 10))
        assertTrue(is_undo_send_active(true, 10))
        assertTrue(is_undo_send_active(null, null))
    }

    @Test
    fun a_synced_value_outside_the_presets_is_listed() {
        assertEquals(listOf(3, 5, 7, 10, 15, 20, 30), undo_send_delay_options(7))
        assertEquals(listOf(1, 3, 5, 10, 15, 20, 30), undo_send_delay_options(1))
    }

    @Test
    fun a_preset_value_keeps_the_preset_list() {
        assertEquals(UNDO_SEND_PRESET_SECONDS, undo_send_delay_options(10))
    }

    @Test
    fun out_of_range_values_are_listed_after_clamping() {
        assertEquals(UNDO_SEND_PRESET_SECONDS, undo_send_delay_options(600))
        assertEquals(UNDO_SEND_PRESET_SECONDS, undo_send_delay_options(0))
    }

    @Test
    fun clamp_caps_values_above_the_maximum() {
        assertEquals(UNDO_SEND_MAX_SECONDS, clamp_undo_send_seconds(45))
        assertEquals(UNDO_SEND_MIN_SECONDS, clamp_undo_send_seconds(1))
    }
}
