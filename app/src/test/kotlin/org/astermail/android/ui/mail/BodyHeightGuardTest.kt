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
    fun a_page_wider_than_the_screen_overflows_sideways() {
        assertTrue(body_overflows_sideways(1727, 1080))
        assertFalse(body_overflows_sideways(1080, 1080))
        assertFalse(body_overflows_sideways(1081, 1080))
        assertFalse(body_overflows_sideways(1727, 0))
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
    fun a_failed_or_waiting_send_shows_its_state_without_a_countdown() {
        assertEquals(
            pending_send_phase_failed to 0,
            pending_send_display_phase(org.astermail.android.mail.OPTIMISTIC_FAILED_STATUS, 10_000L, 0L),
        )
        assertEquals(
            pending_send_phase_waiting to 0,
            pending_send_display_phase(org.astermail.android.mail.OPTIMISTIC_WAITING_STATUS, 10_000L, 0L),
        )
        assertEquals(
            pending_send_phase_queued to 10,
            pending_send_display_phase(org.astermail.android.mail.OPTIMISTIC_SEND_STATUS, 10_000L, 0L),
        )
        assertEquals(
            pending_send_phase_sent to 0,
            pending_send_display_phase(org.astermail.android.mail.OPTIMISTIC_SENT_STATUS, 10_000L, 0L),
        )
        assertTrue(pending_send_counts_down(org.astermail.android.mail.OPTIMISTIC_SEND_STATUS))
        assertFalse(pending_send_counts_down(org.astermail.android.mail.OPTIMISTIC_FAILED_STATUS))
        assertFalse(pending_send_counts_down(org.astermail.android.mail.OPTIMISTIC_WAITING_STATUS))
        assertFalse(pending_send_counts_down(org.astermail.android.mail.OPTIMISTIC_SENT_STATUS))
    }

    @Test
    fun thread_keys_take_an_alias_only_when_it_is_free() {
        assertEquals(listOf("a", "p1"), thread_item_keys(listOf("a", "srv"), mapOf("srv" to "p1")))
        assertEquals(listOf("srv", "p1"), thread_item_keys(listOf("srv", "p1"), mapOf("srv" to "p1")))
        assertEquals(listOf("p1", "b"), thread_item_keys(listOf("a", "b"), mapOf("a" to "p1", "b" to "p1")))
    }

}
