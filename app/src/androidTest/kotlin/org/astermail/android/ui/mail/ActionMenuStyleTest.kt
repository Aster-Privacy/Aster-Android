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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionMenuStyleTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private var closed = 0
    private var replied = 0

    private fun render(is_starred: Boolean = false, is_spam: Boolean = false) {
        compose_rule.setContent {
            var expanded by remember { mutableStateOf(true) }
            AsterTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    action_menu_sheet(
                        expanded = expanded,
                        on_close = { closed++; expanded = false },
                        on_reply = { replied++ },
                        on_reply_all = {},
                        on_forward = {},
                        on_star = {},
                        is_starred = is_starred,
                        on_mark_unread = {},
                        on_archive = {},
                        on_trash = {},
                        on_spam = {},
                        is_spam = is_spam,
                    )
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun screen(): Pair<Float, Float> {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        return metrics.widthPixels.toFloat() to metrics.heightPixels.toFloat()
    }

    @Test
    fun menu_is_never_full_width() {
        render()
        val menu = compose_rule.onNodeWithTag("action_menu").fetchSemanticsNode().size.width
        val (width, _) = screen()
        assertTrue("the action menu must not span the whole screen ($menu of $width)", menu < width * 0.9f)
        assertTrue("the action menu must stay comfortably wide ($menu of $width)", menu > width * 0.4f)
    }

    @Test
    fun menu_sits_at_the_bottom_end_corner() {
        render()
        val menu = compose_rule.onNodeWithTag("action_menu").fetchSemanticsNode().boundsInWindow
        val (width, height) = screen()
        assertTrue(
            "the menu must hug the right edge (right ${menu.right} of $width)",
            width - menu.right < width * 0.12f,
        )
        assertTrue(
            "the menu must open upward from the toolbar (bottom ${menu.bottom} of $height)",
            height - menu.bottom < height * 0.2f,
        )
    }

    @Test
    fun menu_lists_every_action_and_dispatches_taps() {
        render()
        listOf(
            "Reply",
            "Reply all",
            "Forward",
            "Star",
            "Mark as unread",
            "Label",
            "Snooze",
            "Archive",
            "Report spam",
            "Move to trash",
            "Customize toolbar",
        ).forEach { label ->
            compose_rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }

        compose_rule.onNodeWithText("Reply").performScrollTo().performClick()
        compose_rule.waitForIdle()
        assertEquals("tapping an item must fire its action", 1, replied)
    }

    @Test
    fun starred_and_spam_states_flip_their_labels() {
        render(is_starred = true, is_spam = true)
        compose_rule.onNodeWithText("Unstar").assertIsDisplayed()
        compose_rule.onNodeWithText("Not spam").assertIsDisplayed()
    }
}
