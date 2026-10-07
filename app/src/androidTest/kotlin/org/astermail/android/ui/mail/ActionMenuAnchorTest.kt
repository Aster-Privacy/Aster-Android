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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionMenuAnchorTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    private fun dp(value: Float) = value * density

    private fun render(alignment: Alignment, direction: LayoutDirection = LayoutDirection.Ltr) {
        compose_rule.setContent {
            var anchor by remember { mutableStateOf<IntRect?>(null) }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AsterTheme {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 96.dp)) {
                        Box(
                            modifier = Modifier
                                .align(alignment)
                                .size(40.dp)
                                .testTag("anchor_button")
                                .onGloballyPositioned { anchor = it.boundsInWindow().roundToIntRect() },
                        )
                        action_menu_sheet(
                            expanded = anchor != null,
                            on_close = {},
                            on_reply = {},
                            on_reply_all = {},
                            on_forward = {},
                            on_star = {},
                            is_starred = false,
                            on_mark_unread = {},
                            on_archive = {},
                            on_trash = {},
                            on_spam = {},
                            anchor = anchor,
                        )
                    }
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun bounds(tag: String): FloatArray {
        val node = compose_rule.onNodeWithTag(tag).fetchSemanticsNode()
        val origin = node.positionOnScreen
        return floatArrayOf(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    @Test
    fun menu_drops_down_from_the_button_aligned_to_its_end() {
        render(Alignment.TopEnd)
        val button = bounds("anchor_button")
        val menu = bounds("action_menu")
        val gap = menu[1] - button[3]
        assertTrue("the menu must open just below the button (gap $gap px)", gap >= 0f && gap <= dp(8f))
        assertTrue(
            "the menu must line up with the button's end (menu right ${menu[2]}, button right ${button[2]})",
            kotlin.math.abs(menu[2] - button[2]) <= dp(2f),
        )
    }

    @Test
    fun menu_flips_above_the_button_when_there_is_no_room_below() {
        render(Alignment.BottomEnd)
        val button = bounds("anchor_button")
        val menu = bounds("action_menu")
        val gap = button[1] - menu[3]
        assertTrue("the menu must open just above the button (gap $gap px)", gap >= 0f && gap <= dp(8f))
        assertTrue(
            "the menu must line up with the button's end (menu right ${menu[2]}, button right ${button[2]})",
            kotlin.math.abs(menu[2] - button[2]) <= dp(2f),
        )
    }

    @Test
    fun menu_lines_up_with_the_button_start_in_right_to_left_layouts() {
        render(Alignment.TopEnd, LayoutDirection.Rtl)
        val button = bounds("anchor_button")
        val menu = bounds("action_menu")
        val gap = menu[1] - button[3]
        assertTrue("the menu must open just below the button (gap $gap px)", gap >= 0f && gap <= dp(8f))
        assertTrue(
            "the menu must line up with the button's left edge (menu left ${menu[0]}, button left ${button[0]})",
            kotlin.math.abs(menu[0] - button[0]) <= dp(2f),
        )
    }
}
