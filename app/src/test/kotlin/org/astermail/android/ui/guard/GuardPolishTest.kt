//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.ui.guard

import androidx.compose.ui.graphics.vector.PathParser
import org.astermail.android.R
import org.astermail.android.design.dark_semantic_colors
import org.astermail.android.design.light_semantic_colors
import org.astermail.android.guard.guard_held_tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardPolishTest {

    @Test
    fun setup_steps_count_from_one() {
        assertEquals(1, guard_setup_step_number(guard_setup_step.intro))
        assertEquals(2, guard_setup_step_number(guard_setup_step.choose))
        assertEquals(3, guard_setup_step_number(guard_setup_step.rescue))
        assertEquals(4, guard_setup_step_number(guard_setup_step.confirm_rescue))
        assertEquals(GUARD_SETUP_STEPS, guard_setup_step_number(guard_setup_step.password))
        assertEquals(0, guard_setup_step_number(guard_setup_step.done))
        assertEquals(0, guard_setup_step_number(guard_setup_step.join_intro))
    }

    @Test
    fun close_is_hidden_while_the_rescue_code_is_on_screen() {
        assertTrue(guard_setup_can_close(guard_setup_step.intro))
        assertTrue(guard_setup_can_close(guard_setup_step.choose))
        assertTrue(guard_setup_can_close(guard_setup_step.password))
        assertTrue(guard_setup_can_close(guard_setup_step.rescue))
        assertTrue(guard_setup_can_close(guard_setup_step.confirm_rescue))
        assertFalse(guard_setup_can_close(guard_setup_step.working))
        assertFalse(guard_setup_can_close(guard_setup_step.done))
    }

    @Test
    fun step_segments_fill_up_to_the_current_step() {
        assertTrue(guard_step_filled(0, 1))
        assertFalse(guard_step_filled(1, 1))
        assertTrue(guard_step_filled(4, 5))
        assertFalse(guard_step_filled(0, 0))
    }

    @Test
    fun track_color_stays_visible_in_both_themes() {
        assertTrue(guard_track_color(light_semantic_colors).alpha > 0.2f)
        assertTrue(guard_track_color(dark_semantic_colors).alpha > 0.2f)
    }

    @Test
    fun expiry_tone_warns_in_the_last_minute() {
        val colors = dark_semantic_colors
        assertEquals(colors.text_primary, guard_expiry_tone(colors, 120))
        assertEquals(colors.warning, guard_expiry_tone(colors, 59))
        assertEquals(colors.danger, guard_expiry_tone(colors, 0))
    }

    @Test
    fun denied_sheet_stays_open_when_it_offers_to_secure() {
        assertTrue(guard_denied_auto_closes(auto_close = true, can_secure = false))
        assertFalse(guard_denied_auto_closes(auto_close = true, can_secure = true))
        assertFalse(guard_denied_auto_closes(auto_close = false, can_secure = false))
    }

    @Test
    fun rescue_is_offered_only_when_relevant() {
        assertFalse(guard_wait_offers_rescue(code_open = false, notice = null))
        assertTrue(guard_wait_offers_rescue(code_open = true, notice = null))
        assertTrue(guard_wait_offers_rescue(code_open = false, notice = guard_wait_notice.wrong_code))
    }

    @Test
    fun wait_results_have_titles() {
        assertEquals(R.string.guard_wait_denied_title, guard_wait_result_for(guard_wait_state.denied).title)
        assertEquals(R.string.guard_wait_expired_title, guard_wait_result_for(guard_wait_state.expired).title)
        assertEquals(R.string.guard_wait_retry_title, guard_wait_result_for(guard_wait_state.retry(30)).title)
        assertEquals(R.string.guard_wait_held_title, guard_wait_result_for(guard_wait_state.held("x")).title)
        assertEquals(R.string.guard_wait_failed_title, guard_wait_result_for(guard_wait_state.failed).title)
        assertEquals(R.string.guard_rescue_sign_in, guard_wait_result_for(guard_wait_state.rescue_applied).action)
        assertEquals(R.string.guard_wait_back, guard_wait_result_for(guard_wait_state.denied).action)
        assertEquals(R.string.guard_wait_sign_in_again, guard_wait_result_for(guard_wait_state.expired).action)
        assertEquals(R.string.guard_wait_sign_in_again, guard_wait_result_for(guard_wait_state.failed).action)
        assertEquals(R.string.guard_wait_paused_title, guard_wait_result_for(guard_wait_state.paused(600, 0)).title)
        val err = guard_wait_state.error(org.astermail.android.guard.guard_error_message(R.string.guard_error_disabled))
        assertEquals(R.string.guard_wait_sign_in_again, guard_wait_result_for(err).action)
        assertEquals(R.string.guard_wait_approved_title, guard_wait_title_for(guard_wait_state.approved))
    }

    @Test
    fun stopped_rescue_needs_a_password_route() {
        assertTrue(guard_changes_shows_stopped(guard_changes_notice.change_stopped, true))
        assertFalse(guard_changes_shows_stopped(guard_changes_notice.change_stopped, false))
        assertFalse(guard_changes_shows_stopped(guard_changes_notice.canceled, true))
        assertFalse(guard_changes_shows_stopped(null, true))
    }

    @Test
    fun held_tone_is_warning() {
        assertEquals(light_semantic_colors.warning, guard_held_tone(light_semantic_colors))
        assertEquals(dark_semantic_colors.warning, guard_held_tone(dark_semantic_colors))
    }

    @Test
    fun mark_paths_fit_the_24_grid() {
        assertEquals(4, GUARD_MARK_PATHS.size)
        for (d in GUARD_MARK_PATHS) {
            val nodes = PathParser().parsePathString(d).toNodes()
            assertTrue(nodes.isNotEmpty())
        }
        assertEquals(24f, guard_mark_icon.viewportWidth)
        assertEquals(24f, guard_mark_icon.viewportHeight)
    }

    @Test
    fun connector_ends_at_the_middle_row() {
        val shape = guard_connector_geometry(
            width = 300f, height = 60f, line_x = 28f, end_x = 52f, radius = 6f, top = 0f, last = false, rtl = false,
        )
        assertEquals(28f, shape.line_x)
        assertEquals(30f, shape.elbow_y)
        assertEquals(52f, shape.end_x)
        assertEquals(60f, shape.bottom)
        assertEquals(6f, shape.radius)
        assertEquals(1f, shape.direction)
    }

    @Test
    fun last_connector_stops_at_the_elbow() {
        val shape = guard_connector_geometry(300f, 60f, 28f, 52f, 6f, 0f, last = true, rtl = false)
        assertNull(shape.bottom)
    }

    @Test
    fun connector_mirrors_for_rtl() {
        val shape = guard_connector_geometry(300f, 60f, 28f, 52f, 6f, 0f, last = false, rtl = true)
        assertEquals(272f, shape.line_x)
        assertEquals(248f, shape.end_x)
        assertEquals(-1f, shape.direction)
    }

    @Test
    fun connector_radius_shrinks_in_short_rows() {
        val shape = guard_connector_geometry(300f, 8f, 28f, 52f, 6f, 0f, last = false, rtl = false)
        assertEquals(4f, shape.radius)
        assertEquals(2.5f, guard_connector_width.value)
        assertEquals(0.6f, guard_connector_color(light_semantic_colors).alpha, 0.01f)
        assertEquals(0.6f, guard_connector_color(dark_semantic_colors).alpha, 0.01f)
    }
}
