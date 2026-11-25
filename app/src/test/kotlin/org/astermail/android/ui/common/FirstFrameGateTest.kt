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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstFrameGateTest {

    @After
    fun reset_gate() {
        first_frame_gate.mark_ready()
    }

    @Test
    fun signed_in_starts_on_inbox() {
        assertEquals(
            launch_start.inbox,
            launch_start_for(is_signed_in = true, pending_guard_sign_in = true, onboarding_seen = false),
        )
    }

    @Test
    fun pending_guard_sign_in_starts_on_sign_in() {
        assertEquals(
            launch_start.sign_in,
            launch_start_for(is_signed_in = false, pending_guard_sign_in = true, onboarding_seen = false),
        )
    }

    @Test
    fun first_run_starts_on_onboarding() {
        assertEquals(
            launch_start.onboarding,
            launch_start_for(is_signed_in = false, pending_guard_sign_in = false, onboarding_seen = false),
        )
    }

    @Test
    fun returning_signed_out_user_starts_on_welcome() {
        assertEquals(
            launch_start.welcome,
            launch_start_for(is_signed_in = false, pending_guard_sign_in = false, onboarding_seen = true),
        )
    }

    @Test
    fun non_inbox_starts_are_ready_at_once() {
        listOf(launch_start.sign_in, launch_start.onboarding, launch_start.welcome).forEach {
            assertTrue(first_frame_ready_before_inbox(start = it, is_locked = false))
        }
    }

    @Test
    fun locked_inbox_is_ready_at_once_so_the_lock_screen_is_the_first_frame() {
        assertTrue(first_frame_ready_before_inbox(start = launch_start.inbox, is_locked = true))
    }

    @Test
    fun unlocked_inbox_waits_for_its_rows() {
        assertFalse(first_frame_ready_before_inbox(start = launch_start.inbox, is_locked = false))
    }

    @Test
    fun inbox_with_cached_rows_is_ready() {
        assertTrue(inbox_first_frame_ready(state_matches_folder = true, has_rows = true, cache_pending = true, rows_deriving = false))
    }

    @Test
    fun inbox_with_empty_cache_is_ready_once_the_read_finishes() {
        assertFalse(inbox_first_frame_ready(state_matches_folder = true, has_rows = false, cache_pending = true, rows_deriving = false))
        assertTrue(inbox_first_frame_ready(state_matches_folder = true, has_rows = false, cache_pending = false, rows_deriving = false))
    }

    @Test
    fun inbox_is_not_ready_while_rows_derive_or_folder_differs() {
        assertFalse(inbox_first_frame_ready(state_matches_folder = true, has_rows = false, cache_pending = false, rows_deriving = true))
        assertFalse(inbox_first_frame_ready(state_matches_folder = false, has_rows = true, cache_pending = false, rows_deriving = false))
    }

    @Test
    fun draw_waits_for_insets_and_content() {
        assertFalse(first_frame_may_draw(insets_applied = false, content_ready = true, elapsed_ms = 0L))
        assertFalse(first_frame_may_draw(insets_applied = true, content_ready = false, elapsed_ms = 0L))
        assertTrue(first_frame_may_draw(insets_applied = true, content_ready = true, elapsed_ms = 0L))
    }

    @Test
    fun content_hold_is_capped() {
        assertFalse(first_frame_may_draw(insets_applied = true, content_ready = false, elapsed_ms = first_frame_hold_cap_ms - 1))
        assertTrue(first_frame_may_draw(insets_applied = true, content_ready = false, elapsed_ms = first_frame_hold_cap_ms))
    }

    @Test
    fun inset_hold_is_capped() {
        assertFalse(first_frame_may_draw(insets_applied = false, content_ready = true, elapsed_ms = first_frame_inset_cap_ms - 1))
        assertTrue(first_frame_may_draw(insets_applied = false, content_ready = false, elapsed_ms = first_frame_inset_cap_ms))
    }

    @Test
    fun content_cap_is_shorter_than_inset_cap() {
        assertTrue(first_frame_hold_cap_ms <= first_frame_inset_cap_ms)
    }

    @Test
    fun gate_is_ready_until_armed_and_after_marked() {
        assertTrue(first_frame_gate.is_ready)
        first_frame_gate.arm()
        assertFalse(first_frame_gate.is_ready)
        first_frame_gate.mark_ready()
        assertTrue(first_frame_gate.is_ready)
    }
}
