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

package org.astermail.android.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import compose.icons.TablerIcons
import compose.icons.tablericons.Mail
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopToastLayoutTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val countdown = "Sending in 27s"

    private fun render(on_secondary: () -> Unit = {}) {
        compose_rule.setContent {
            AsterTheme {
                top_toast_overlay(
                    state = TopToastState(
                        message = countdown,
                        undo_label = "Undo",
                        on_undo = {},
                        secondary_label = "View message",
                        secondary_icon = TablerIcons.Mail,
                        on_secondary = on_secondary,
                        show_close = true,
                        duration_ms = 60_000L,
                        key = 1L,
                    ),
                    on_dismiss = {},
                )
            }
        }
        compose_rule.waitForIdle()
    }

    @Test
    fun the_countdown_never_wraps_onto_a_second_line() {
        render()

        val node = compose_rule.onNodeWithText(countdown, useUnmergedTree = true)
        node.assertIsDisplayed()
        val height_dp = node.fetchSemanticsNode().size.height / compose_rule.density.density
        assertTrue("the countdown must stay on one line (was ${height_dp}dp tall)", height_dp < 26f)
    }

    @Test
    fun the_secondary_action_is_a_compact_icon_button() {
        var tapped = false
        render(on_secondary = { tapped = true })

        val icon = compose_rule.onNodeWithContentDescription("View message", useUnmergedTree = true)
        icon.assertIsDisplayed()
        val width_dp = icon.fetchSemanticsNode().size.width / compose_rule.density.density
        assertTrue("the view action must be a compact icon (was ${width_dp}dp wide)", width_dp < 44f)

        icon.performClick()
        assertTrue("tapping the icon must open the message", tapped)
    }
}
