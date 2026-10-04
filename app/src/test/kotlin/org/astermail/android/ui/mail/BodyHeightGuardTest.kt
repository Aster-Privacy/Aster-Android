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
}
