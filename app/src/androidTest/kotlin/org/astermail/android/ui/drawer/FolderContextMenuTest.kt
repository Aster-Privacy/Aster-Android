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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderContextMenuTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val unlocked = drawer_folder_item(
        id = "token_work",
        label = "Work",
        icon = Icons.Outlined.Folder,
        depth = 0,
        label_id = "label_work",
        color = "#3b82f6",
        password_set = false,
        muted = false,
        can_move_up = false,
        can_move_down = true,
        can_have_children = true,
    )

    private val locked = unlocked.copy(
        id = "token_private",
        label = "Private",
        label_id = "label_private",
        password_set = true,
        muted = true,
        can_move_up = true,
        can_move_down = false,
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
        items: List<drawer_folder_item>,
        actions: folder_menu_actions = folder_menu_actions(),
        on_create_folder: (String, String?) -> Unit = { _, _ -> },
    ) {
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = "inbox",
                    on_select = {},
                    on_close = {},
                    api_folder_items = items,
                    folder_actions = actions,
                    on_create_folder = on_create_folder,
                    folder_parent_options = listOf(
                        folder_parent_option(token = "token_work", label = "Work", depth = 0, path_label = ""),
                        folder_parent_option(token = "token_private", label = "Private", depth = 0, path_label = ""),
                    ),
                )
            }
        }
    }

    private fun text_field() = compose_rule.onAllNodes(hasSetTextAction()).onFirst()

    private fun open_menu(label: String) {
        compose_rule.onNodeWithText(label).performScrollTo().performTouchInput { longClick() }
        compose_rule.waitForIdle()
    }

    @Test
    fun long_press_shows_every_web_parity_action() {
        render(listOf(unlocked))
        open_menu("Work")

        compose_rule.onNodeWithTag("folder_actions_menu").assertIsDisplayed()
        val problems = listOf(
            "Create subfolder",
            "Lock folder",
            "Rename",
            "Change color",
            "Mute folder notifications",
            "Move to",
            "Move up",
            "Move down",
            "Delete",
        ).mapNotNull { label ->
            try {
                compose_rule.onNodeWithText(label).assertIsDisplayed()
                null
            } catch (t: Throwable) {
                "$label -> ${t.message?.lineSequence()?.take(3)?.joinToString(" | ")}"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun locked_and_muted_folder_shows_inverse_labels() {
        render(listOf(locked))
        open_menu("Private")

        compose_rule.onNodeWithText("Remove lock").assertIsDisplayed()
        compose_rule.onNodeWithText("Unmute folder notifications").assertIsDisplayed()
    }

    @Test
    fun rename_dialog_submits_trimmed_name_with_label_id() {
        var renamed: Pair<String, String>? = null
        render(
            listOf(unlocked),
            folder_menu_actions(on_rename = { item, name -> renamed = item.label_id to name }),
        )
        open_menu("Work")

        compose_rule.onNodeWithText("Rename").performClick()
        compose_rule.waitForIdle()
        text_field().performTextClearance()
        text_field().performTextInput("Projects")
        compose_rule.onNodeWithText("Save").performClick()
        compose_rule.waitForIdle()

        assertEquals("label_work" to "Projects", renamed)
    }

    @Test
    fun move_order_reports_direction() {
        var moved: Pair<String, Int>? = null
        render(
            listOf(unlocked),
            folder_menu_actions(on_move_order = { item, direction -> moved = item.label_id to direction }),
        )
        open_menu("Work")

        compose_rule.onNodeWithText("Move down").performClick()
        compose_rule.waitForIdle()

        assertEquals("label_work" to 1, moved)
    }

    @Test
    fun move_to_root_sends_null_parent() {
        var move_called = false
        var parent: String? = "unset"
        render(
            listOf(unlocked),
            folder_menu_actions(
                on_move_to = { _, token ->
                    move_called = true
                    parent = token
                },
            ),
        )
        open_menu("Work")

        compose_rule.onNodeWithText("Move to").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("Save").performClick()
        compose_rule.waitForIdle()

        assertEquals(true, move_called)
        assertEquals(null, parent)
    }

    @Test
    fun lock_dialog_passes_password_through() {
        var locked_with: Pair<String, String>? = null
        render(
            listOf(unlocked),
            folder_menu_actions(on_set_lock = { item, password -> locked_with = item.label_id to password }),
        )
        open_menu("Work")

        compose_rule.onNodeWithText("Lock folder").performClick()
        compose_rule.waitForIdle()
        text_field().performTextInput("hunter2hunter2")
        compose_rule.onNodeWithText("Save").performClick()
        compose_rule.waitForIdle()

        assertEquals("label_work" to "hunter2hunter2", locked_with)
    }

    @Test
    fun remove_lock_dialog_passes_password_through() {
        var removed_with: Pair<String, String>? = null
        render(
            listOf(locked),
            folder_menu_actions(on_remove_lock = { item, password -> removed_with = item.label_id to password }),
        )
        open_menu("Private")

        compose_rule.onNodeWithText("Remove lock").performClick()
        compose_rule.waitForIdle()
        text_field().performTextInput("opensesame")
        compose_rule.onAllNodesWithText("Remove lock").filterToOne(hasClickAction()).performClick()
        compose_rule.waitForIdle()

        assertEquals("label_private" to "opensesame", removed_with)
    }

    @Test
    fun delete_requires_confirmation_dialog() {
        var deleted: String? = null
        render(
            listOf(unlocked),
            folder_menu_actions(on_delete = { item, _, _, _ -> deleted = item.label_id }),
        )
        open_menu("Work")

        compose_rule.onNodeWithText("Delete").performClick()
        compose_rule.waitForIdle()
        assertEquals(null, deleted)

        compose_rule.onNodeWithText("Delete").performClick()
        compose_rule.waitForIdle()
        assertEquals("label_work", deleted)
    }

    @Test
    fun create_subfolder_preselects_the_long_pressed_folder_as_parent() {
        var created: Pair<String, String?>? = null
        render(
            listOf(unlocked),
            on_create_folder = { name, parent -> created = name to parent },
        )
        open_menu("Work")

        compose_rule.onNodeWithText("Create subfolder").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("parent_folder_selector").assertIsDisplayed()
        text_field().performTextInput("Invoices")
        compose_rule.onNodeWithText("Save").performClick()
        compose_rule.waitForIdle()

        assertEquals("Invoices" to "token_work", created)
    }
}
