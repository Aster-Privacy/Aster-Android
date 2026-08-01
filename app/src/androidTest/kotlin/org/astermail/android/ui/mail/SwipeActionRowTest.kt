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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import compose.icons.TablerIcons
import compose.icons.tablericons.Archive
import compose.icons.tablericons.Trash
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class SwipeActionRowTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private var start_fired = 0
    private var end_fired = 0
    private var legacy_fired = 0
    private var first_visible = 0
    private val generation = mutableStateOf(0)

    private val fast_diagonal_flick: TouchInjectionScope.() -> Unit = {
        down(center)
        for (step in 1..3) {
            advanceEventTime(8)
            moveTo(center.copy(x = center.x + step * 80f, y = center.y - step * 110f))
        }
        advanceEventTime(8)
        up()
    }

    private val shallow_diagonal_drag: TouchInjectionScope.() -> Unit = {
        down(center)
        for (step in 1..12) {
            advanceEventTime(16)
            moveTo(center.copy(x = center.x + step * 40f, y = center.y - step * 30f))
        }
        advanceEventTime(16)
        up()
    }

    private val shallow_fast_flick: TouchInjectionScope.() -> Unit = {
        down(center)
        for (step in 1..4) {
            advanceEventTime(6)
            moveTo(center.copy(x = center.x + step * 120f, y = center.y - step * 60f))
        }
        advanceEventTime(6)
        up()
    }

    private val steep_diagonal_scroll: TouchInjectionScope.() -> Unit = {
        down(center)
        for (step in 1..12) {
            advanceEventTime(12)
            moveTo(center.copy(x = center.x + step * 14f, y = center.y - step * 46f))
        }
        advanceEventTime(12)
        up()
    }

    private val horizontal_drift_then_scroll: TouchInjectionScope.() -> Unit = {
        down(center)
        advanceEventTime(16)
        moveTo(center.copy(x = center.x + 34f, y = center.y - 6f))
        for (step in 1..10) {
            advanceEventTime(16)
            moveTo(center.copy(x = center.x + 34f + step * 3f, y = center.y - 6f - step * 52f))
        }
        advanceEventTime(16)
        up()
    }

    private val scroll_gestures = listOf(
        "fast_diagonal_flick" to fast_diagonal_flick,
        "shallow_diagonal_drag" to shallow_diagonal_drag,
        "shallow_fast_flick" to shallow_fast_flick,
        "steep_diagonal_scroll" to steep_diagonal_scroll,
        "horizontal_drift_then_scroll" to horizontal_drift_then_scroll,
    )

    private val gestures_that_misfire_on_the_legacy_box = listOf(
        "fast_diagonal_flick" to fast_diagonal_flick,
        "shallow_diagonal_drag" to shallow_diagonal_drag,
        "shallow_fast_flick" to shallow_fast_flick,
    )

    private fun render_rows() {
        start_fired = 0
        end_fired = 0
        first_visible = 0
        compose_rule.setContent {
            AsterTheme {
                key(generation.value) {
                    val list_state = rememberLazyListState()
                    val rows = remember { (0 until 40).toList() }
                    first_visible = list_state.firstVisibleItemIndex
                    LazyColumn(
                        state = list_state,
                        modifier = Modifier.fillMaxSize().testTag("list"),
                    ) {
                        items(rows, key = { it }) { index ->
                            swipe_action_row(
                                start_action = "archive",
                                end_action = "toggle_read",
                                start_label = "Archive",
                                end_label = "Read",
                                start_icon = TablerIcons.Archive,
                                end_icon = TablerIcons.Trash,
                                start_color = Color.Blue,
                                end_color = Color.Red,
                                on_swipe_start = { start_fired++ },
                                on_swipe_end = { end_fired++ },
                                haptic_enabled = false,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .background(Color.DarkGray)
                                        .testTag("row_$index"),
                                ) {
                                    Text("row $index")
                                }
                            }
                        }
                    }
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun render_legacy_rows() {
        legacy_fired = 0
        compose_rule.setContent {
            AsterTheme {
                key(generation.value) {
                    val list_state = rememberLazyListState()
                    val rows = remember { (0 until 40).toList() }
                    LazyColumn(
                        state = list_state,
                        modifier = Modifier.fillMaxSize().testTag("list"),
                    ) {
                        items(rows, key = { it }) { index ->
                            val state = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it != SwipeToDismissBoxValue.Settled) legacy_fired++
                                    false
                                },
                            )
                            SwipeToDismissBox(state = state, backgroundContent = {}) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .background(Color.DarkGray)
                                        .testTag("row_$index"),
                                ) {
                                    Text("row $index")
                                }
                            }
                        }
                    }
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun run_gesture(tag: String, gesture: TouchInjectionScope.() -> Unit) {
        compose_rule.runOnUiThread { generation.value += 1 }
        compose_rule.waitForIdle()
        start_fired = 0
        end_fired = 0
        legacy_fired = 0
        compose_rule.onNodeWithTag(tag).performTouchInput(gesture)
        compose_rule.waitForIdle()
    }

    @Test
    fun control_the_legacy_swipe_box_really_does_misfire_on_these_scroll_gestures() {
        render_legacy_rows()

        gestures_that_misfire_on_the_legacy_box.forEach { (name, gesture) ->
            run_gesture("list", gesture)
            assertTrue(
                "control is vacuous: $name no longer reproduces the bug on SwipeToDismissBox",
                legacy_fired > 0,
            )
        }
    }

    @Test
    fun no_scroll_gesture_fires_a_swipe_action() {
        render_rows()

        scroll_gestures.forEach { (name, gesture) ->
            run_gesture("list", gesture)
            assertEquals("$name fired a start action while scrolling", 0, start_fired)
            assertEquals("$name fired an end action while scrolling", 0, end_fired)
        }
    }

    @Test
    fun a_vertical_scroll_still_scrolls_the_list() {
        render_rows()

        run_gesture("list", steep_diagonal_scroll)

        assertTrue("list should have scrolled", first_visible > 0)
    }

    @Test
    fun a_deliberate_horizontal_drag_past_the_threshold_fires_the_action() {
        render_rows()

        run_gesture("row_1") {
            down(center)
            for (step in 1..12) {
                advanceEventTime(16)
                moveTo(center.copy(x = center.x + step * (width * 0.06f)))
            }
            advanceEventTime(16)
            up()
        }

        assertEquals(1, start_fired)
        assertEquals(0, end_fired)
        assertEquals("list should not have scrolled", 0, first_visible)
    }

    @Test
    fun a_deliberate_horizontal_drag_the_other_way_fires_the_end_action() {
        render_rows()

        run_gesture("row_1") {
            down(center)
            for (step in 1..12) {
                advanceEventTime(16)
                moveTo(center.copy(x = center.x - step * (width * 0.06f)))
            }
            advanceEventTime(16)
            up()
        }

        assertEquals(0, start_fired)
        assertEquals(1, end_fired)
    }

    @Test
    fun a_short_horizontal_drag_below_the_threshold_snaps_back_without_firing() {
        render_rows()

        run_gesture("row_1") {
            down(center)
            for (step in 1..6) {
                advanceEventTime(16)
                moveTo(center.copy(x = center.x + step * (width * 0.03f)))
            }
            advanceEventTime(16)
            up()
        }

        assertEquals(0, start_fired)
        assertEquals(0, end_fired)
    }

    @Test
    fun a_fast_horizontal_flick_that_barely_moves_does_not_fire() {
        render_rows()

        run_gesture("row_1") {
            down(center)
            advanceEventTime(8)
            moveTo(center.copy(x = center.x + width * 0.12f))
            advanceEventTime(8)
            up()
        }

        assertEquals(0, start_fired)
        assertEquals(0, end_fired)
    }
}
