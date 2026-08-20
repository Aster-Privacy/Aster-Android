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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Reproduces the "I have to tap an email three or four times before it gets
// selected" report. While the list is still settling from a fling, the
// scrollable ancestor consumes the press at the initial pointer pass, and a
// press held past the long press timeout was cancelled by the drag select
// gesture, so in both cases the row never registered the tap.
//
@RunWith(AndroidJUnit4::class)
class InboxSelectionScrollTapTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val selection_log = mutableListOf<String>()
    private var scrolling_state: () -> Boolean = { false }

    private fun sample(id: String) = Email(
        id = id,
        sender_name = "Aster Team",
        sender_email = "hello@astermail.org",
        subject = "Subject $id",
        preview = "Preview body for $id",
        received_at = 1_700_000_000_000L,
        is_read = false,
        is_starred = false,
        has_attachment = false,
    )

    private fun set_list_content() {
        selection_log.clear()
        val threads = flat_thread_rows((1..60).map { sample("m$it") })
        compose_rule.mainClock.autoAdvance = false
        compose_rule.setContent {
            AsterTheme {
                val selected = remember { mutableStateListOf<String>() }
                val list_state = rememberLazyListState()
                scrolling_state = { list_state.isScrollInProgress }
                LazyColumn(
                    state = list_state,
                    modifier = Modifier.fillMaxSize().testTag("list"),
                ) {
                    items(threads, key = { it.thread_id }) { thread ->
                        ThreadInboxRow(
                            modifier = Modifier.fillMaxWidth(),
                            thread = thread,
                            on_click = {
                                if (selected.contains(thread.thread_id)) {
                                    selected.remove(thread.thread_id)
                                } else {
                                    selected.add(thread.thread_id)
                                }
                                selection_log.clear()
                                selection_log.addAll(selected)
                            },
                            on_long_click = {},
                            on_toggle_star = {},
                            is_selected = selected.contains(thread.thread_id),
                            select_mode = true,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun tapping_a_row_while_the_list_is_still_flinging_selects_it() {
        set_list_content()
        compose_rule.mainClock.advanceTimeBy(200)

        compose_rule.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose_rule.mainClock.advanceTimeBy(64)
        val scrolling_at_tap = scrolling_state()

        compose_rule.onNodeWithTag("list").performTouchInput { click() }
        compose_rule.mainClock.advanceTimeBy(400)
        compose_rule.mainClock.autoAdvance = true
        compose_rule.waitForIdle()

        assertTrue("the list was not settling, so the test proves nothing", scrolling_at_tap)
        assertTrue("the tap during the fling was swallowed", selection_log.isNotEmpty())
    }

    @Test
    fun holding_a_row_past_the_long_press_timeout_toggles_it_exactly_once() {
        set_list_content()
        compose_rule.mainClock.advanceTimeBy(200)

        compose_rule.onNodeWithTag("list").performTouchInput { down(center) }
        compose_rule.mainClock.advanceTimeBy(900)
        compose_rule.onNodeWithTag("list").performTouchInput { up() }
        compose_rule.mainClock.advanceTimeBy(400)
        compose_rule.mainClock.autoAdvance = true
        compose_rule.waitForIdle()

        assertEquals("a held tap did not toggle exactly one row", 1, selection_log.size)
    }
}
