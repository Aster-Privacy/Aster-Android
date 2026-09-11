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

package org.astermail.android.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeHeaderRowsTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private var open_count = 0
    private var long_press_count = 0
    private var cc_expanded by mutableStateOf(false)

    private fun render_header() {
        compose_rule.setContent {
            AsterTheme {
                Column {
                    compose_from_row(
                        address = "shopping@astermail.org",
                        on_open = { open_count++ },
                        on_long_press = { long_press_count++ },
                        avatar = { Box(Modifier.size(24.dp)) },
                    )
                    compose_field_row(
                        label = "To",
                        on_label_click = { cc_expanded = !cc_expanded },
                        label_test_tag = "to_label",
                        trailing = {
                            compose_cc_caret(
                                expanded = cc_expanded,
                                on_toggle = { cc_expanded = !cc_expanded },
                            )
                        },
                    ) {
                        Text("recipient@astermail.org", modifier = Modifier.testTag("to_content"))
                    }
                }
            }
        }
    }

    @Test
    fun from_chevron_opens_the_sender_picker() {
        render_header()
        compose_rule.onNodeWithTag("from_chevron", useUnmergedTree = true).performClick()
        compose_rule.runOnIdle { assertEquals(1, open_count) }
    }

    @Test
    fun from_label_opens_the_sender_picker() {
        render_header()
        compose_rule.onNodeWithText("From").performClick()
        compose_rule.runOnIdle { assertEquals(1, open_count) }
    }

    @Test
    fun taps_near_the_top_and_bottom_edge_of_the_from_row_open_the_picker() {
        render_header()
        val row = compose_rule.onNodeWithTag("from_field")
        row.performTouchInput { click(Offset(right - 4f, top + 2f)) }
        row.performTouchInput { click(Offset(right - 4f, bottom - 2f)) }
        compose_rule.runOnIdle { assertEquals(2, open_count) }
    }

    @Test
    fun from_row_meets_the_minimum_touch_height() {
        render_header()
        compose_rule.onNodeWithTag("from_field")
            .assertHeightIsAtLeast(compose_field_min_height)
    }

    @Test
    fun long_press_on_the_from_row_copies_instead_of_opening() {
        render_header()
        compose_rule.onNodeWithTag("from_field").performTouchInput { longClick() }
        compose_rule.runOnIdle {
            assertEquals(1, long_press_count)
            assertEquals(0, open_count)
        }
    }

    @Test
    fun to_label_toggles_cc_and_bcc() {
        render_header()
        compose_rule.onNodeWithTag("to_label").performClick()
        compose_rule.runOnIdle { assertTrue(cc_expanded) }
        compose_rule.onNodeWithText("To").performClick()
        compose_rule.runOnIdle { assertFalse(cc_expanded) }
    }

    @Test
    fun to_chevron_toggles_cc_and_bcc() {
        render_header()
        compose_rule.onNodeWithTag("cc_toggle").performClick()
        compose_rule.runOnIdle { assertTrue(cc_expanded) }
        compose_rule.onNodeWithTag("cc_toggle").performClick()
        compose_rule.runOnIdle { assertFalse(cc_expanded) }
    }

    @Test
    fun to_chevron_edge_tap_toggles_cc_and_bcc() {
        render_header()
        compose_rule.onNodeWithTag("cc_toggle").performTouchInput { click(Offset(left + 3f, centerY)) }
        compose_rule.runOnIdle { assertTrue(cc_expanded) }
    }

    @Test
    fun to_chevron_and_label_meet_the_minimum_touch_size() {
        render_header()
        compose_rule.onNodeWithTag("cc_toggle")
            .assertWidthIsAtLeast(compose_caret_touch_size)
            .assertHeightIsAtLeast(compose_caret_touch_size)
        compose_rule.onNodeWithTag("to_label")
            .assertHeightIsAtLeast(compose_field_min_height)
    }

    @Test
    fun ime_wait_returns_once_the_keyboard_inset_reaches_zero() = runBlocking {
        val insets = fake_ime_insets(bottom_px = 600)
        val density = Density(1f)
        val waiting = async { wait_for_ime_hidden(insets, density, timeout_ms = 5_000L) }
        delay(100)
        assertFalse(waiting.isCompleted)
        insets.bottom_px = 0
        Snapshot.sendApplyNotifications()
        val started = System.currentTimeMillis()
        waiting.await()
        assertTrue(System.currentTimeMillis() - started < 2_000L)
    }

    @Test
    fun ime_wait_gives_up_after_the_timeout() = runBlocking {
        val insets = fake_ime_insets(bottom_px = 600)
        val started = System.currentTimeMillis()
        wait_for_ime_hidden(insets, Density(1f), timeout_ms = 200L)
        val elapsed = System.currentTimeMillis() - started
        assertTrue(elapsed in 150L..2_000L)
    }

    private class fake_ime_insets(bottom_px: Int) : WindowInsets {
        var bottom_px by mutableIntStateOf(bottom_px)
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getTop(density: Density): Int = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getBottom(density: Density): Int = bottom_px
    }
}
