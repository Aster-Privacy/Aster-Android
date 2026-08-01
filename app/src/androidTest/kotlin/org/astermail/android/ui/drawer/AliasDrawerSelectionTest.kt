//
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
class AliasDrawerSelectionTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val alias = drawer_alias_item(
        id = "alias_1",
        address = "shopping@aster.cx",
        routing_token = "route_abc",
    )

    @Before
    fun clear_sidebar_prefs() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("aster_sidebar", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun render(
        selected_id: String,
        on_select: (String) -> Unit = {},
        on_navigate_alias: (String, String, String?) -> Unit = { _, _, _ -> },
    ) {
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = selected_id,
                    on_select = on_select,
                    on_close = {},
                    on_navigate_alias = on_navigate_alias,
                    api_alias_items = listOf(alias),
                    preferences_loaded = true,
                )
            }
        }
    }

    @Test
    fun clicking_an_alias_selects_it_and_reports_its_routing_token() {
        var selected: String? = null
        var navigated: Triple<String, String, String?>? = null
        render(
            selected_id = "inbox",
            on_select = { selected = it },
            on_navigate_alias = { id, address, token -> navigated = Triple(id, address, token) },
        )

        compose_rule.onNodeWithText(alias.address).performScrollTo().performClick()
        compose_rule.waitForIdle()

        assertEquals("alias_1", selected)
        assertEquals(Triple("alias_1", "shopping@aster.cx", "route_abc"), navigated)
    }

    @Test
    fun alias_row_stays_highlighted_while_its_mail_list_is_open() {
        render(selected_id = "alias_1")

        compose_rule.onNodeWithText(alias.address).performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Inbox").performScrollTo().assertIsDisplayed()
        capture_screenshot("drawer_alias_selected", compose_rule.onRoot())
    }
}
