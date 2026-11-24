// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class toast_window_action_test {
    @Test
    fun nothing_happens_while_idle() {
        assertEquals(
            ToastWindowAction.none,
            toast_window_action(attached = false, attached_generation = 0, generation = 3, wants_window = false),
        )
    }

    @Test
    fun a_new_toast_attaches_the_window() {
        assertEquals(
            ToastWindowAction.attach,
            toast_window_action(attached = false, attached_generation = 0, generation = 2, wants_window = true),
        )
    }

    @Test
    fun a_steady_toast_keeps_its_window() {
        assertEquals(
            ToastWindowAction.none,
            toast_window_action(attached = true, attached_generation = 4, generation = 4, wants_window = true),
        )
    }

    @Test
    fun a_popup_opening_raises_the_window() {
        assertEquals(
            ToastWindowAction.raise,
            toast_window_action(attached = true, attached_generation = 4, generation = 5, wants_window = true),
        )
    }

    @Test
    fun a_popup_closing_leaves_the_window_alone() {
        val generation = 7
        val after_open = toast_window_action(attached = true, attached_generation = 6, generation = generation, wants_window = true)
        assertEquals(ToastWindowAction.raise, after_open)
        val after_close = toast_window_action(attached = true, attached_generation = generation, generation = generation, wants_window = true)
        assertEquals(ToastWindowAction.none, after_close)
    }

    @Test
    fun an_ended_toast_detaches_the_window() {
        assertEquals(
            ToastWindowAction.detach,
            toast_window_action(attached = true, attached_generation = 4, generation = 9, wants_window = false),
        )
    }
}
