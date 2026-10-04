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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPixelHighlightTest {

    @Test
    fun markers_are_hidden_by_default() {
        val state = TrackingPixelHighlight()

        assertFalse(state.list_open)
        assertNull(state.status_count)
    }

    @Test
    fun markers_show_while_the_tracker_list_is_open_and_hide_when_it_closes() {
        val open = TrackingPixelHighlight().open()

        assertTrue(open.list_open)
        assertNull(open.status_count)

        val counted = open.counted(3)
        assertTrue(counted.list_open)
        assertEquals(3, counted.status_count)

        val closed = counted.close()
        assertFalse(closed.list_open)
        assertNull(closed.status_count)
    }

    @Test
    fun the_status_line_counts_only_drawn_markers() {
        assertNull(TrackingPixelHighlight().open().counted(0).status_count)
        assertEquals(1, TrackingPixelHighlight().open().counted(1).status_count)
    }

    @Test
    fun a_count_arriving_after_the_list_closed_is_ignored() {
        val late = TrackingPixelHighlight().open().close().counted(3)

        assertFalse(late.list_open)
        assertNull(late.drawn)
        assertNull(late.status_count)
    }

    @Test
    fun reopening_the_list_starts_a_fresh_count() {
        val reopened = TrackingPixelHighlight().open().counted(3).close().open()

        assertTrue(reopened.list_open)
        assertNull(reopened.status_count)
    }

    @Test
    fun the_dot_is_small_and_two_toned() {
        assertEquals(6.0, TrackingPixelDot.SIZE_PX, 0.0)
        assertTrue(TrackingPixelDot.OUTER_RING_PX > TrackingPixelDot.INNER_RING_PX)
        assertEquals(
            "0 0 0 1px rgba(255,255,255,0.9),0 0 0 2px rgba(0,0,0,0.35)",
            TrackingPixelDot.box_shadow(),
        )
    }

    @Test
    fun the_dot_meets_non_text_contrast_on_light_and_dark_emails() {
        for (background in listOf("#ffffff", "#f3f4f6", "#000000", "#0a0a0a", "#111827", "#1f1f1f")) {
            val ratio = TrackingPixelDot.contrast_ratio(TrackingPixelDot.FILL, background)!!
            assertTrue("$background gives $ratio", ratio >= 3.0)
        }
    }

    @Test
    fun the_light_ring_separates_the_dot_from_mid_tone_backgrounds() {
        assertTrue(TrackingPixelDot.contrast_ratio(TrackingPixelDot.FILL, "#808080")!! < 3.0)
        assertTrue(TrackingPixelDot.contrast_ratio(TrackingPixelDot.INNER_RING, TrackingPixelDot.FILL)!! >= 3.0)
        assertTrue(TrackingPixelDot.contrast_ratio(TrackingPixelDot.INNER_RING, "#808080")!! >= 3.0)
    }
}
