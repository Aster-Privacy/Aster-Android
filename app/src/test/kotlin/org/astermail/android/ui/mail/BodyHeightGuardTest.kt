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
import org.junit.Assert.assertNull
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
    fun a_zoomed_body_grows_with_the_zoom_ratio_up_to_the_general_cap() {
        assertEquals(30000f, zoomed_body_height_dp(15000f, 2f, max_height))
        assertEquals(60000f, zoomed_body_height_dp(15000f, 4f, max_height))
        assertEquals(max_height.toFloat(), zoomed_body_height_dp(30000f, 3f, max_height))
        assertEquals(1f, zoomed_body_height_dp(0f, 2f, max_height))
        assertEquals(1f, zoomed_body_height_dp(500f, 2f, 0))
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

    @Test
    fun a_page_that_overflows_sideways_gets_a_viewport_wide_enough_to_hold_it() {
        assertEquals(492, overflow_fit_width(scroll_range = 1470, scroll_extent = 1080, scale = 3f, current_fit = null))
        assertEquals(1600, overflow_fit_width(scroll_range = 9000, scroll_extent = 1080, scale = 3f, current_fit = null))
    }

    @Test
    fun a_page_that_fits_or_barely_overflows_keeps_its_viewport() {
        assertNull(overflow_fit_width(scroll_range = 1080, scroll_extent = 1080, scale = 3f, current_fit = null))
        assertNull(overflow_fit_width(scroll_range = 1089, scroll_extent = 1080, scale = 3f, current_fit = null))
        assertNull(overflow_fit_width(scroll_range = 1470, scroll_extent = 1080, scale = 0f, current_fit = null))
    }

    @Test
    fun a_fitted_viewport_only_grows() {
        assertNull(overflow_fit_width(scroll_range = 1470, scroll_extent = 1080, scale = 3f, current_fit = 492))
        assertNull(overflow_fit_width(scroll_range = 1470, scroll_extent = 1080, scale = 3f, current_fit = 600))
        assertEquals(602, overflow_fit_width(scroll_range = 1800, scroll_extent = 1080, scale = 3f, current_fit = 492))
    }

    @Test
    fun inline_images_are_kept_when_attachments_briefly_disappear() {
        val current = mapOf("logo" to "data:a", "sig" to "data:b")

        assertTrue(keeps_inline_images(current, emptyMap(), same_html = true))
        assertTrue(keeps_inline_images(current, mapOf("logo" to "data:a"), same_html = true))
    }

    @Test
    fun inline_images_follow_a_new_body_or_new_sources() {
        val current = mapOf("logo" to "data:a")

        assertFalse(keeps_inline_images(current, emptyMap(), same_html = false))
        assertFalse(keeps_inline_images(current, mapOf("logo" to "data:a", "sig" to "data:b"), same_html = true))
        assertFalse(keeps_inline_images(emptyMap(), mapOf("logo" to "data:a"), same_html = true))
    }

    @Test
    fun a_pending_send_counts_down_then_sends_then_reports_sent() {
        assertEquals(pending_send_phase_queued to 10, pending_send_phase(false, 10_000L, 0L))
        assertEquals(pending_send_phase_queued to 1, pending_send_phase(false, 10_000L, 9_500L))
        assertEquals(pending_send_phase_sending to 0, pending_send_phase(false, 10_000L, 10_000L))
        assertEquals(pending_send_phase_sending to 0, pending_send_phase(false, null, 0L))
        assertEquals(pending_send_phase_sent to 0, pending_send_phase(true, 10_000L, 0L))
    }

    @Test
    fun thread_keys_take_an_alias_only_when_it_is_free() {
        assertEquals(listOf("a", "p1"), thread_item_keys(listOf("a", "srv"), mapOf("srv" to "p1")))
        assertEquals(listOf("srv", "p1"), thread_item_keys(listOf("srv", "p1"), mapOf("srv" to "p1")))
        assertEquals(listOf("p1", "b"), thread_item_keys(listOf("a", "b"), mapOf("a" to "p1", "b" to "p1")))
    }

    @Test
    fun a_finger_tap_that_drifts_past_sixteen_pixels_still_remeasures() {
        assertTrue(body_tap_may_toggle(dx = 20f, dy = 6f, touch_slop_px = 21f, duration_ms = 90, long_press_ms = 400, multi_touch = false))
        assertTrue(body_tap_may_toggle(dx = 30f, dy = 24f, touch_slop_px = 24f, duration_ms = 120, long_press_ms = 400, multi_touch = false))
    }

    @Test
    fun a_still_tap_remeasures() {
        assertTrue(body_tap_may_toggle(dx = 0f, dy = 0f, touch_slop_px = 21f, duration_ms = 60, long_press_ms = 400, multi_touch = false))
    }

    @Test
    fun a_drag_long_press_or_pinch_does_not_remeasure() {
        assertFalse(body_tap_may_toggle(dx = 0f, dy = 120f, touch_slop_px = 21f, duration_ms = 90, long_press_ms = 400, multi_touch = false))
        assertFalse(body_tap_may_toggle(dx = 2f, dy = 2f, touch_slop_px = 21f, duration_ms = 400, long_press_ms = 400, multi_touch = false))
        assertFalse(body_tap_may_toggle(dx = 2f, dy = 2f, touch_slop_px = 21f, duration_ms = 90, long_press_ms = 400, multi_touch = true))
    }
}
