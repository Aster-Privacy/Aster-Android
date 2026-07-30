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

package org.astermail.android.ui.drawer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.capture_screenshot
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllMailPlacementTest {

    @get:Rule
    val compose_rule = createComposeRule()

    @Before
    fun clear_sidebar_prefs() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("aster_sidebar", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun render_drawer(on_select: (String) -> Unit = {}, more_collapsed: Boolean = false) {
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = "inbox",
                    on_select = on_select,
                    on_close = {},
                    trash_count = 4,
                    initial_more_collapsed = more_collapsed,
                    preferences_loaded = true,
                )
            }
        }
    }

    @Test
    fun all_mail_stays_visible_when_more_is_collapsed() {
        var selected: String? = null
        render_drawer(on_select = { selected = it }, more_collapsed = true)

        compose_rule.onNodeWithText("Scheduled").assertDoesNotExist()
        compose_rule.onNodeWithText("All Mail").performScrollTo().assertIsDisplayed()
        capture_screenshot("drawer_all_mail_after_trash", compose_rule.onRoot())

        compose_rule.onNodeWithText("All Mail").performClick()
        compose_rule.waitForIdle()
        assertEquals("all", selected)
    }

    @Test
    fun all_mail_sits_directly_below_trash() {
        render_drawer()

        val trash_bottom = compose_rule.onNodeWithText("Trash")
            .performScrollTo()
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val all_mail_top = compose_rule.onNodeWithText("All Mail")
            .performScrollTo()
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val more_top = compose_rule.onNodeWithText("MORE")
            .performScrollTo()
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        assert(all_mail_top >= trash_bottom - 1f) { "All Mail ($all_mail_top) must sit below Trash ($trash_bottom)" }
        assert(all_mail_top < more_top) { "All Mail ($all_mail_top) must sit above MORE ($more_top)" }
    }
}
