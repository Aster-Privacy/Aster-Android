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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class toast_position_test {
    @Test
    fun top_values_map_to_the_top_edge() {
        listOf("top", "top-left", "top-right", "top-center", "  top  ").forEach {
            assertEquals(it, ToastEdge.top, toast_edge_for(it))
        }
    }

    @Test
    fun only_an_explicit_bottom_corner_maps_to_the_bottom_edge() {
        listOf("bottom-center", "bottom-left", "bottom-right").forEach {
            assertEquals(it, ToastEdge.bottom, toast_edge_for(it))
        }
        listOf("bottom", "middle", "", "  ", "TOP", "sideways", null).forEach {
            assertEquals(it.toString(), ToastEdge.top, toast_edge_for(it))
        }
    }

    @Test
    fun the_default_is_the_top_edge() {
        assertEquals("top", default_toast_position)
        assertEquals(ToastEdge.top, toast_edge_for(default_toast_position))
    }

    @Test
    fun the_picker_shows_only_top_and_bottom() {
        assertEquals(listOf("top", "bottom-center"), toast_position_choices)
        assertEquals("top", toast_position_choice_for("top-right"))
        assertEquals("bottom-center", toast_position_choice_for("bottom-left"))
        assertEquals("top", toast_position_choice_for("unknown"))
    }

    @Test
    fun picking_the_same_edge_keeps_the_stored_value() {
        assertEquals("top-left", toast_position_after_pick("top-left", "top"))
        assertEquals("bottom-right", toast_position_after_pick("bottom-right", "bottom-center"))
        assertEquals("middle", toast_position_after_pick("middle", "top"))
    }

    @Test
    fun picking_the_other_edge_writes_the_plain_choice() {
        assertEquals("top", toast_position_after_pick("bottom-right", "top"))
        assertEquals("bottom-center", toast_position_after_pick("top-left", "bottom-center"))
        assertEquals("top", toast_position_after_pick(null, "top"))
    }

    @Test
    fun an_unknown_pick_changes_nothing() {
        assertEquals("top-left", toast_position_after_pick("top-left", "left"))
    }

    @Test
    fun toasts_hide_past_their_own_edge() {
        assertEquals(-124f, toast_hidden_offset(ToastEdge.top, 100f, 24f), 0.001f)
        assertEquals(124f, toast_hidden_offset(ToastEdge.bottom, 100f, 24f), 0.001f)
    }

    @Test
    fun a_swipe_dismisses_only_toward_the_edge() {
        assertTrue(toast_swipe_dismisses(ToastEdge.top, -60f, 0f, 44f, 420f))
        assertFalse(toast_swipe_dismisses(ToastEdge.top, 60f, 0f, 44f, 420f))
        assertTrue(toast_swipe_dismisses(ToastEdge.top, -10f, -500f, 44f, 420f))
        assertTrue(toast_swipe_dismisses(ToastEdge.bottom, 60f, 0f, 44f, 420f))
        assertFalse(toast_swipe_dismisses(ToastEdge.bottom, -60f, -900f, 44f, 420f))
        assertTrue(toast_swipe_dismisses(ToastEdge.bottom, 10f, 500f, 44f, 420f))
    }

    @Test
    fun a_drag_away_from_the_edge_is_resisted() {
        assertEquals(-50f, toast_drag_translation(ToastEdge.top, -50f, 0.32f), 0.001f)
        assertEquals(16f, toast_drag_translation(ToastEdge.top, 50f, 0.32f), 0.001f)
        assertEquals(50f, toast_drag_translation(ToastEdge.bottom, 50f, 0.32f), 0.001f)
        assertEquals(-16f, toast_drag_translation(ToastEdge.bottom, -50f, 0.32f), 0.001f)
    }
}
