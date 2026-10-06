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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopToastSwipeTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val message = "Sending in 6s"
    private var state by mutableStateOf<TopToastState?>(null)
    private var dismissals = 0
    private var undos = 0
    private var closes = 0

    private fun toast() = TopToastState(
        message = message,
        undo_label = "Undo",
        on_undo = { undos++ },
        on_close = { closes++ },
        key = 5L,
    )

    private fun render() {
        state = toast()
        compose_rule.setContent {
            AsterTheme {
                top_toast_overlay(
                    state = state,
                    on_dismiss = {
                        dismissals++
                        state = null
                    },
                )
            }
        }
        compose_rule.waitForIdle()
    }

    private fun top(): Float =
        compose_rule.onNodeWithText(message, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun a_swiped_toast_returns_to_its_place_when_it_shows_again_mid_exit() {
        render()
        val resting_top = top()

        compose_rule.mainClock.autoAdvance = false
        compose_rule.onNodeWithText(message, useUnmergedTree = true).performTouchInput {
            swipe(start = center, end = center - Offset(0f, 400f), durationMillis = 120)
        }
        compose_rule.mainClock.advanceTimeBy(200)
        assertNull("the swipe must dismiss the toast", state)
        assertEquals(1, closes)

        compose_rule.mainClock.advanceTimeBy(48)
        state = toast()
        compose_rule.mainClock.advanceTimeBy(2000)
        compose_rule.mainClock.autoAdvance = true
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText(message, useUnmergedTree = true).assertIsDisplayed()
        assertEquals("the toast must come back to its resting place", resting_top, top(), 1f)
    }

    @Test
    fun a_double_tap_on_undo_fires_once() {
        render()
        compose_rule.mainClock.autoAdvance = false
        val undo = compose_rule.onNodeWithText("Undo", useUnmergedTree = true)
        undo.performClick()
        undo.performClick()
        compose_rule.mainClock.autoAdvance = true
        compose_rule.waitForIdle()

        assertEquals(1, undos)
        assertEquals(1, dismissals)
    }

    @Test
    fun close_reports_once_and_dismisses() {
        render()
        compose_rule.onNodeWithContentDescription("Close", useUnmergedTree = true).performClick()
        compose_rule.waitForIdle()

        assertEquals(1, closes)
        assertEquals(1, dismissals)
        assertNull(state)
    }
}
