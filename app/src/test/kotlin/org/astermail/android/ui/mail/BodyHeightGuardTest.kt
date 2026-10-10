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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyHeightGuardTest {

    private val max_height = 76190

    @Test
    fun a_body_measured_at_the_height_cap_is_measured_again() {
        val guard = body_height_guard()

        assertTrue(guard.should_remeasure_capped(max_height, max_height))
    }

    @Test
    fun an_ordinary_height_is_trusted() {
        val guard = body_height_guard()

        assertFalse(guard.should_remeasure_capped(5664, max_height))
        assertFalse(guard.should_remeasure_capped(max_height - 1, max_height))
    }

    @Test
    fun a_body_that_really_is_that_tall_stops_being_remeasured() {
        val guard = body_height_guard(max_capped_remeasures = 2)

        assertTrue(guard.should_remeasure_capped(max_height, max_height))
        assertTrue(guard.should_remeasure_capped(max_height, max_height))
        assertFalse(guard.should_remeasure_capped(max_height, max_height))
    }

    @Test
    fun a_new_layout_width_invalidates_the_measured_height() {
        assertTrue(body_width_changed(1, 1028))
        assertTrue(body_width_changed(1028, 2232))
        assertFalse(body_width_changed(0, 1028))
        assertFalse(body_width_changed(1028, 1028))
        assertFalse(body_width_changed(1028, 0))
    }

    @Test
    fun a_layout_narrower_than_any_phone_is_not_measured() {
        assertFalse(body_width_measurable(0, 2.625f))
        assertFalse(body_width_measurable(3, 2.625f))
        assertFalse(body_width_measurable(300, 2.625f))
        assertTrue(body_width_measurable(1028, 2.625f))
        assertTrue(body_width_measurable(320, 1f))
    }

    @Test
    fun growth_by_the_same_factor_each_pass_is_rejected() {
        val guard = body_growth_guard()

        assertEquals(208, guard.settle(179, 208))
        assertEquals(241, guard.settle(208, 241))
        assertEquals(179, guard.settle(241, 279))
        assertEquals(179, guard.settle(179, 208))
    }

    @Test
    fun a_body_that_grows_once_keeps_its_new_height() {
        val guard = body_growth_guard()

        assertEquals(838, guard.settle(106, 838))
        assertEquals(838, guard.settle(838, 838))
        assertEquals(400, guard.settle(838, 400))
    }

    @Test
    fun uneven_growth_from_late_images_is_accepted() {
        val guard = body_growth_guard()

        assertEquals(300, guard.settle(200, 300))
        assertEquals(620, guard.settle(300, 620))
        assertEquals(700, guard.settle(620, 700))
        assertEquals(1900, guard.settle(700, 1900))
    }

    @Test
    fun a_reset_guard_accepts_growth_again() {
        val guard = body_growth_guard()
        guard.settle(179, 208)
        guard.settle(208, 241)
        guard.settle(241, 279)

        guard.reset()

        assertEquals(279, guard.settle(241, 279))
    }

    @Test
    fun a_page_wider_than_the_screen_overflows_sideways() {
        assertTrue(body_overflows_sideways(1727, 1080))
        assertFalse(body_overflows_sideways(1080, 1080))
        assertFalse(body_overflows_sideways(1081, 1080))
        assertFalse(body_overflows_sideways(1727, 0))
    }

    @Test
    fun a_height_that_only_fills_the_stretched_viewport_is_not_a_measurement() {
        assertTrue(body_height_viewport_filled(213, 133, 1727, 1080))
        assertTrue(body_height_viewport_filled(187, 133, 1510, 1080))
        assertFalse(body_height_viewport_filled(900, 133, 1727, 1080))
        assertFalse(body_height_viewport_filled(213, 133, 1080, 1080))
        assertFalse(body_height_viewport_filled(0, 133, 1727, 1080))
        assertFalse(body_height_viewport_filled(213, 0, 1727, 1080))
    }
}
