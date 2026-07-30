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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderNestingDrawerTest {

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

    private fun sample_folders() = listOf(
        drawer_folder_item(id = "t1", label = "Test 1", icon = Icons.Outlined.Folder, count = 0, depth = 0, has_children = true),
        drawer_folder_item(id = "apple", label = "Apple", icon = Icons.Outlined.Folder, count = 0, depth = 1),
        drawer_folder_item(id = "boy", label = "Boy", icon = Icons.Outlined.Folder, count = 0, depth = 1, has_children = true),
        drawer_folder_item(id = "cat", label = "Cat", icon = Icons.Outlined.Folder, count = 2, depth = 2),
    )

    private fun sample_parent_options() = listOf(
        folder_parent_option(token = "t1", label = "Test 1", depth = 0, path_label = "Test 1"),
        folder_parent_option(token = "boy", label = "Boy", depth = 1, path_label = "Test 1 · Boy"),
    )

    private fun set_drawer_content(
        on_create_folder: (String, String?) -> Unit = { _, _ -> },
    ) {
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = "inbox",
                    on_select = {},
                    on_close = {},
                    api_folder_items = sample_folders(),
                    folder_parent_options = sample_parent_options(),
                    on_create_folder = on_create_folder,
                )
            }
        }
    }

    @Test
    fun children_are_hidden_until_parent_is_expanded() {
        set_drawer_content()
        compose_rule.onNodeWithText("Test 1").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Apple").assertDoesNotExist()
        compose_rule.onNodeWithText("Boy").assertDoesNotExist()
        compose_rule.onNodeWithText("Cat").assertDoesNotExist()
    }

    @Test
    fun renders_nested_folder_rows_when_expanded() {
        set_drawer_content()
        compose_rule.onNodeWithTag("folder_expand_Test 1").performScrollTo().performClick()
        compose_rule.onNodeWithText("Apple").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Boy").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("Cat").assertDoesNotExist()

        compose_rule.onNodeWithTag("folder_expand_Boy").performScrollTo().performClick()
        compose_rule.onNodeWithText("Cat").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun collapsing_a_parent_hides_its_descendants() {
        set_drawer_content()
        compose_rule.onNodeWithTag("folder_expand_Test 1").performScrollTo().performClick()
        compose_rule.onNodeWithTag("folder_expand_Boy").performScrollTo().performClick()
        compose_rule.onNodeWithText("Cat").performScrollTo().assertIsDisplayed()

        compose_rule.onNodeWithTag("folder_expand_Test 1").performScrollTo().performClick()
        compose_rule.onNodeWithText("Apple").assertDoesNotExist()
        compose_rule.onNodeWithText("Boy").assertDoesNotExist()
        compose_rule.onNodeWithText("Cat").assertDoesNotExist()
    }

    @Test
    fun expanding_a_folder_does_not_navigate() {
        var selected: String? = null
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = "inbox",
                    on_select = { selected = it },
                    on_close = {},
                    api_folder_items = sample_folders(),
                    folder_parent_options = sample_parent_options(),
                )
            }
        }

        compose_rule.onNodeWithTag("folder_expand_Test 1").performScrollTo().performClick()
        compose_rule.runOnIdle {
            assert(selected == null)
        }
    }

    @Test
    fun create_folder_dialog_offers_parent_selection() {
        var created_name: String? = null
        var created_parent: String? = null
        set_drawer_content { name, parent ->
            created_name = name
            created_parent = parent
        }

        compose_rule.onNodeWithTag("create_folder").performScrollTo().performClick()
        compose_rule.onNodeWithText("Parent folder").assertIsDisplayed()
        compose_rule.onNodeWithTag("parent_folder_selector").assertIsDisplayed()

        compose_rule.onNodeWithTag("parent_folder_selector").performClick()
        compose_rule.onAllNodesWithText("None")
            .filterToOne(hasAnyAncestor(isPopup()))
            .assertIsDisplayed()
        compose_rule.onAllNodesWithText("Boy", substring = true)
            .filterToOne(hasAnyAncestor(isPopup()))
            .performClick()

        compose_rule.onNodeWithText("Test 1 · Boy").assertIsDisplayed()

        compose_rule.onNode(hasSetTextAction()).performTextInput("Receipts")
        compose_rule.onNodeWithText("Save").performClick()

        compose_rule.runOnIdle {
            assert(created_name == "Receipts")
            assert(created_parent == "boy")
        }
    }

    @Test
    fun create_folder_defaults_to_no_parent() {
        var created_parent: String? = "sentinel"
        set_drawer_content { _, parent ->
            created_parent = parent
        }

        compose_rule.onNodeWithTag("create_folder").performScrollTo().performClick()
        compose_rule.onNode(hasSetTextAction()).performTextInput("Top Level")
        compose_rule.onNodeWithText("Save").performClick()

        compose_rule.runOnIdle {
            assert(created_parent == null)
        }
    }

    @Test
    fun dropdown_none_option_clears_selection() {
        set_drawer_content()

        compose_rule.onNodeWithTag("create_folder").performScrollTo().performClick()
        compose_rule.onNodeWithTag("parent_folder_selector").performClick()
        compose_rule.onAllNodesWithText("Test 1")
            .filterToOne(hasAnyAncestor(isPopup()))
            .performClick()
        compose_rule.onNodeWithTag("parent_folder_selector").performClick()
        compose_rule.onAllNodesWithText("None")
            .filterToOne(hasAnyAncestor(isPopup()))
            .performClick()
        compose_rule.onNodeWithTag("parent_folder_selector").assert(hasText("None"))
    }
}
