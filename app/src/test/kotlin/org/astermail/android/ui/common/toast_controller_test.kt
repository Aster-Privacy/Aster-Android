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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class toast_controller_test {
    private val scope = TestScope(StandardTestDispatcher())
    private val controller = ToastController(scope)

    private fun shown(): String? = controller.state.value?.message

    private fun settle(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test
    fun a_toast_shows_at_once_and_times_out() {
        controller.show("saved")
        assertEquals("saved", shown())
        settle(toast_plain_duration_ms - 1)
        assertEquals("saved", shown())
        settle(1)
        assertNull(shown())
    }

    @Test
    fun a_toast_with_an_action_stays_longer() {
        controller.show(TopToastState(message = "archived", undo_label = "Undo", on_undo = {}))
        settle(toast_plain_duration_ms)
        assertEquals("archived", shown())
        settle(toast_action_duration_ms - toast_plain_duration_ms)
        assertNull(shown())
    }

    @Test
    fun a_second_toast_replaces_the_first_after_one_gap() {
        controller.show("first")
        settle(toast_settle_ms)
        controller.show("second")
        assertNull(shown())
        settle(toast_exit_gap_ms)
        assertEquals("second", shown())
    }

    @Test
    fun a_toast_that_only_just_appeared_is_swapped_in_place() {
        controller.show("first")
        settle(toast_settle_ms - 1)
        controller.show("second")
        assertEquals("second", shown())
    }

    @Test
    fun a_burst_of_toasts_never_flickers_and_ends_on_the_latest() {
        repeat(40) { index ->
            controller.show("toast $index")
            assertEquals("toast $index", shown())
            settle(30)
            assertEquals("toast $index", shown())
        }
        settle(toast_plain_duration_ms)
        assertNull(shown())
    }

    @Test
    fun a_replaced_toast_commits_exactly_once() {
        var commits = 0
        controller.show(TopToastState(message = "first", on_timeout = { commits++ }))
        controller.show("second")
        assertEquals(1, commits)
        settle(60_000)
        assertEquals(1, commits)
    }

    @Test
    fun a_timeout_commits_exactly_once() {
        var commits = 0
        controller.show(TopToastState(message = "first", on_timeout = { commits++ }))
        settle(60_000)
        assertEquals(1, commits)
    }

    @Test
    fun a_dismissed_toast_does_not_commit() {
        var commits = 0
        val toast = TopToastState(message = "first", on_timeout = { commits++ })
        controller.show(toast)
        controller.dismiss(toast.key)
        settle(60_000)
        assertEquals(0, commits)
        assertNull(shown())
    }

    @Test
    fun a_stale_dismiss_leaves_the_newer_toast_alone() {
        val first = TopToastState(message = "first")
        controller.show(first)
        controller.show("second")
        settle(toast_exit_gap_ms)
        controller.dismiss(first.key)
        assertEquals("second", shown())
    }

    @Test
    fun the_same_key_updates_in_place_and_restarts_the_timer() {
        controller.show(TopToastState(message = "one", key = 7L))
        settle(2000)
        controller.show(TopToastState(message = "two", key = 7L))
        assertEquals("two", shown())
        settle(2000)
        assertEquals("two", shown())
        settle(toast_plain_duration_ms)
        assertNull(shown())
    }

    @Test
    fun accumulating_toasts_merge_without_a_gap_or_a_commit() {
        var commits = 0
        controller.show(TopToastState(message = "1 archived", accumulation_key = "archive", on_timeout = { commits++ }))
        val key = controller.state.value?.key
        controller.show(TopToastState(message = "2 archived", accumulation_key = "archive", on_timeout = { commits++ }))
        assertEquals("2 archived", shown())
        assertEquals(key, controller.state.value?.key)
        assertEquals(0, commits)
        settle(60_000)
        assertEquals(1, commits)
    }

    @Test
    fun a_different_accumulation_replaces_and_commits_the_old_one() {
        var commits = 0
        controller.show(TopToastState(message = "1 archived", accumulation_key = "archive", on_timeout = { commits++ }))
        controller.show(TopToastState(message = "1 deleted", accumulation_key = "trash"))
        assertEquals(1, commits)
        settle(toast_exit_gap_ms)
        assertEquals("1 deleted", shown())
    }

    @Test
    fun dismiss_accumulated_only_removes_accumulating_toasts() {
        controller.show("plain")
        controller.dismiss_accumulated()
        assertEquals("plain", shown())
        controller.show(TopToastState(message = "1 archived", accumulation_key = "archive"))
        settle(toast_exit_gap_ms)
        controller.dismiss_accumulated()
        assertNull(shown())
    }

    @Test
    fun a_sticky_toast_has_no_timer_and_updates_in_place() {
        controller.show_sticky(TopToastState(message = "Sending in 10s", key = 99L))
        settle(60_000)
        assertEquals("Sending in 10s", shown())
        controller.show_sticky(TopToastState(message = "Sending in 9s", key = 99L))
        assertEquals("Sending in 9s", shown())
        controller.hide_sticky(99L)
        assertNull(shown())
    }

    @Test
    fun a_toast_covers_the_sticky_one_and_then_gives_it_back() {
        controller.show_sticky(TopToastState(message = "Sending in 10s", key = 99L))
        settle(toast_settle_ms)
        controller.show("marked as unread")
        assertNull(shown())
        settle(toast_exit_gap_ms)
        assertEquals("marked as unread", shown())
        controller.show_sticky(TopToastState(message = "Sending in 9s", key = 99L))
        assertEquals("marked as unread", shown())
        settle(toast_plain_duration_ms - toast_exit_gap_ms)
        assertNull(shown())
        settle(toast_exit_gap_ms)
        assertEquals("Sending in 9s", shown())
    }

    @Test
    fun hiding_the_sticky_toast_under_another_toast_changes_nothing_visible() {
        controller.show_sticky(TopToastState(message = "Sending in 10s", key = 99L))
        controller.show("marked as unread")
        settle(toast_exit_gap_ms)
        controller.hide_sticky(99L)
        assertEquals("marked as unread", shown())
        settle(toast_plain_duration_ms + toast_exit_gap_ms)
        assertNull(shown())
    }

    @Test
    fun dismissing_the_sticky_toast_removes_it() {
        controller.show_sticky(TopToastState(message = "Sending in 10s", key = 99L))
        controller.dismiss(99L)
        assertNull(shown())
        settle(toast_exit_gap_ms)
        assertNull(shown())
    }

    @Test
    fun holding_a_toast_pauses_its_timer() {
        val toast = TopToastState(message = "held")
        controller.show(toast)
        settle(1000)
        controller.hold(toast.key, true)
        settle(60_000)
        assertEquals("held", shown())
        controller.hold(toast.key, false)
        settle(toast_plain_duration_ms - 1)
        assertEquals("held", shown())
        settle(1)
        assertNull(shown())
    }

    @Test
    fun a_stale_hold_release_does_not_affect_the_newer_toast() {
        val first = TopToastState(message = "first")
        controller.show(first)
        controller.hold(first.key, true)
        controller.show("second")
        controller.hold(first.key, false)
        settle(toast_exit_gap_ms)
        assertEquals("second", shown())
        settle(toast_plain_duration_ms)
        assertNull(shown())
    }

    @Test
    fun a_toast_after_a_dismiss_waits_for_the_exit() {
        val first = TopToastState(message = "first")
        controller.show(first)
        controller.dismiss(first.key)
        controller.show("second")
        assertNull(shown())
        settle(toast_exit_gap_ms)
        assertEquals("second", shown())
    }

    @Test
    fun a_delayed_toast_shows_after_the_transition() {
        controller.show_after_transition("discarded")
        assertNull(shown())
        settle(toast_transition_delay_ms)
        assertEquals("discarded", shown())
    }

    @Test
    fun a_newer_toast_cancels_a_delayed_one() {
        controller.show_after_transition("discarded")
        controller.show("saved")
        settle(toast_transition_delay_ms + toast_exit_gap_ms)
        assertEquals("saved", shown())
    }

    @Test
    fun default_keys_never_collide() {
        val keys = List(1000) { TopToastState(message = "x").key }
        assertEquals(1000, keys.toSet().size)
    }
}
