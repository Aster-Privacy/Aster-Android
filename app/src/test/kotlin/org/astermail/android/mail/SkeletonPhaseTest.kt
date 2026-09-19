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

import org.astermail.android.ui.mail.SkeletonPhase
import org.astermail.android.ui.mail.initial_skeleton_phase
import org.astermail.android.ui.mail.plan_skeleton_step
import org.astermail.android.ui.mail.skeleton_await_rows_ms
import org.astermail.android.ui.mail.skeleton_fade_out_ms
import org.astermail.android.ui.mail.skeleton_handoff_ms
import org.astermail.android.ui.mail.skeleton_min_visible_ms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkeletonPhaseTest {

    @Test
    fun cached_content_starts_as_content_with_no_skeleton() {
        assertEquals(SkeletonPhase.content, initial_skeleton_phase(wanted = false, rows_imminent = false))
        assertEquals(SkeletonPhase.content, initial_skeleton_phase(wanted = false, rows_imminent = true))
    }

    @Test
    fun a_cold_load_starts_on_the_skeleton() {
        assertEquals(SkeletonPhase.skeleton, initial_skeleton_phase(wanted = true, rows_imminent = false))
    }

    @Test
    fun imminent_rows_start_blank_instead_of_flashing_a_skeleton() {
        assertEquals(SkeletonPhase.blank, initial_skeleton_phase(wanted = true, rows_imminent = true))
    }

    @Test
    fun a_visible_skeleton_stays_put_and_never_replays() {
        val step = plan_skeleton_step(SkeletonPhase.skeleton, wanted = true, rows_imminent = false, skeleton_shown_for_ms = 50L)
        assertEquals(SkeletonPhase.skeleton, step.target)
        assertEquals(0L, step.after_ms)
    }

    @Test
    fun blank_waits_briefly_for_imminent_rows_before_showing_a_skeleton() {
        val step = plan_skeleton_step(SkeletonPhase.blank, wanted = true, rows_imminent = true, skeleton_shown_for_ms = 0L)
        assertEquals(SkeletonPhase.skeleton, step.target)
        assertEquals(skeleton_await_rows_ms, step.after_ms)
    }

    @Test
    fun content_never_snaps_back_to_a_skeleton_immediately() {
        val step = plan_skeleton_step(SkeletonPhase.content, wanted = true, rows_imminent = false, skeleton_shown_for_ms = 0L)
        assertEquals(SkeletonPhase.skeleton, step.target)
        assertTrue(step.after_ms > 0L)
    }

    @Test
    fun a_short_skeleton_holds_until_its_minimum_before_the_handoff() {
        val step = plan_skeleton_step(SkeletonPhase.skeleton, wanted = false, rows_imminent = false, skeleton_shown_for_ms = 100L)
        assertEquals(SkeletonPhase.content, step.target)
        assertEquals(skeleton_min_visible_ms - 100L, step.after_ms)
    }

    @Test
    fun a_long_skeleton_hands_off_at_once() {
        val step = plan_skeleton_step(SkeletonPhase.skeleton, wanted = false, rows_imminent = false, skeleton_shown_for_ms = 5_000L)
        assertEquals(SkeletonPhase.content, step.target)
        assertEquals(0L, step.after_ms)
    }

    @Test
    fun blank_goes_straight_to_content_when_rows_land() {
        val step = plan_skeleton_step(SkeletonPhase.blank, wanted = false, rows_imminent = false, skeleton_shown_for_ms = 0L)
        assertEquals(SkeletonPhase.content, step.target)
        assertEquals(0L, step.after_ms)
    }

    @Test
    fun the_crossfade_is_one_short_matched_fade() {
        assertEquals(skeleton_fade_out_ms, skeleton_handoff_ms)
        assertTrue(skeleton_handoff_ms <= 150)
    }
}
