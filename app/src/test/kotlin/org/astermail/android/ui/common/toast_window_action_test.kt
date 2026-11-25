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
    fun an_ended_toast_keeps_the_window_attached() {
        assertEquals(
            ToastWindowAction.none,
            toast_window_action(attached = true, attached_generation = 4, generation = 4, wants_window = false),
        )
    }

    @Test
    fun a_popup_raise_waits_while_a_card_is_on_screen() {
        assertEquals(
            ToastWindowAction.none,
            toast_window_action(attached = true, attached_generation = 4, generation = 5, wants_window = true, idle = false),
        )
        assertEquals(
            ToastWindowAction.raise,
            toast_window_action(attached = true, attached_generation = 4, generation = 5, wants_window = true, idle = true),
        )
    }

    @Test
    fun a_new_edge_or_size_applies_only_once_the_card_is_gone() {
        assertEquals(
            ToastWindowAction.none,
            toast_window_action(attached = true, attached_generation = 4, generation = 4, wants_window = true, idle = false, spec_matches = false),
        )
        assertEquals(
            ToastWindowAction.relayout,
            toast_window_action(attached = true, attached_generation = 4, generation = 4, wants_window = true, idle = true, spec_matches = false),
        )
    }

    private val top_spec = toast_window_spec(ToastEdge.top, height_px = 400, offset_px = 80, secure = false)

    @Test
    fun rapid_shows_keep_one_window_with_the_same_params() {
        var ledger = toast_window_step(toast_window_ledger(), wants_window = true, generation = 3, idle = true, spec = top_spec)
        val first_spec = ledger.spec
        repeat(12) { tap ->
            ledger = toast_window_step(ledger, wants_window = true, generation = 3, idle = tap % 3 == 0, spec = top_spec)
            ledger = toast_window_step(ledger, wants_window = true, generation = 3, idle = false, spec = top_spec)
            ledger = toast_window_step(ledger, wants_window = false, generation = 3, idle = tap % 2 == 0, spec = top_spec)
        }
        assertEquals(1, ledger.adds)
        assertEquals(0, ledger.removes)
        assertEquals(0, ledger.relayouts)
        assertEquals(first_spec, ledger.spec)
    }

    @Test
    fun an_edge_change_mid_toast_never_moves_the_visible_window() {
        val bottom_spec = toast_window_spec(ToastEdge.bottom, height_px = 520, offset_px = 48, secure = false)
        var ledger = toast_window_step(toast_window_ledger(), wants_window = true, generation = 1, idle = true, spec = top_spec)
        repeat(5) {
            ledger = toast_window_step(ledger, wants_window = true, generation = 2, idle = false, spec = bottom_spec)
            assertEquals(top_spec, ledger.spec)
        }
        ledger = toast_window_step(ledger, wants_window = true, generation = 2, idle = true, spec = bottom_spec)
        assertEquals(1, ledger.removes)
        assertEquals(2, ledger.adds)
        assertEquals(bottom_spec, ledger.spec)
    }

    @Test
    fun the_window_anchors_to_the_chosen_edge_at_full_width() {
        assertEquals(0x30 or 0x00800003, toast_window_gravity(ToastEdge.top))
        assertEquals(0x50 or 0x00800003, toast_window_gravity(ToastEdge.bottom))
    }

    private val card = toast_card_box(left = 100f, top = 60f, right = 500f, bottom = 140f)

    @Test
    fun a_touch_on_the_card_goes_to_the_toast() {
        assertEquals(ToastTouchRoute.toast, toast_touch_route(listOf(card), 300f, 100f, popup_layers_open = 0))
        assertEquals(ToastTouchRoute.toast, toast_touch_route(listOf(card), 100f, 60f, popup_layers_open = 2))
    }

    @Test
    fun a_touch_beside_the_card_passes_through_to_the_app() {
        assertEquals(ToastTouchRoute.host, toast_touch_route(listOf(card), 40f, 100f, popup_layers_open = 0))
        assertEquals(ToastTouchRoute.host, toast_touch_route(listOf(card), 500f, 100f, popup_layers_open = 0))
        assertEquals(ToastTouchRoute.host, toast_touch_route(listOf(card), 300f, 140f, popup_layers_open = 0))
    }

    @Test
    fun a_touch_with_no_card_passes_through_to_the_app() {
        assertEquals(ToastTouchRoute.host, toast_touch_route(emptyList(), 300f, 100f, popup_layers_open = 0))
    }

    @Test
    fun a_touch_beside_the_card_over_a_popup_is_never_sent_behind_it() {
        assertEquals(ToastTouchRoute.drop, toast_touch_route(listOf(card), 40f, 100f, popup_layers_open = 1))
    }

    @Test
    fun an_outgoing_and_incoming_card_both_take_touches() {
        val incoming = toast_card_box(left = 150f, top = 60f, right = 450f, bottom = 120f)
        assertEquals(ToastTouchRoute.toast, toast_touch_route(listOf(card, incoming), 460f, 130f, popup_layers_open = 0))
        assertEquals(ToastTouchRoute.toast, toast_touch_route(listOf(incoming), 200f, 100f, popup_layers_open = 0))
    }

    @Test
    fun the_window_ignores_touches_only_while_no_toast_shows() {
        val base = 0x00000008
        assertEquals(base or toast_flag_not_touchable, toast_window_flags(base, touchable = false))
        assertEquals(base, toast_window_flags(base or toast_flag_not_touchable, touchable = true))
        assertEquals(base, toast_window_flags(base, touchable = true))
        assertEquals(
            toast_window_flags(base, touchable = false),
            toast_window_flags(toast_window_flags(base, touchable = false), touchable = false),
        )
    }
}
