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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundReturnGateTest {

    private fun background_and_return() {
        app_session.mark_backgrounded()
        app_session.mark_foregrounded()
    }

    @Test
    fun a_list_coming_back_from_a_detail_screen_keeps_its_position_after_the_app_was_backgrounded() {
        background_and_return()
        val epoch = app_session.foreground_epoch.value
        val returning_list = ChangeGate(epoch)
        assertFalse(returning_list.passes(epoch))
    }

    @Test
    fun a_list_on_screen_while_the_app_returns_to_the_foreground_resets() {
        val shown_list = ChangeGate(app_session.foreground_epoch.value)
        background_and_return()
        assertTrue(shown_list.passes(app_session.foreground_epoch.value))
        assertFalse(shown_list.passes(app_session.foreground_epoch.value))
    }

    @Test
    fun the_selection_alignment_runs_only_after_selection_mode_is_left() {
        val gate = ChangeGate(false)
        assertFalse(gate.passes(false))
        assertTrue(gate.passes(true))
        assertTrue(gate.passes(false))
        assertFalse(gate.passes(false))
    }

    @Test
    fun foregrounding_without_a_prior_background_is_not_a_return() {
        val gate = ChangeGate(app_session.foreground_epoch.value)
        app_session.mark_foregrounded()
        assertFalse(gate.passes(app_session.foreground_epoch.value))
    }
}
