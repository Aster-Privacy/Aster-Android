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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.astermail.android.mail.CategoryEntry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDrawerTest {

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

    private fun sample_categories() = listOf(
        CategoryEntry(id = "primary", label = "Primary", icon = "inbox"),
        CategoryEntry(id = "promotions", label = "Deals", icon = "tag"),
        CategoryEntry(id = "social", label = "Social", icon = "users"),
        CategoryEntry(id = "updates", label = "Notifications", icon = "bell"),
    )

    private fun set_drawer_content(
        categories_enabled: Boolean = true,
        selected_category: String = "primary",
        inbox_unread: Int = 0,
        category_unread: Map<String, Int> = emptyMap(),
        on_select: (String) -> Unit = {},
        on_select_category: (String) -> Unit = {},
    ) {
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = "inbox",
                    on_select = on_select,
                    on_close = {},
                    inbox_unread = inbox_unread,
                    categories_enabled = categories_enabled,
                    category_entries = sample_categories(),
                    category_unread = category_unread,
                    selected_category = selected_category,
                    on_select_category = on_select_category,
                )
            }
        }
    }

    @Test
    fun inbox_row_stays_visible_when_categories_are_enabled() {
        set_drawer_content()
        compose_rule.onNodeWithText("Inbox").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Sent").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun categories_render_as_children_of_inbox() {
        set_drawer_content()
        compose_rule.onNodeWithText("Deals").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Social").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Primary").assertDoesNotExist()
    }

    @Test
    fun collapsing_inbox_hides_the_categories() {
        set_drawer_content()
        compose_rule.onNodeWithTag("folder_expand_Inbox").performScrollTo().performClick()
        compose_rule.onNodeWithText("Deals").assertDoesNotExist()
        compose_rule.onNodeWithText("Social").assertDoesNotExist()
        compose_rule.onNodeWithText("Notifications").assertDoesNotExist()
        compose_rule.onNodeWithText("Inbox").performScrollTo().assertIsDisplayed()

        compose_rule.onNodeWithTag("folder_expand_Inbox").performScrollTo().performClick()
        compose_rule.onNodeWithText("Deals").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tapping_inbox_selects_the_primary_category() {
        var selected_category: String? = null
        var selected_folder: String? = null
        set_drawer_content(
            on_select = { selected_folder = it },
            on_select_category = { selected_category = it },
        )

        compose_rule.onNodeWithText("Inbox").performScrollTo().performClick()
        compose_rule.runOnIdle {
            assert(selected_category == "primary")
            assert(selected_folder == null)
        }
    }

    @Test
    fun tapping_a_category_selects_that_category() {
        var selected_category: String? = null
        set_drawer_content(on_select_category = { selected_category = it })

        compose_rule.onNodeWithText("Social").performScrollTo().performClick()
        compose_rule.runOnIdle {
            assert(selected_category == "social")
        }
    }

    @Test
    fun expanding_inbox_does_not_navigate() {
        var selected_category: String? = null
        var selected_folder: String? = null
        set_drawer_content(
            on_select = { selected_folder = it },
            on_select_category = { selected_category = it },
        )

        compose_rule.onNodeWithTag("folder_expand_Inbox").performScrollTo().performClick()
        compose_rule.runOnIdle {
            assert(selected_category == null)
            assert(selected_folder == null)
        }
    }

    @Test
    fun inbox_selects_the_folder_when_categories_are_disabled() {
        var selected_category: String? = null
        var selected_folder: String? = null
        set_drawer_content(
            categories_enabled = false,
            on_select = { selected_folder = it },
            on_select_category = { selected_category = it },
        )

        compose_rule.onNodeWithText("Deals").assertDoesNotExist()
        compose_rule.onNodeWithText("Inbox").performScrollTo().performClick()
        compose_rule.runOnIdle {
            assert(selected_folder == "inbox")
            assert(selected_category == null)
        }
    }

    @Test
    fun inbox_shows_primary_unread_expanded_and_total_unread_collapsed() {
        set_drawer_content(
            inbox_unread = 8,
            category_unread = mapOf("primary" to 3, "promotions" to 5),
        )
        compose_rule.onNodeWithText("3").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("5").performScrollTo().assertIsDisplayed()

        compose_rule.onNodeWithTag("folder_expand_Inbox").performScrollTo().performClick()
        compose_rule.onNodeWithText("8").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("5").assertDoesNotExist()
    }
}
